import './style.css'

interface ExtendedWindow extends Window {
  createKmpTerminal?: (config: {
    containerId: string;
    rows: number;
    cols: number;
    onInput: (input: string) => void;
  }) => TerminalInstance;
  writeToTerminal?: (text: string) => void;
  resizeTerminal?: (rows: number, cols: number) => void;
  focusTerminal?: () => void;
  setTerminalTheme?: (themeName: string) => void;
  setTerminalCursorStyle?: (cursorStyle: string) => void;
  setTerminalFontSize?: (size: number) => void;
  onTerminalInput?: (input: string) => void;
}

interface TerminalInstance {
  write: (text: string) => void;
  resize: (rows: number, cols: number) => void;
  focus: () => void;
  dispose: () => void;
  setTheme?: (name: string) => void;
  setCursorStyle?: (style: string) => void;
  setFontSize?: (size: number) => void;
}

const extWindow = window as unknown as ExtendedWindow;

let termInstance: TerminalInstance | null = null;
let currentFontSize = 14;
let activeInterval: ReturnType<typeof setInterval> | null = null;
let isInAltScreen = false;
let commandBuffer = "";
const commandHistory: string[] = [];
let historyIndex = -1;

function stopActiveAnimation() {
  if (activeInterval !== null) {
    clearInterval(activeInterval);
    activeInterval = null;
  }
  if (isInAltScreen) {
    if (termInstance) {
      termInstance.write("\u001B[?1049l\u001B[?25h");
    }
    isInAltScreen = false;
  }
}

function write(text: string) {
  if (termInstance) {
    termInstance.write(text);
  } else if (extWindow.writeToTerminal) {
    extWindow.writeToTerminal(text);
  }
}

function runColorsDemo() {
  stopActiveAnimation();
  let out = "\r\n\u001B[1;37m=== 1. Standard 16 ANSI Colors (Normal & Bright) ===\u001B[0m\r\nNormal: ";
  for (let c = 30; c <= 37; c++) out += `\u001B[${c}m■ Color ${c} \u001B[0m`;
  out += "\r\nBright: ";
  for (let c = 90; c <= 97; c++) out += `\u001B[${c}m■ Color ${c} \u001B[0m`;
  out += "\r\n\r\n\u001B[1;37m=== 2. 256 Indexed Color Palette ===\u001B[0m\r\n";
  for (let r = 0; r < 6; r++) {
    for (let g = 0; g < 6; g++) {
      for (let b = 0; b < 6; b++) {
        const code = 16 + (r * 36) + (g * 6) + b;
        out += `\u001B[48;5;${code}m  \u001B[0m`;
      }
      out += " ";
    }
    out += "\r\n";
  }
  out += "Grayscale: ";
  for (let code = 232; code <= 255; code++) {
    out += `\u001B[48;5;${code}m  \u001B[0m`;
  }
  out += "\r\n\r\n\u001B[1;37m=== 3. 24-bit TrueColor RGB Smooth Gradient ===\u001B[0m\r\n";
  for (let i = 0; i < 70; i++) {
    const ratio = i / 70.0;
    const r = Math.floor(Math.sin(ratio * Math.PI) * 255);
    const g = Math.floor(Math.sin((ratio + 0.33) * Math.PI) * 255);
    const b = Math.floor(Math.sin((ratio + 0.66) * Math.PI) * 255);
    out += `\u001B[38;2;${r};${g};${b}m█\u001B[0m`;
  }
  out += "\r\n\r\n";
  write(out);
}

function runStylesDemo() {
  stopActiveAnimation();
  const out = "\r\n\u001B[1;37m=== Text Formatting & Attributes ===\u001B[0m\r\n" +
    "  \u001B[0mNormal text\u001B[0m\r\n" +
    "  \u001B[1mBold text\u001B[0m\r\n" +
    "  \u001B[2mDim / Faint text\u001B[0m\r\n" +
    "  \u001B[3mItalic text\u001B[0m\r\n" +
    "  \u001B[4mUnderlined text\u001B[0m\r\n" +
    "  \u001B[21mDouble underlined text\u001B[0m\r\n" +
    "  \u001B[5mBlinking text\u001B[0m\r\n" +
    "  \u001B[7mInverse / Reverse video\u001B[0m\r\n" +
    "  \u001B[9mCrossed out / Strikethrough\u001B[0m\r\n" +
    "  \u001B[1;3;4;33;44mCombined: Bold + Italic + Underline + Yellow on Blue\u001B[0m\r\n\r\n";
  write(out);
}

function runUnicodeDemo() {
  stopActiveAnimation();
  const out = "\r\n\u001B[1;37m=== Unicode, Box Drawing & Powerline Glyphs ===\u001B[0m\r\n" +
    "\u001B[36m┌──────────────────────────────────────────────┐\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m  \u001B[1;33m⚡ KMP Multiplatform Monospace Grid\u001B[0m         \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m├──────────────────────┬───────────────────────┤\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m Double Lines: ╔═╦═╗  \u001B[36m│\u001B[0m Rounded: ╭───┬───╮     \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m               ╠═╬═╣  \u001B[36m│\u001B[0m          │   │   │     \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m               ╚═╩═╝  \u001B[36m│\u001B[0m          ╰───┴───╯     \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m├──────────────────────┴───────────────────────┤\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m Blocks & Shades: ░▒▓█ ▌▐ ▄▀ ■ □ ▲ ▼ ◆ ◈       \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m Braille Graph:   ⡀⣀⣄⣤⣦⣶⣷⣿                   \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m Tree Hierarchy:                               \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m   ├── \u001B[34mterminal-core\u001B[0m (zero UI dependencies)   \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m   └── \u001B[32mterminal-ui\u001B[0m   (Compose Multiplatform)   \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m│\u001B[0m Powerline: \u001B[44;30m master \u001B[42;34m\u001B[30m ✓ clean \u001B[49;32m\u001B[0m             \u001B[36m│\u001B[0m\r\n" +
    "\u001B[36m└──────────────────────────────────────────────┘\u001B[0m\r\n\r\n";
  write(out);
}

function runProgressDemo() {
  stopActiveAnimation();
  write("\r\n\u001B[1;37m=== Dynamic Progress Bars & Spinners ===\u001B[0m\r\n");
  const spinners = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"];
  let pct = 0;
  activeInterval = setInterval(() => {
    pct += 2;
    const sp = spinners[(pct / 2) % spinners.length];
    const filled = Math.floor(pct / 4);
    const empty = 25 - filled;
    const bar = "█".repeat(filled) + "░".repeat(empty);
    const color = pct < 50 ? "\u001B[33m" : (pct < 80 ? "\u001B[36m" : "\u001B[32m");
    write(`\r\u001B[K\u001B[35m${sp}\u001B[0m Downloading assets: [${color}${bar}\u001B[0m] \u001B[1m${pct}%\u001B[0m`);
    if (pct >= 100) {
      if (activeInterval) clearInterval(activeInterval);
      activeInterval = null;
      write("\r\n\u001B[32m✔ Download and asset verification complete!\u001B[0m\r\n\r\nkmp-vite-shell$ ");
    }
  }, 30);
}

function runMatrixDemo() {
  stopActiveAnimation();
  write("\u001B[?25l\u001B[2J\u001B[H");
  const cols = 80;
  const rows = 24;
  const drops = new Array(cols).fill(0).map(() => -Math.floor(Math.random() * 20));
  const chars = "abcdefghijklmnopqrstuvwxyz0123456789@#$%&*+-=~<>{}[]";
  let frameCount = 0;

  activeInterval = setInterval(() => {
    let frame = "";
    for (let c = 0; c < cols; c++) {
      const r = drops[c];
      if (r >= 1 && r <= rows) {
        const ch = chars[Math.floor(Math.random() * chars.length)];
        frame += `\u001B[${r};${c + 1}H\u001B[1;37m${ch}`;
        if (r > 1) {
          const ch2 = chars[Math.floor(Math.random() * chars.length)];
          frame += `\u001B[${r - 1};${c + 1}H\u001B[1;32m${ch2}`;
        }
        if (r > 3) {
          const ch3 = chars[Math.floor(Math.random() * chars.length)];
          frame += `\u001B[${r - 3};${c + 1}H\u001B[2;32m${ch3}`;
        }
        if (r > 8) {
          frame += `\u001B[${r - 8};${c + 1}H `;
        }
      }
      drops[c]++;
      if (drops[c] > rows + 10) {
        drops[c] = -Math.floor(Math.random() * 10);
      }
    }
    write(frame);
    frameCount++;
    if (frameCount > 200) {
      stopActiveAnimation();
      write("\u001B[?25h\u001B[2J\u001B[H\u001B[32m[Matrix animation completed]\u001B[0m\r\n\r\nkmp-vite-shell$ ");
    }
  }, 40);
}

function runHtopDemo() {
  stopActiveAnimation();
  isInAltScreen = true;
  write("\u001B[?1049h\u001B[?25l\u001B[2J\u001B[H");
  let tick = 0;

  activeInterval = setInterval(() => {
    if (!isInAltScreen) return;
    const cpu1 = Math.min(98, Math.max(5, Math.floor(40 + 30 * Math.sin(tick * 0.2))));
    const cpu2 = Math.min(98, Math.max(5, Math.floor(55 + 25 * Math.cos(tick * 0.15))));
    const cpu3 = Math.min(98, Math.max(5, Math.floor(30 + 40 * Math.sin(tick * 0.3))));
    const cpu4 = Math.min(98, Math.max(5, Math.floor(70 + 20 * Math.cos(tick * 0.25))));
    const mem = 3840 + (tick % 50) * 12;

    const makeBar = (pct: number) => {
      const filled = Math.floor(pct / 5);
      return "\u001B[32m" + "|".repeat(filled) + "\u001B[0m" + " ".repeat(20 - filled);
    };

    const sb = "\u001B[H" +
      `\u001B[1;36m 1 \u001B[0m[${makeBar(cpu1)}] \u001B[1m${cpu1}%\u001B[0m       \u001B[1;36mTasks:\u001B[0m \u001B[1;32m74 total, 2 running\u001B[0m\r\n` +
      `\u001B[1;36m 2 \u001B[0m[${makeBar(cpu2)}] \u001B[1m${cpu2}%\u001B[0m       \u001B[1;36mLoad average:\u001B[0m 0.42 0.38 0.31\r\n` +
      `\u001B[1;36m 3 \u001B[0m[${makeBar(cpu3)}] \u001B[1m${cpu3}%\u001B[0m       \u001B[1;36mUptime:\u001B[0m 14 days, 03:22:18\r\n` +
      `\u001B[1;36m 4 \u001B[0m[${makeBar(cpu4)}] \u001B[1m${cpu4}%\u001B[0m       \u001B[1;36mBuffer Mode:\u001B[0m \u001B[1;33mAlternate Screen (DECSET 1049)\u001B[0m\r\n` +
      `\u001B[1;36mMem\u001B[0m[\u001B[34m` + "|".repeat(12) + `\u001B[0m        ] ${mem}/16384 MB\r\n\r\n` +
      "\u001B[7;1m  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   TIME+  Command                     \u001B[0m\r\n" +
      ` 1024 root       20   0  1.2G  142M   45M S ${(cpu1 * 0.4).toFixed(1).padStart(4)}  1.2  02:14.2 compose-skia-render       \r\n` +
      ` 1432 johan      20   0  850M   98M   32M S ${(cpu2 * 0.3).toFixed(1).padStart(4)}  0.8  00:45.1 kotlin-wasm-daemon         \r\n` +
      ` 2048 johan      20   0  420M   64M   20M R ${(cpu3 * 0.5).toFixed(1).padStart(4)}  0.5  01:12.8 kmp-terminal-core          \r\n` +
      " 3012 system     20   0  310M   28M   14M S  0.0  0.2  00:03.0 pty-subsystem              \r\n" +
      " 4096 johan      20   0  180M   16M    8M S  0.0  0.1  00:00.4 htop-simulator             \r\n\r\n" +
      "\u001B[1;33m[Press 'q' or click Clear to return to normal buffer without losing history]\u001B[0m";

    write(sb);
    tick++;
  }, 500);
}

function handleShellCommand(rawCmd: string) {
  const cmd = rawCmd.trim();
  if (cmd.length > 0) {
    commandHistory.push(cmd);
    historyIndex = commandHistory.length;
  }
  write("\r\n");
  const parts = cmd.split(" ");
  const action = (parts[0] || "").toLowerCase();

  switch (action) {
    case "help":
      write(
        "\u001B[1;36mKMP Terminal Emulator Showcase - Commands Catalog\u001B[0m\r\n" +
        "  \u001B[1;33mcolors\u001B[0m   - Showcase 16 ANSI colors, 256 colors & 24-bit TrueColor\r\n" +
        "  \u001B[1;33mstyles\u001B[0m   - Test bold, italic, underline, strikethrough, inverse\r\n" +
        "  \u001B[1;33municode\u001B[0m  - Display box drawing, trees, braille & powerline symbols\r\n" +
        "  \u001B[1;33mprogress\u001B[0m - Run animated progress bars and live spinners\r\n" +
        "  \u001B[1;33mmatrix\u001B[0m   - Digital rain animation with real-time cursor positioning\r\n" +
        "  \u001B[1;33mhtop\u001B[0m     - Live system monitor in Alternate Screen Buffer (press 'q')\r\n" +
        "  \u001B[1;33mclear\u001B[0m    - Clear screen buffer\r\n" +
        "  \u001B[1;33mecho\u001B[0m     - Echo text back to terminal\r\n" +
        "  \u001B[1;33mping\u001B[0m     - Respond with pong\r\n"
      );
      break;
    case "colors":
      runColorsDemo();
      break;
    case "styles":
      runStylesDemo();
      break;
    case "unicode":
    case "box":
      runUnicodeDemo();
      break;
    case "progress":
      runProgressDemo();
      return;
    case "matrix":
      runMatrixDemo();
      return;
    case "htop":
    case "top":
      runHtopDemo();
      return;
    case "clear":
      write("\u001B[2J\u001B[H");
      break;
    case "ping":
      write("pong!\r\n");
      break;
    case "echo":
      write(parts.slice(1).join(" ") + "\r\n");
      break;
    case "":
      break;
    default:
      write(`Unknown command: '${cmd}'. Type '\u001B[33mhelp\u001B[0m' for available commands.\r\n`);
      break;
  }
  write("kmp-vite-shell$ ");
}

function processInput(input: string) {
  if (isInAltScreen || activeInterval !== null) {
    if (input.includes("q") || input.includes("\x03")) {
      stopActiveAnimation();
      write("\r\nkmp-vite-shell$ ");
    }
    return;
  }

  if (input === "\u001B[A") { // Up arrow
    if (commandHistory.length > 0 && historyIndex > 0) {
      historyIndex--;
      const prev = commandHistory[historyIndex];
      const clearBack = "\b \b".repeat(commandBuffer.length);
      commandBuffer = prev;
      write(`${clearBack}${prev}`);
    }
    return;
  } else if (input === "\u001B[B") { // Down arrow
    if (historyIndex < commandHistory.length - 1) {
      historyIndex++;
      const next = commandHistory[historyIndex];
      const clearBack = "\b \b".repeat(commandBuffer.length);
      commandBuffer = next;
      write(`${clearBack}${next}`);
    } else if (historyIndex === commandHistory.length - 1) {
      historyIndex = commandHistory.length;
      const clearBack = "\b \b".repeat(commandBuffer.length);
      commandBuffer = "";
      write(clearBack);
    }
    return;
  }

  for (let i = 0; i < input.length; i++) {
    const char = input[i];
    if (char === "\r" || char === "\n") {
      const cmd = commandBuffer;
      commandBuffer = "";
      handleShellCommand(cmd);
    } else if (char === "\x7f" || char === "\b") {
      if (commandBuffer.length > 0) {
        commandBuffer = commandBuffer.slice(0, -1);
        write("\b \b");
      }
    } else if (char === "\x03") {
      commandBuffer = "";
      write("^C\r\nkmp-vite-shell$ ");
    } else if (char.charCodeAt(0) >= 32 && char.charCodeAt(0) <= 126) {
      commandBuffer += char;
      write(char);
    }
  }
}

// Setup input callback globally BEFORE window load completes, so Wasm registers it
extWindow.onTerminalInput = (input: string) => {
  processInput(input);
};

window.addEventListener('load', () => {
  setTimeout(() => {
    if (typeof extWindow.createKmpTerminal === 'function') {
      termInstance = extWindow.createKmpTerminal({
        containerId: "compose-receiver",
        rows: 24,
        cols: 80,
        onInput: (input: string) => processInput(input)
      });
    }

    // Write welcome banner
    write("\u001B[1;36m╭─────────────────────────────────────────────────────────────────────────────╮\u001B[0m\r\n");
    write("\u001B[1;36m│\u001B[0m  \u001B[1;32m⚡ KMP Terminal Emulator (Vite + TypeScript + WebAssembly Demo)\u001B[0m            \u001B[1;36m│\u001B[0m\r\n");
    write("\u001B[1;36m│\u001B[0m  Features: 24-bit TrueColor, DECSTBM margins, Alt-Buffer, Unicode, Matrix     \u001B[1;36m│\u001B[0m\r\n");
    write("\u001B[1;36m│\u001B[0m  Try toolbar buttons above or type '\u001B[33mhelp\u001B[0m', '\u001B[33mcolors\u001B[0m', '\u001B[33mhtop\u001B[0m', '\u001B[33mmatrix\u001B[0m'.      \u001B[1;36m│\u001B[0m\r\n");
    write("\u001B[1;36m╰─────────────────────────────────────────────────────────────────────────────╯\u001B[0m\r\n\r\n");
    write("kmp-vite-shell$ ");
  }, 250);

  // Setup UI listeners
  document.querySelector('#select-theme')?.addEventListener('change', (e) => {
    const themeName = (e.target as HTMLSelectElement).value;
    if (termInstance?.setTheme) {
      termInstance.setTheme(themeName);
    } else if (extWindow.setTerminalTheme) {
      extWindow.setTerminalTheme(themeName);
    }
  });

  document.querySelector('#select-cursor')?.addEventListener('change', (e) => {
    const cursorStyle = (e.target as HTMLSelectElement).value;
    if (termInstance?.setCursorStyle) {
      termInstance.setCursorStyle(cursorStyle);
    } else if (extWindow.setTerminalCursorStyle) {
      extWindow.setTerminalCursorStyle(cursorStyle);
    }
  });

  document.querySelector('#btn-font-inc')?.addEventListener('click', () => {
    currentFontSize = Math.min(26, currentFontSize + 1);
    const label = document.querySelector('#label-font');
    if (label) label.textContent = `${currentFontSize}px`;
    if (termInstance?.setFontSize) {
      termInstance.setFontSize(currentFontSize);
    } else if (extWindow.setTerminalFontSize) {
      extWindow.setTerminalFontSize(currentFontSize);
    }
  });

  document.querySelector('#btn-font-dec')?.addEventListener('click', () => {
    currentFontSize = Math.max(10, currentFontSize - 1);
    const label = document.querySelector('#label-font');
    if (label) label.textContent = `${currentFontSize}px`;
    if (termInstance?.setFontSize) {
      termInstance.setFontSize(currentFontSize);
    } else if (extWindow.setTerminalFontSize) {
      extWindow.setTerminalFontSize(currentFontSize);
    }
  });

  document.querySelector('#btn-colors')?.addEventListener('click', () => {
    runColorsDemo();
    write("kmp-vite-shell$ ");
  });

  document.querySelector('#btn-styles')?.addEventListener('click', () => {
    runStylesDemo();
    write("kmp-vite-shell$ ");
  });

  document.querySelector('#btn-unicode')?.addEventListener('click', () => {
    runUnicodeDemo();
    write("kmp-vite-shell$ ");
  });

  document.querySelector('#btn-progress')?.addEventListener('click', runProgressDemo);
  document.querySelector('#btn-matrix')?.addEventListener('click', runMatrixDemo);
  document.querySelector('#btn-htop')?.addEventListener('click', runHtopDemo);

  document.querySelector('#btn-clear')?.addEventListener('click', () => {
    stopActiveAnimation();
    write("\u001B[2J\u001B[Hkmp-vite-shell$ ");
  });

  document.querySelector('#btn-focus')?.addEventListener('click', () => {
    if (termInstance?.focus) {
      termInstance.focus();
    } else if (extWindow.focusTerminal) {
      extWindow.focusTerminal();
    }
  });
});
