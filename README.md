# KMP Terminal Emulator

[![CI](https://github.com/JohNan/kmp-terminal-emulator/actions/workflows/ci.yml/badge.svg)](https://github.com/JohNan/kmp-terminal-emulator/actions/workflows/ci.yml)
[![GitHub Pages](https://img.shields.io/badge/GitHub_Pages-Live_Demo-brightgreen.svg?logo=github)](https://johnan.github.io/kmp-terminal-emulator/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.4.20-purple.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.11.1-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Target-Android-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/Target-iOS-000000.svg?logo=apple&logoColor=white)](https://developer.apple.com/ios/)
[![JVM](https://img.shields.io/badge/Target-JVM_Desktop-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)
[![WebAssembly](https://img.shields.io/badge/Target-WasmJs-654FF0.svg?logo=webassembly&logoColor=white)](https://webassembly.org/)
[![Google Play](https://img.shields.io/badge/Google_Play-Production_App-34A853?logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.johnan.sshclient)

A modern Kotlin Multiplatform (KMP) terminal emulator engine and Compose Multiplatform rendering library. This library provides a standalone, high-performance, GPU-accelerated terminal emulator designed for mobile, desktop, and web applications.

👉 **[🚀 Try Live Interactive WebAssembly Demo on GitHub Pages](https://johnan.github.io/kmp-terminal-emulator/)**

---

## Interactive Demos & Showcase

The repository includes multiple demo implementations targeting different environments and runtimes:

### 1. [Live WebAssembly Demo (GitHub Pages)](https://johnan.github.io/kmp-terminal-emulator/)
- **Live URL**: [https://johnan.github.io/kmp-terminal-emulator/](https://johnan.github.io/kmp-terminal-emulator/)
- **Module**: [`demo-web`](demo-web)
- An interactive in-browser terminal REPL compiled via Kotlin/Wasm (`wasmJs`) and rendered with Compose Multiplatform on a GPU-accelerated Skia canvas.
- Automatically built and deployed on pushes to `main` via [GitHub Actions](.github/workflows/deploy-pages.yml).
- Supports interactive typing, ANSI color test patterns (16 / 256 / 24-bit TrueColor), Unicode box drawing, alternate screen buffer (`htop` preview), and configurable cursor styling.

### 2. Desktop Terminal Emulator (`demo-jvm`)
- **Module**: [`demo-jvm`](demo-jvm)
- A native desktop application running on macOS, Linux, and Windows using Compose Multiplatform for JVM.
- Features a standalone interactive REPL terminal window with real-time ANSI sequence parsing and keyboard navigation.
- Run locally:
  ```bash
  mise run run-jvm
  # or
  ./gradlew :demo-jvm:run
  ```

### 3. Modern Web Application with Vite & TypeScript (`demo-vite`)
- **Directory**: [`demo-vite`](demo-vite)
- Demonstrates how to consume the compiled WebAssembly terminal emulator inside a modern TypeScript single-page application using Vite.
- Includes interactive UI controls for real-time theme switching (Default, Monokai, Nord, Solarized Dark/Light), cursor style selection (`BLOCK`, `BEAM`, `UNDERLINE`), font size scaling, and one-click ANSI stress-testing scripts (color matrices, progress bars, matrix rain, htop alt-buffer).
- Run locally:
  ```bash
  cd demo-vite
  npm install
  npm run dev
  ```

### 4. Zero-Framework Vanilla HTML/JS Integration (`demo-html`)
- **Directory**: [`demo-html`](demo-html)
- Demonstrates how to mount, control, and receive events from the compiled Wasm terminal emulator directly in plain HTML and JavaScript without any frontend framework or bundler.
- Run locally with any static web server:
  ```bash
  npx http-server demo-html -p 8083
  # or
  python3 -m http.server -d demo-html 8083
  ```

### 5. Web Distribution Package (`npm-package`)
- **Directory**: [`npm-package`](npm-package)
- A self-contained npm-ready package (`@JohNan/kmp-terminal`) generated via `./build-npm.sh` that bundles the production WebAssembly executable, JavaScript interop loader (`demo-web.js`), and Compose web resources for integration in web applications.

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
  - `:terminal-core`: Pure Kotlin multiplatform terminal state machine, screen buffer, ANSI/VT100 parser, and text selection models. Zero UI dependencies.
  - `:terminal-ui`: Hardware-accelerated Compose Multiplatform canvas renderer, gesture recognizers, platform-native input capture, selection overlays, and toolbar key bars.
- **Type-Safe Configuration System**:
  - `TerminalConfig` & `terminalConfig { ... }` DSL for core engine dimensions, scrollback capacity, OSC 52 security policies, tab stops, and bell behavior.
  - `TerminalUiConfig` & `terminalUiConfig { ... }` DSL for typography, cursor styles (`BLOCK`, `UNDERLINE`, `BEAM`), gesture controls, key bar layouts, and color themes.
  - Ambient Compose propagation via `LocalTerminalUiConfig`.
- **Reactive State & Attention Events**:
  - `screenState` StateFlow emitting immutable buffer snapshots on change.
  - Reactive event flows: `attentionEvents` (OSC 9 / OSC 777 desktop notifications), `bellEvents` / `bellCount`, `clipboardEvents` / `clipboardWriteCount`, and `windowTitle`.
- **Text Selection & Clipboard Management**:
  - Multi-line text selection overlay with visual start/end handle indicators.
  - Drag-selection gesture tracking, long-press activation, and auto-copy on release.
  - Plain-text extraction with ANSI sequence stripping and scrollback boundary support.
- **ANSI & Modern Terminal Standards**:
  - 16 standard/bright ANSI colors, 256 indexed colors, 24-bit TrueColor RGB.
  - Cursor movement, scrolling regions (DECSTBM), alternate screen buffer (`altBuffer` for vim/htop/less).
  - SGR Mouse Tracking (1006) and legacy xterm mouse tracking (press, release, drag, wheel).
  - OSC 52 clipboard integration with configurable security policies (`ASK`, `ALWAYS_ALLOW`, `ALWAYS_DENY`).
  - Independent deep state cloning (`TerminalEmulator.copy()`) for headless sessions and speculative execution (e.g. Mosh).
  - Soft-wrap row querying (`ScreenBuffer.isRowWrapped(row)`).
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

---

## Project Structure

```
kmp-terminal/
├── terminal-core/       # Pure Kotlin Multiplatform emulation engine & screen buffer (Zero UI dependencies)
├── terminal-ui/         # Compose Multiplatform canvas renderer & UI components
├── demo-web/            # WebAssembly (wasmJs) interactive demo & JS bridge (deployed to GitHub Pages)
├── demo-jvm/            # JVM Desktop Compose demo (macOS, Linux, Windows)
├── demo-vite/           # Vite + TypeScript web application integrating Wasm terminal
├── demo-html/           # Vanilla HTML/JS zero-framework integration demo
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
        onLog = { msg -> println("[TerminalLog] $msg") },
        modifier = Modifier.fillMaxSize()
    )
}
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
