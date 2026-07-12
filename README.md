# KMP Terminal Emulator

A Kotlin Multiplatform (KMP) terminal emulator engine and Compose Multiplatform rendering library. This library provides a standalone, reusable, GPU-accelerated terminal emulator designed to replace standard terminal integrations with fully-customizable Compose Multiplatform UI components.

## Features

- **Decoupled Architecture**: Separate `terminal-core` (pure Kotlin emulation engine) and `terminal-ui` (Compose Multiplatform canvas renderer).
- **Multiplatform Support**: Compiles to WebAssembly (`wasmJs`), Desktop (`jvm`), Android, and iOS.
- **Pinch-to-Zoom & Zoom Control**: Smooth canvas scaling, font measurement, and sizing.
- **Modifier Key Integration**: Full soft-keyboard modifier support (Ctrl, Alt, Shift states) with customized key bars.
- **Text Selection & Clipboard**: Built-in drag-selection overlay, selection handles, and automatic copy actions.
- **Search Highlighting**: Search overlay highlights matching terminal outputs dynamically.

## Project Structure

- `:terminal-core`: Core emulation engine that maintains the screen buffer, parses ANSI/VT100 escape sequences, and manages selection states.
- `:terminal-ui`: Compose Multiplatform components (`TerminalRenderer`, `TerminalCanvas`, `TerminalKeyBar`, selection overlays, input handlers).
- `:demo-web`: An interactive WebAssembly (`wasmJs`) web app showcasing a local interactive shell REPL inside the Compose Terminal viewport.

## Getting Started

### Prerequisites

This project uses `mise` to automatically manage Java (JDK 21) and the Android SDK. Ensure `mise` is installed in your system:
```bash
# Install tools using mise
mise install
```

### Building the Project

To build the core and UI modules for all platforms:
```bash
mise run build
```

To compile specifically for WebAssembly (`wasmJs`):
```bash
mise run compile-wasm
```

### Running the interactive WebAssembly Proof of Concept

To boot the local development server for the WebAssembly REPL shell:
```bash
mise run run-demo
```
Once compilation is complete, open the local development URL (typically `http://localhost:8080`) in your browser.

## Customization

### Terminal Theme / Color Scheme

Pass a custom `TerminalColorScheme` to the `TerminalRenderer` or use `TerminalColorScheme.DEFAULT`:
```kotlin
TerminalRenderer(
    terminalState = terminalState,
    colorScheme = TerminalColorScheme.DEFAULT,
    fontSize = 14f,
    onInput = { input -> /* send to PTY/Server */ },
    onArrowKey = { arrow, shift -> /* handle arrow inputs */ }
)
```
