# KMP Terminal Emulator

[![CI](https://github.com/JohNan/kmp-terminal-emulator/actions/workflows/ci.yml/badge.svg)](https://github.com/JohNan/kmp-terminal-emulator/actions/workflows/ci.yml)
[![GitHub Pages](https://img.shields.io/badge/GitHub_Pages-Live_Demo-brightgreen.svg?logo=github)](https://johnan.github.io/kmp-terminal-emulator/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.4.20-purple.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.11.1-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Target-Android-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/Target-iOS-000000.svg?logo=apple&logoColor=white)](https://developer.apple.com/ios/)
[![macOS](https://img.shields.io/badge/Target-macOS-000000.svg?logo=apple&logoColor=white)](https://apple.com/macos/)
[![Linux](https://img.shields.io/badge/Target-Linux-FCC624.svg?logo=linux&logoColor=black)](https://kernel.org)
[![JVM](https://img.shields.io/badge/Target-JVM_Desktop-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)
[![WebAssembly](https://img.shields.io/badge/Target-WasmJs-654FF0.svg?logo=webassembly&logoColor=white)](https://webassembly.org/)
[![Google Play](https://img.shields.io/badge/Google_Play-Production_App-34A853?logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.johnan.sshclient)

A modern Kotlin Multiplatform (KMP) terminal emulator engine, TUI differential rendering system, and Compose Multiplatform rendering library. Designed for mobile, desktop, CLI, and web applications.

👉 **[🚀 Try Live Interactive WebAssembly Demo on GitHub Pages](https://johnan.github.io/kmp-terminal-emulator/)**

---

## Interactive Demos & Showcase

The repository includes multiple demo implementations targeting different environments and runtimes:

<details>
<summary><strong>1. Live WebAssembly Demo (GitHub Pages)</strong> &mdash; In-browser REPL via Kotlin/Wasm</summary>

- **Live URL**: [https://johnan.github.io/kmp-terminal-emulator/](https://johnan.github.io/kmp-terminal-emulator/)
- **Module**: [`demo-web`](demo-web)
- An interactive in-browser terminal REPL compiled via Kotlin/Wasm (`wasmJs`) and rendered with Compose Multiplatform on a GPU-accelerated Skia canvas.
- Automatically built and deployed on pushes to `main` via [GitHub Actions](.github/workflows/deploy-pages.yml).
- Supports interactive typing, ANSI color test patterns (16 / 256 / 24-bit TrueColor), Unicode box drawing, alternate screen buffer (`htop` preview), and configurable cursor styling.

</details>

<details>
<summary><strong>2. Desktop Terminal Emulator (<code>demo-jvm</code>)</strong> &mdash; Native JVM application (macOS, Linux, Windows)</summary>

- **Module**: [`demo-jvm`](demo-jvm)
- A native desktop application running on macOS, Linux, and Windows using Compose Multiplatform for JVM.
- Features a standalone interactive REPL terminal window with real-time ANSI sequence parsing and keyboard navigation.
- Run locally:
  ```bash
  mise run run-jvm
  # or
  ./gradlew :demo-jvm:run
  ```

</details>

<details>
<summary><strong>3. Modern Web Application with Vite & TypeScript (<code>demo-vite</code>)</strong> &mdash; Frontend bundler integration</summary>

- **Directory**: [`demo-vite`](demo-vite)
- Demonstrates how to consume the compiled WebAssembly terminal emulator inside a modern TypeScript single-page application using Vite.
- Includes interactive UI controls for real-time theme switching (Default, Monokai, Nord, Solarized Dark/Light), cursor style selection (`BLOCK`, `BEAM`, `UNDERLINE`), font size scaling, and one-click ANSI stress-testing scripts (color matrices, progress bars, matrix rain, htop alt-buffer).
- Run locally:
  ```bash
  cd demo-vite
  npm install
  npm run dev
  ```

</details>

<details>
<summary><strong>4. Zero-Framework Vanilla HTML/JS Integration (<code>demo-html</code>)</strong> &mdash; Direct DOM mounting over static HTTP</summary>

- **Directory**: [`demo-html`](demo-html)
- Demonstrates how to mount, control, and receive events from the compiled Wasm terminal emulator directly in plain HTML and JavaScript without any frontend framework or bundler.
- Run locally with any static web server:
  ```bash
  npx http-server demo-html -p 8083
  # or
  python3 -m http.server -d demo-html 8083
  ```

</details>

<details>
<summary><strong>5. Web Distribution Package (<code>npm-package</code>)</strong> &mdash; Pre-packaged NPM bundle</summary>

- **Directory**: [`npm-package`](npm-package)
- A self-contained npm-ready package (`@JohNan/kmp-terminal`) generated via `./build-npm.sh` that bundles the production WebAssembly executable, JavaScript interop loader (`demo-web.js`), and Compose web resources for integration in web applications.

</details>

<details>
<summary><strong>6. Mobile Android & iOS Showcase (<code>demo-mobile</code>)</strong> &mdash; Native Compose Multiplatform Mobile App</summary>

- **Module**: [`demo-mobile`](demo-mobile)
- A shared Compose Multiplatform mobile application targeting Android (APK) and iOS (`ComposeUIViewController` export).
- Features an interactive mobile REPL with a floating soft-key bar (Ctrl, Alt, Shift, Esc, Tab, Arrows), virtual keyboard toggle, touch-drag selection handles, and quick-action ANSI test chips (Colors, Styles, Unicode, Matrix rain, Htop alternate buffer).
- Run on Android:
  ```bash
  mise run assemble-android
  # or
  ./gradlew :demo-mobile:assembleDebug
  ```
- Compile for iOS Simulator:
  ```bash
  mise run compile-ios-demo
  # or
  ./gradlew :demo-mobile:compileKotlinIosSimulatorArm64
  ```

</details>

<details>
<summary><strong>7. Native C-ABI & Rust FFI Integration (<code>demo-rust</code>)</strong> &mdash; Safe Rust bindings over Kotlin/Native</summary>

- **Directory**: [`demo-rust`](demo-rust)
- Safe, idiomatic Rust RAII bindings (`kmp-terminal-rs`) interfacing with Kotlin/Native `:terminal-core` compiled as a native C-ABI dynamic (`libterminal_core.so` / `libterminal_core.dylib`) or static (`libterminal_core.a`) library.
- Demonstrates zero-allocation bulk grid rendering via `packGridToBuffer`, synchronous ANSI stream processing, selection extraction, and ANSI terminal rendering.
- Run tests:
  ```bash
  mise run test-rust
  ```
- Run CLI showcase demo:
  ```bash
  mise run run-rust
  ```

</details>

---

## Production Showcase

`kmp-terminal` powers the terminal rendering canvas and ANSI state machine in production:

### [SSH Client - Terminal & SFTP](https://play.google.com/store/apps/details?id=com.johnan.sshclient)
A secure, high-performance Android SSH & SFTP client with hardware-backed encryption, multi-session management, and custom quick key bars.

- **Engine Core**: Uses `:terminal-core` for ANSI/VT100 parsing, TrueColor support, and circular scrollback buffer management.
- **Rendering Pipeline**: Uses `:terminal-ui` for low-allocation Compose Multiplatform canvas rendering, touch gestures, and responsive IME soft-keyboard input capture.
- **Product Website**: [ssh-client.nanzen.se](https://ssh-client.nanzen.se/)
- **Google Play**: [Download on Google Play](https://play.google.com/store/apps/details?id=com.johnan.sshclient)

---

## Key Features

- **Decoupled Tiered Architecture**: 
  - `:terminal-core`: Pure Kotlin multiplatform terminal state machine, screen buffer, ANSI/VT100 parser, `TerminalPalette`, and text selection models. Zero UI dependencies. Compiles to native C-ABI shared (`libterminal_core.so` / `libterminal_core.dylib`) and static (`libterminal_core.a`) libraries on Linux and macOS.
  - `:terminal-ui`: Hardware-accelerated Compose Multiplatform canvas renderer, gesture recognizers, platform-native input capture, selection overlays, toolbar key bars, automatic DECSCUSR dynamic cursor shape mapping, OSC 10/11/12 live color overrides, OSC 133 prompt mark gutter indicators, and Kitty Progressive Keyboard input dispatch.
  - `:terminal-tui`: Low-allocation differential ANSI terminal UI renderer, 24-bit TrueColor, offscreen grid surface (`TuiSurface`), extended underline styles (`UnderlineStyle` / SGR `4:x`), 24-bit underline RGB (SGR `58;2;r;g;b`), overline (SGR `53`), OSC 8 explicit hyperlinks, and raw VT100 / SGR input parser (`TuiInputParser`).
- **Native C-ABI & Zero-Allocation FFI Interop**:
  - Direct C-ABI dynamic and static library exports from `:terminal-core` for non-JVM / non-Compose runtimes.
  - Additive synchronous execution APIs (`processOutputSync`, `resizeSync`, `clearSync`, `resetSync`, `searchSync`).
  - Zero-allocation bulk grid serializer (`packGridToBuffer`) packing cell data into contiguous integers (codepoint, fg RGB, bg RGB, attribute bitmask) for high-throughput FFI consumers (Rust, C, C++).
  - Full protocol parity across C-ABI and Rust FFI (`kmp-terminal-rs` in `demo-rust`), providing `CursorShape`, `UnderlineStyle`, response draining (`drain_response`), Kitty keyboard flags (`kitty_keyboard_flags`), key event encoding (`encode_key_event`), working directory tracking (`working_directory`), exit codes (`last_exit_code`), command execution status (`is_command_running`), and semantic output extraction (`extract_last_command_output`).
- **Type-Safe Configuration & Palette System**:
  - `TerminalConfig` & `terminalConfig { ... }` DSL for core engine dimensions, scrollback capacity, OSC 52 security policies, tab stops, and bell behavior.
  - `TerminalPalette`: Pure Kotlin 24-bit RGB color palette models and fast ANSI code resolution (`TerminalColor.toRgbInt`).
  - `TerminalUiConfig` & `terminalUiConfig { ... }` DSL for typography, cursor styles (`BLOCK`, `UNDERLINE`, `BEAM`), gesture controls, key bar layouts, and color themes.
  - Ambient Compose propagation via `LocalTerminalUiConfig`.
- **Reactive State & Attention Events**:
  - `screenState` StateFlow emitting immutable buffer snapshots on change.
  - Reactive shell integration flows: `currentWorkingDirectory` (`StateFlow<String?>`), `lastExitCode` (`StateFlow<Int?>`), and `isCommandRunning` (`StateFlow<Boolean>`).
  - Reactive event flows: `attentionEvents` (OSC 9 / OSC 777 desktop notifications), `bellEvents` / `bellCount`, `clipboardEvents` / `clipboardWriteCount`, and `windowTitle`.
- **Semantic Shell Integration & Explicit Hyperlinks**:
  - **OSC 8 Explicit Hyperlinks**: Supports inline explicit URL hyperlinks (`\e]8;[params];<url>\e\` or `\e]8;[params];<url>\a`), populating `TerminalCell.hyperlinkUrl`. `ScreenBuffer.getUrlRanges(row)` and `ScreenBuffer.extractUrl(row, range)` return explicit URLs via `UrlRange.url` without colliding with regex-detected plain-text URLs on the same line, complete with automatic `TerminalRenderer` / `TerminalCanvas` click handling and reflow preservation.
  - **OSC 7 & OSC 9;9 Current Working Directory**: Parses shell working directory URIs (`\e]7;file://hostname/path\a`) with RFC 3986 percent-decoding (including multibyte UTF-8) and Windows Terminal / ConEmu markers (`\e]9;9;"path"\a`), updating `TerminalEmulator.currentWorkingDirectory` silently without triggering desktop notification events.
  - **OSC 133 Semantic Prompt Marks (FTCS)**: Full FinalTerm semantic shell integration (`133;A` prompt start, `133;B` command start, `133;C` output start, `133;D;[exitCode]` command finished). Tracks execution status in `isCommandRunning` and `lastExitCode`, records `PromptMark` boundaries, enables prompt navigation (`findPreviousPromptLine()`, `findNextPromptLine()`), and cleanly isolates command output via `extractLastCommandOutput()` excluding prompts and typed commands.
- **Text Selection & Clipboard Management**:
  - Multi-line text selection overlay with visual start/end handle indicators.
  - Drag-selection gesture tracking, long-press activation, and auto-copy on release.
  - Plain-text extraction with ANSI sequence stripping and scrollback boundary support.
- **ANSI & Modern Terminal Standards**:
  - 16 standard/bright ANSI colors, 256 indexed colors, 24-bit TrueColor RGB.
  - Cursor movement, scrolling regions (DECSTBM), alternate screen buffer (`altBuffer` for vim/htop/less).
  - Extended Underline Styles & Colors (SGR 4:x / 21 / 58:2): ITU-T T.416 sub-parameter styled underlines (`4:0` none, `4:1` single, `4:2`/`21` double, `4:3` curly, `4:4` dotted, `4:5` dashed) with 24-bit TrueColor underline coloring (`58:2::r:g:b` / `58:5:c` and reset `59`).
  - Dynamic Cursor Shapes (DECSCUSR `\e[ q`): Dynamic cursor shape switching between blinking/steady block, underline, and beam shapes (`CursorShape.BLOCK`, `CursorShape.UNDERLINE`, `CursorShape.BEAM`).
  - Synchronized Output (DEC Mode 2026 `\e[?2026h` / `\e[?2026l`): Atomic screen updates eliminating tearing and flickering during high-frequency frame rendering in modern CLI and TUI apps.
  - Focus Reporting (DEC Mode 1004 `\e[?1004h` / `\e[?1004l`): Emits `\e[I` on terminal window/input focus gain and `\e[O` on focus loss.
  - Terminal Capabilities & Query Modes: Primary & Secondary Device Attributes (DA1 / DA2 `\e[>c`), Terminal Version reporting (XTVERSION `\e[>0q`), and DEC Request Mode (DECRQM `\e[?<mode>$p`).
  - SGR Mouse Tracking (1006) and legacy xterm mouse tracking (press, release, drag, wheel).
  - OSC 52 clipboard integration with configurable security policies (`ASK`, `ALWAYS_ALLOW`, `ALWAYS_DENY`).
  - Independent deep state cloning (`TerminalEmulator.copy()`) for headless sessions and speculative execution (e.g. Mosh).
  - Soft-wrap row querying (`ScreenBuffer.isRowWrapped(row)`).
- **OSC Dynamic Color Overrides & DCS Queries**:
  - **OSC 4 / 10 / 11 / 12 Color Queries & Overrides**: Dynamic query and override of foreground (`OSC 10`), background (`OSC 11`), cursor (`OSC 12`), and 256-color palette slots (`OSC 4 ; <idx>`) using X11 color specifications (`#RGB`, `#RRGGBB`, `#RRRRGGGGBBBB`, and `rgb:r/g/b`, `rgb:rr/gg/bb`, `rgb:rrrr/gggg/bbbb`), formatting 16-bit RGB query responses (`\e]10;rgb:rrrr/gggg/bbbb\e\`).
  - **OSC 104 / 110 / 111 / 112 Color Resets**: Restores original palette definitions dynamically for indexed colors (`OSC 104 [; <idx>]`), foreground (`OSC 110`), background (`OSC 111`), and cursor (`OSC 112`).
  - **DCS Capability Negotiation (XTGETTCAP `DCS + q`)**: Terminal capability query support returning hex-encoded key-value pairs for `TN` (`xterm-256color`), `Co` (`256`), `RGB`/`Tc` (TrueColor), `Smulx` (styled underlines), `Setulc` (underline color), `Sync` (synchronized output), and `Ms` (OSC 52 clipboard).
  - **DCS Request Status String (DECRQSS `DCS $ q`)**: Reports active terminal modes and graphic renditions (`m`), 1-indexed scrolling margins (`r`), and active cursor shape (` q`).
- **Kitty Graphics Protocol & Unicode Placeholders**:
  - Full APC control sequence handling (`\e_G...;\e\`) for inline images with hardware-accelerated canvas rendering.
  - Protocol formats: 24-bit RGB (`f=24`), 32-bit RGBA (`f=32`), and pure KMP zero-dependency PNG (`f=100`).
  - Compression: Pure Kotlin RFC 1950/1951 Deflate/zlib decompressor (`o=z`) supporting single-chunk and multi-chunk (`m=1`/`m=0`) transfers.
  - Control commands: Transmit (`a=t`), Transmit & Display (`a=T`), Place (`a=p`), Delete (`a=d`), and Query (`a=q`).
  - Unicode Placeholders (`U=1`, `U+10EEEE`) using the canonical 297-diacritic encoding table for seamless scrolling and reflow inside `tmux` and `zellij`.
  - Layering and z-index ordering: 3-pass draw loop respecting negative, background, text, and overlay z-indices.
  - Safety & Geometry: Configurable `maxImageMemoryBytes` with automatic LRU texture eviction, inflation bomb protection, and subpixel cell geometry via `setCellDimensionsPx`.
- **Kitty Progressive Keyboard Protocol**:
  - Full negotiation and flag stack management (`CSI > <flags> u` push, `CSI < <count> u` pop, `CSI = <flags> ; <mode> u` set, `CSI ? u` query).
  - Progressive enhancement flags: `DISAMBIGUATE_ESCAPE_CODES` (1), `REPORT_EVENT_TYPES` (2), `REPORT_ALTERNATE_KEYS` (4), `REPORT_ALL_KEYS_AS_ESCAPE_CODES` (8), and `REPORT_ASSOCIATED_TEXT` (16).
  - 16-deep LIFO flag stack with automatic oldest eviction on overflow, independent stack isolation across primary and alternate screens (`?1049h` / `?1049l`), and reset synchronization on RIS (`\ec`) and `resetSync()`.
  - Disambiguation from legacy `SCORC` (`\e[u` cursor restore).
  - Key event encoding: `KittyKeyboardProtocol.encodeKeyEvent(...)` and `TerminalEmulator.encodeKeyEvent(...)` with modifiers, event types (`PRESS`, `REPEAT`, `RELEASE`), shifted codepoints, and associated text.
  - UI input dispatch: `:terminal-ui` `applyModifierToChar` and `handleHardwareKeyEvent` accept `kittyKeyboardFlags` to transparently emit unambiguous escape codes (`\e[<code-point>;<modifiers>u`).
  - TUI integration: `:terminal-tui` `TuiInputParser` parses `CSI u` sequences and functional keys with `:<eventType>` sub-parameters, and encodes keys using `toAnsiSequence(..., kittyKeyboardFlags = flags)`.
- **Mobile & Desktop Responsive Features**:
  - Soft keyboard modifier toggle integration (Ctrl, Alt, Shift states).
  - Monospace cell measurement with sub-pixel precision to eliminate column drift.
  - Low-allocation batching render loop with LRU cache.
  - Touch-scroll disambiguation (`touchScrollSendsWheelOnly`) to send mouse wheel events on mobile drag gestures in tmux and vim.
- **Multiplatform Target Support**:
  - WebAssembly (`wasmJs`)
  - Desktop (`jvm`)
  - Android (`androidMain`)
  - iOS (`iosArm64`, `iosSimulatorArm64`)
  - macOS (`macosArm64`, `macosX64`)
  - Linux (`linuxX64`)
  - Native C-ABI / FFI (`linuxX64`, `macosArm64`, `macosX64`) via `libterminal_core`

---

## Project Structure

```
kmp-terminal/
├── terminal-core/       # Pure Kotlin Multiplatform emulation engine & screen buffer (Zero UI dependencies)
├── terminal-ui/         # Compose Multiplatform canvas renderer & UI components
├── terminal-tui/        # Terminal User Interface (TUI) differential renderer & raw input parser
├── demo-mobile/         # Android & iOS Compose Multiplatform mobile showcase
├── demo-web/            # WebAssembly (wasmJs) interactive demo & JS bridge (deployed to GitHub Pages)
├── demo-jvm/            # JVM Desktop Compose demo (macOS, Linux, Windows)
├── demo-vite/           # Vite + TypeScript web application integrating Wasm terminal
├── demo-html/           # Vanilla HTML/JS zero-framework integration demo
├── demo-rust/           # Safe Rust FFI wrapper crate & CLI demo linking libterminal_core
└── npm-package/         # NPM package bundle (@JohNan/kmp-terminal)
```

---

## Installation

Add the dependencies to your multiplatform `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core ANSI engine, scrollback buffer, and text selection (zero UI dependencies)
            implementation("io.github.johnan:terminal-core:0.3.11")

            // Optional: Compose Multiplatform canvas renderer & gestures
            implementation("io.github.johnan:terminal-ui:0.3.11")

            // Optional: Terminal User Interface (TUI) differential renderer & raw input engine
            implementation("io.github.johnan:terminal-tui:0.3.11")
        }
    }
}
```

---

## Usage Guide

### 1. Core Engine Setup (`terminal-core`)

Create a `TerminalEmulator` instance with default or DSL-configured parameters:

```kotlin
import com.johnan.terminal.core.BellBehavior
import com.johnan.terminal.core.Osc52Policy
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.terminalConfig

// DSL configuration
val config = terminalConfig {
    initialRows = 24
    initialCols = 80
    maxScrollback = 2000
    osc52Policy = Osc52Policy.ALWAYS_ALLOW
    bellBehavior = BellBehavior.VISUAL
    kittyGraphicsEnabled = true
    maxImageMemoryBytes = 32 * 1024 * 1024 // 32MB LRU image cache
}

val emulator = TerminalEmulator(
    config = config,
    onTerminalResponse = { response ->
        // Send terminal response back to PTY / SSH stream (e.g. CPR, DA1, mouse events)
    }
)

// Process incoming ANSI byte streams
emulator.processOutput("Hello \u001B[32mGreen\u001B[0m World!\r\n")
```

### 2. UI Rendering with Compose Multiplatform (`terminal-ui`)

Render the terminal screen state using `TerminalRenderer`:

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.ui.TerminalCursorStyle
import com.johnan.terminal.ui.TerminalRenderer
import com.johnan.terminal.ui.terminalUiConfig

@Composable
fun TerminalScreen(emulator: TerminalEmulator) {
    val terminalState by emulator.screenState.collectAsState()

    val uiConfig = terminalUiConfig {
        typography {
            fontSize = 15.sp
            letterSpacing = 0.sp
        }
        cursor {
            style = TerminalCursorStyle.BEAM // BLOCK, UNDERLINE, or BEAM
            blink = true
            overrideColor = Color(0xFF00FFCC)
        }
        gestures {
            enableTouchToFocus = true
            enableSelection = true
            wheelScrollMultiplier = 1.5f
            touchScrollSendsWheelOnly = true // Opt-in wheel-only touch scrolling for tmux/vim
        }
        render {
            showPromptMarks = true // 3dp left gutter indicators for OSC 133 semantic prompts
        }
    }

    TerminalRenderer(
        terminalState = terminalState,
        config = uiConfig,
        onInput = { input -> 
            // Forward user keyboard input to PTY / SSH channel
        },
        onArrowKey = { arrow, shift ->
            // Forward arrow navigation
        },
        onCellMeasured = { widthPx, heightPx ->
            emulator.setCellDimensionsPx(widthPx.toInt(), heightPx.toInt())
        },
        onLog = { msg -> println("[TerminalLog] $msg") },
        modifier = Modifier.fillMaxSize()
    )
}
```

#### Automatic Protocol Visual Synchronization
- **Dynamic Cursor Shapes (`DECSCUSR`)**: `TerminalRenderer` automatically maps `terminalState.cursorShape` (`CursorShape.BLOCK`, `UNDERLINE`, `BEAM`) to `TerminalCursorStyle` unless explicitly overridden by `config.cursor.style`.
- **Dynamic Color Overrides (`OSC 10/11/12`)**: Live background (`terminalState.dynamicBackgroundRgb`) and cursor (`terminalState.dynamicCursorRgb`) colors emitted by shells and utilities are applied directly during canvas rendering.
- **Semantic Prompt Marks (`OSC 133`)**: When `render.showPromptMarks = true` (default), 3dp status bars appear in the left gutter next to command lines (green for `exitCode == 0`, red for non-zero exit codes, cyan while awaiting completion).
```

### 3. Text Selection and Copy Mode

Text selection is managed through `TerminalSelection` and `SelectionState`:

```kotlin
import com.johnan.terminal.core.TerminalSelection

// Programmatically extract text from a selection
val selection = TerminalSelection(startRow = 0, startCol = 0, endRow = 2, endCol = 40)
val selectedText = selection.extractText(screenBuffer)

// Selection containment check
val isInside = selection.contains(row = 1, col = 10)
```

### 4. Forking state (`TerminalEmulator.copy()`)

`copy()` returns a fully independent deep copy, including a partially parsed escape or UTF-8 sequence. This lets you
keep one headless emulator per received state (for example Mosh) and apply a diff to a copy of the reference state.
`bellCount`, `clipboardWriteCount` and `lastClipboardWrite` let you diff event-only state between snapshots, and
`ScreenBuffer.isRowWrapped(row)` exposes the soft-wrap flag.

```kotlin
val reference = TerminalEmulator(TerminalConfig(initialRows = 24, initialCols = 80))
reference.processOutput("hello")

val next = reference.copy()           // callbacks are not inherited; pass them if needed
next.processOutput("\u001B[31m world") // diff applied to the copy only

val bellRang = next.bellCount > reference.bellCount
val clipboardChanged = next.clipboardWriteCount > reference.clipboardWriteCount
val firstRowWraps = next.getScreenBuffer().isRowWrapped(0)
```

### 5. Native C-ABI & Rust FFI Integration (`terminal-core` / `demo-rust`)

`:terminal-core` exports a flat C-ABI allowing native runtimes (Rust, C, C++) to drive the terminal emulator without JVM overhead. The `demo-rust` crate (`kmp-terminal-rs`) provides safe, idiomatic Rust RAII bindings, zero-allocation bulk grid reading, and full protocol parity:

```rust
use kmp_terminal_rs::{CursorShape, PackedCell, Palette, Terminal, UnderlineStyle};

fn main() {
    // Instantiate terminal emulator via C-ABI (rows, cols)
    let mut term = Terminal::new(24, 80);

    // Process ANSI byte stream synchronously
    term.process_str("\x1b]133;A\x1b\\\x1b]7;file://localhost/home/user\x1b\\prompt$ \x1b]133;B\x1b\\cargo build\r\n\x1b]133;C\x1b\\Compiling...\r\n\x1b]133;D;0\x1b\\");

    // Inspect semantic shell integration state
    println!("Working directory: {:?}", term.working_directory());
    println!("Command running: {}", term.is_command_running());
    println!("Last exit code: {:?}", term.last_exit_code());
    println!("Last command output: {:?}", term.extract_last_command_output());

    // Drain pending terminal responses (DA1, CPR, DECRQM)
    let mut resp = [0u8; 256];
    if let Some(bytes) = term.drain_response(&mut resp) {
        println!("Terminal response: {:?}", String::from_utf8_lossy(bytes));
    }

    // Query active cursor shape and Kitty keyboard protocol flags
    let cursor_shape: CursorShape = term.cursor_shape();
    let kitty_flags = term.kitty_keyboard_flags();

    // Encode key events using Kitty progressive keyboard protocol
    let encoded_key = term.encode_key_event(b'a' as i32, 0, 1, 0, None);

    // Zero-allocation bulk grid reading into pre-allocated buffer
    let mut cells = vec![
        PackedCell {
            codepoint: ' ' as i32,
            fg_rgb: 0,
            bg_rgb: -1,
            attributes: 0,
        };
        (term.rows() * term.cols()) as usize
    ];
    let count = term.read_grid_into(&mut cells, Palette::Default);

    for cell in &cells[..count] {
        if cell.codepoint != 0 && cell.codepoint != 32 {
            println!(
                "Cell: char='{}', fg={:06x}, bg={:06x}, underline={:?}, overline={}",
                cell.ch(),
                cell.fg_rgb,
                cell.bg_rgb,
                cell.underline_style(),
                cell.attrs().overline()
            );
        }
    }
}
```

### 6. TUI Differential Rendering & Raw Input (`terminal-tui`)

Build native command-line interfaces and terminal applications using `TuiSurface` and `TuiAnsiRenderer`:

```kotlin
import com.johnan.terminal.core.UnderlineStyle
import com.johnan.terminal.tui.TuiAnsiRenderer
import com.johnan.terminal.tui.TuiCellStyle
import com.johnan.terminal.tui.TuiInputParser
import com.johnan.terminal.tui.TuiSurface
import com.johnan.terminal.tui.createPlatformTerminalIo

val io = createPlatformTerminalIo()
io.enterRawTuiMode()
try {
    val size = io.getTerminalSize()
    val surface = TuiSurface(rows = size.rows, cols = size.cols)
    val renderer = TuiAnsiRenderer()
    val parser = TuiInputParser()

    // Draw borders, titles, and text
    surface.drawBox(row = 0, col = 0, height = size.rows, width = size.cols, title = "Console", borderFgRgb = 0x88C0D0, bgRgb = 0x2E3440)
    surface.drawText(row = 2, col = 4, text = "Ready.", fgRgb = 0xECEFF4, bgRgb = 0x2E3440)

    // Set cell with styled underline (SGR 4:3), RGB underline color (SGR 58;2;r;g;b), overline (SGR 53), and OSC 8 hyperlink
    val styledAttr = TuiCellStyle.withUnderlineStyle(
        TuiCellStyle.BOLD or TuiCellStyle.OVERLINE,
        UnderlineStyle.CURLY,
    )
    surface.setCell(
        row = 3,
        col = 4,
        char = '🔗',
        fgRgb = 0x88C0D0,
        bgRgb = 0x2E3440,
        style = styledAttr,
        underlineRgb = 0xBF616A,
        hyperlinkUrl = "https://github.com/JohNan/kmp-terminal-emulator",
    )

    // Atomically render differential updates to stdout
    io.writeStdout(renderer.render(surface))
} finally {
    io.exitRawTuiMode()
}
```

### 7. Semantic Shell Integration & Hyperlinks

Inspect current working directory, command execution status, prompt marks, and OSC 8 hyperlinks:

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.johnan.terminal.core.PromptMarkType
import com.johnan.terminal.core.ScreenBuffer

// Reactive shell state flows
val cwd by emulator.currentWorkingDirectory.collectAsState()
val isRunning by emulator.isCommandRunning.collectAsState()
val exitCode by emulator.lastExitCode.collectAsState()

// Navigate between semantic prompt marks (FTCS OSC 133;A)
val prevPromptLine = emulator.findPreviousPromptLine()
val nextPromptLine = emulator.findNextPromptLine()

// Cleanly copy output of the last executed command (without prompt or command line)
val lastCommandOutput = emulator.extractLastCommandOutput()

// Query explicit hyperlinks (OSC 8) and plain-text URLs on any terminal row
val row = emulator.screenState.value.rows[0]
val urlRanges = ScreenBuffer.getUrlRanges(row)
for (range in urlRanges) {
    val resolvedUrl = ScreenBuffer.extractUrl(row, range)
    val isExplicitOsc8 = range.url != null // true for OSC 8 hyperlinks, null for regex matches
}
```

### 8. Kitty Progressive Keyboard Protocol (`terminal-core`, `terminal-tui` & `terminal-ui`)

Applications query or configure enhanced keyboard modes using Kitty protocol CSI sequences. Active flags are published in `kittyKeyboardFlags`, and key events can be encoded, parsed, or dispatched through Compose with full fidelity:

```kotlin
import com.johnan.terminal.core.KeyEventType
import com.johnan.terminal.core.KittyKeyboardProtocol
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.tui.TuiEvent
import com.johnan.terminal.tui.TuiInputParser
import com.johnan.terminal.tui.TuiKey
import com.johnan.terminal.ui.applyModifierToChar
import com.johnan.terminal.ui.handleHardwareKeyEvent

// Inspect active keyboard enhancement flags
val activeFlags = emulator.kittyKeyboardFlags
val screenFlags = emulator.screenState.value.kittyKeyboardFlags

// Encode key events using active emulator flags
val escSeq = emulator.encodeKeyEvent(codepoint = 27) // "\u001B[27u" when DISAMBIGUATE_ESCAPE_CODES is active
val ctrlASeq = emulator.encodeKeyEvent(
    codepoint = 'a'.code,
    modifiers = KittyKeyboardProtocol.MODIFIER_CTRL,
    eventType = KeyEventType.REPEAT,
) // "\u001B[97;5:2u" with REPORT_EVENT_TYPES

// Parse CSI u sequences and functional keys in TUI apps (:terminal-tui)
val parser = TuiInputParser()
val events = parser.parse("\u001b[97;5u".encodeToByteArray())
// -> TuiEvent.Key(key = TuiKey.CHAR, char = 'a', ctrl = true, eventType = 1)

// Encode TuiEvent keys to ANSI sequences matching active flags
val outSeq = parser.toAnsiSequence(
    event = TuiEvent.Key(key = TuiKey.ESCAPE),
    kittyKeyboardFlags = activeFlags,
)

// Compose UI soft-modifier and hardware key integration (:terminal-ui)
val modifiedChar = applyModifierToChar('a', modifierState, kittyKeyboardFlags = activeFlags)
// -> "\u001B[97;5u" under DISAMBIGUATE_ESCAPE_CODES

handleHardwareKeyEvent(
    keyEvent = hardwareKeyEvent,
    onInput = { input -> /* send to PTY */ },
    onArrowKey = { arrow, shift -> /* handle arrow */ },
    kittyKeyboardFlags = activeFlags,
)
```

### 9. Dynamic Color Queries & DCS Capability Negotiation

Applications query terminal capabilities via XTGETTCAP / DECRQSS and dynamically query or override theme colors via OSC sequences:

```kotlin
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.TerminalPalette

val emulator = TerminalEmulator()

// Inspect dynamic color overrides (null if default palette is in use)
val fgOverride = emulator.dynamicForegroundRgb
val bgOverride = emulator.dynamicBackgroundRgb
val cursorOverride = emulator.dynamicCursorRgb
val paletteColor1 = emulator.getDynamicPaletteColor(1)

// Query effective colors (resolving dynamic override or falling back to palette)
val effectiveFg = emulator.getEffectiveForegroundRgb(TerminalPalette.DEFAULT)
val effectiveBg = emulator.getEffectiveBackgroundRgb(TerminalPalette.DEFAULT)
val effectiveCursor = emulator.getEffectiveCursorRgb(TerminalPalette.DEFAULT)
val effectiveRed = emulator.getEffectivePaletteRgb(1, TerminalPalette.DEFAULT)

// Reactive updates published to screenState
val state = emulator.screenState.value
val screenFg = state.dynamicForegroundRgb
val screenBg = state.dynamicBackgroundRgb
val screenCursor = state.dynamicCursorRgb
```

---

## Tooling & Verification

This project uses `mise` for reproducible toolchain management (JDK 21, Android SDK, Gradle, Node 20).

```bash
# Compile WebAssembly target
mise run compile-wasm

# Run local WebAssembly browser demo
mise run run-demo

# Run native JVM Desktop demo
mise run run-jvm

# Assemble Android debug APK (demo-mobile)
mise run assemble-android

# Compile iOS simulator target (demo-mobile)
mise run compile-ios-demo

# Execute Rust FFI unit & integration tests
mise run test-rust

# Run Rust CLI showcase demo
mise run run-rust

# Build all targets
mise run build

# Execute all multiplatform unit tests
mise exec -- ./gradlew allTests

# Run fast unit test suite
mise exec -- ./gradlew test

# Verify Kotlin code formatting and linting
mise exec -- ./gradlew ktlintCheck
```

---

## Contributing & Community

Contributions are welcome! Please read through our community guides:
- [Contributing Guidelines](CONTRIBUTING.md) — Toolchain setup, testing, and architecture invariants.
- [Code of Conduct](CODE_OF_CONDUCT.md) — Contributor pledge and standards.
- [Security Policy](SECURITY.md) — Responsible vulnerability reporting.
- [Migration Guide](MIGRATION.md) — Before/after recipes and upgrade paths.

---

## License

Distributed under the [Apache License, Version 2.0](LICENSE). Free for commercial and private use with attribution required and express patent grant.
