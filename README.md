# KMP Terminal Emulator

[![CI](https://github.com/JohNan/kmp-terminal-emulator/actions/workflows/ci.yml/badge.svg)](https://github.com/JohNan/kmp-terminal-emulator/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.1.0-purple.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.1-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Target-Android-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/Target-iOS-000000.svg?logo=apple&logoColor=white)](https://developer.apple.com/ios/)
[![JVM](https://img.shields.io/badge/Target-JVM_Desktop-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)
[![WebAssembly](https://img.shields.io/badge/Target-WasmJs-654FF0.svg?logo=webassembly&logoColor=white)](https://webassembly.org/)
[![Google Play](https://img.shields.io/badge/Google_Play-Production_App-34A853?logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.johnan.sshclient)

A modern Kotlin Multiplatform (KMP) terminal emulator engine and Compose Multiplatform rendering library. This library provides a standalone, high-performance, GPU-accelerated terminal emulator designed for mobile, desktop, and web applications.

👉 **[🚀 Try Live Interactive WebAssembly Demo](https://johnan.github.io/kmp-terminal-emulator/)**

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
- **Text Selection & Clipboard Management**:
  - Multi-line text selection overlay with visual start/end handle indicators.
  - Drag-selection gesture tracking, long-press activation, and auto-copy on release.
  - Plain-text extraction with ANSI sequence stripping and scrollback boundary support.
- **ANSI & Modern Terminal Standards**:
  - 16 standard/bright ANSI colors, 256 indexed colors, 24-bit TrueColor RGB.
  - Cursor movement, scrolling regions (DECSTBM), alternate screen buffer (`altBuffer` for vim/htop/less).
  - SGR Mouse Tracking (1006) and legacy xterm mouse tracking (press, release, drag, wheel).
  - OSC 52 clipboard integration with configurable security policies (`ASK`, `ALWAYS_ALLOW`, `ALWAYS_DENY`).
- **Mobile & Desktop Responsive Features**:
  - Soft keyboard modifier toggle integration (Ctrl, Alt, Shift states).
  - Monospace cell measurement with sub-pixel precision to eliminate column drift.
  - Low-allocation batching render loop with LRU cache.
- **Multiplatform Target Support**:
  - WebAssembly (`wasmJs`)
  - Desktop (`jvm`)
  - Android (`androidMain`)
  - iOS (`iosArm64`, `iosSimulatorArm64`)

---

## Project Structure

```
kmp-terminal/
├── terminal-core/       # Pure Kotlin Multiplatform emulation engine & screen buffer
├── terminal-ui/         # Compose Multiplatform canvas renderer & UI components
├── demo-web/            # WebAssembly (wasmJs) interactive demo
└── demo-jvm/            # JVM Desktop Compose demo
```

---

## Installation

Add the dependencies to your multiplatform `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core ANSI engine, scrollback buffer, and text selection (zero UI dependencies)
            implementation("io.github.johnan:terminal-core:0.3.0")

            // Optional: Compose Multiplatform canvas renderer & gestures
            implementation("io.github.johnan:terminal-ui:0.3.0")
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
        colorScheme = TerminalColorScheme.SOLARIZED_DARK
        gestures {
            enableTouchToFocus = true
            enableSelection = true
            wheelScrollMultiplier = 1.5f
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

---

## Tooling & Verification

This project uses `mise` for reproducible toolchain management (JDK 21, Android SDK, Gradle).

```bash
# Compile WebAssembly demo
mise run compile-wasm

# Run local WebAssembly demo server
mise run run-demo

# Execute all multiplatform unit tests
mise exec -- ./gradlew allTests
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
