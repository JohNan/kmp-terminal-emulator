import './style.css'

// Declare types for window bindings
interface ExtendedWindow extends Window {
  writeToTerminal?: (text: string) => void;
  resizeTerminal?: (rows: number, cols: number) => void;
  focusTerminal?: () => void;
  onTerminalInput?: (input: string) => void;
}

const extWindow = window as unknown as ExtendedWindow;

console.log("Vite TS application loaded!");

// Setup input callback globally BEFORE window load completes, so Wasm registers it
extWindow.onTerminalInput = (input: string) => {
  console.log("TS User Input:", JSON.stringify(input));
  // Echo input back to the terminal (in real life this goes to SSH socket)
  if (extWindow.writeToTerminal) {
    extWindow.writeToTerminal(input);
  }
};

window.addEventListener('load', () => {
  setTimeout(() => {
    if (typeof extWindow.writeToTerminal !== 'function') {
      console.error("writeToTerminal not found. Wasm bundle might still be loading.");
      return;
    }

    // Write initial welcome text
    extWindow.writeToTerminal("\r\n\u001B[1;36mKMP WebAssembly Terminal - Vite TS Demo\u001B[0m\r\n");
    extWindow.writeToTerminal("Ready to echo inputs. Try typing or clicking buttons below!\r\n\r\n");
  }, 300);

  // Bind UI buttons
  document.querySelector('#btn-msg')?.addEventListener('click', () => {
    if (extWindow.writeToTerminal) {
      extWindow.writeToTerminal("\r\n[Sent from Vite parent component]\r\n");
    }
  });

  document.querySelector('#btn-rainbow')?.addEventListener('click', () => {
    if (extWindow.writeToTerminal) {
      extWindow.writeToTerminal("\r\nRainbow: \u001B[31mR\u001B[32mG\u001B[33mB\u001B[34mC\u001B[35mM\u001B[36mY\u001B[0m\r\n");
    }
  });

  document.querySelector('#btn-clear')?.addEventListener('click', () => {
    if (extWindow.writeToTerminal) {
      extWindow.writeToTerminal("\u001B[2J\u001B[H");
    }
  });

  document.querySelector('#btn-focus')?.addEventListener('click', () => {
    if (extWindow.focusTerminal) {
      extWindow.focusTerminal();
    }
  });
});
