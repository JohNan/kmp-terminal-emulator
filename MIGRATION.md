# Migration Guide: Configuration System & Decoupled APIs

This document provides explicit, recipe-driven before-and-after migration instructions designed for both human developers and AI coding agents.

---

## Overview of Architectural Changes

1. **`TerminalConfig`**: Pure Kotlin Multiplatform configuration for `terminal-core` (dimensions, scrollback buffer, OSC 52 policy, tab stops, bell behavior). Zero Compose/UI dependencies.
2. **`TerminalUiConfig`**: Modular Compose Multiplatform UI configuration for `terminal-ui` (`TerminalTypographyConfig`, `TerminalCursorConfig`, `TerminalGestureConfig`, `TerminalKeyBarConfig`, `TerminalRenderOptimizationConfig`, `LocalTerminalUiConfig`).
3. **Cursor Styling**: Added support for `TerminalCursorStyle.BLOCK`, `TerminalCursorStyle.UNDERLINE`, and `TerminalCursorStyle.BEAM`.
4. **Text Selection & Extraction**: Fully built into `terminal-core` (`TerminalSelection`, `SelectionState`) and rendered via `terminal-ui` (`SelectionOverlay`).
5. **Synchronous Core APIs & Native C-ABI / Rust FFI**: Additive synchronous execution APIs in `TerminalEmulator`, zero-allocation grid serialization (`packGridToBuffer`), native C-ABI library exports (`libterminal_core`), and safe Rust bindings (`kmp-terminal-rs`).

---

## Recipes

### Recipe 1: Initializing `TerminalEmulator`

#### Before
```kotlin
import com.johnan.terminal.core.Osc52Policy
import com.johnan.terminal.core.TerminalEmulator

val emulator = TerminalEmulator(
    rows = 24,
    cols = 80,
    osc52Policy = Osc52Policy.ASK
)
```

#### After
```kotlin
import com.johnan.terminal.core.Osc52Policy
import com.johnan.terminal.core.TerminalConfig
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.terminalConfig

val emulator = TerminalEmulator(
    config = terminalConfig {
        initialRows = 24
        initialCols = 80
        maxScrollback = 1000
        osc52Policy = Osc52Policy.ASK
    }
)
```

---

### Recipe 2: `TerminalRenderer` UI Styling & Cursors

#### Before (Ad-hoc constructor parameters)
```kotlin
import androidx.compose.ui.Modifier
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.ui.TerminalRenderer

TerminalRenderer(
    terminalState = terminalState,
    colorScheme = TerminalColorScheme.SOLARIZED_DARK,
    fontSize = 16f,
    onInput = { input -> /* handle */ },
    onArrowKey = { arrow, shift -> /* handle */ },
    onLog = { msg -> /* log */ }
)
```

#### After (Consolidated `TerminalUiConfig`)
```kotlin
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.ui.TerminalCursorStyle
import com.johnan.terminal.ui.TerminalRenderer
import com.johnan.terminal.ui.terminalUiConfig

val uiConfig = terminalUiConfig {
    typography {
        fontSize = 16.sp
    }
    cursor {
        style = TerminalCursorStyle.BEAM // BLOCK, UNDERLINE, or BEAM
        blink = true
    }
    colorScheme = TerminalColorScheme.SOLARIZED_DARK
    gestures {
        enableTouchToFocus = true
        enableSelection = true
        wheelScrollMultiplier = 1.0f
        touchScrollSendsWheelOnly = true // Opt-in wheel-only touch scrolling for tmux/vim
    }

}

TerminalRenderer(
    terminalState = terminalState,
    config = uiConfig,
    onInput = { input -> /* handle */ },
    onArrowKey = { arrow, shift -> /* handle */ },
    onLog = { msg -> /* log */ }
)
```

---

### Recipe 3: Ambient Compose Theming via `LocalTerminalUiConfig`

#### Example
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.johnan.terminal.ui.LocalTerminalUiConfig
import com.johnan.terminal.ui.TerminalRenderer
import com.johnan.terminal.ui.terminalUiConfig

@Composable
fun AppTerminalContainer(emulator: TerminalEmulator) {
    val globalTerminalConfig = terminalUiConfig {
        cursor { style = TerminalCursorStyle.UNDERLINE }
    }

    CompositionLocalProvider(LocalTerminalUiConfig provides globalTerminalConfig) {
        // Child composables automatically inherit globalTerminalConfig
        TerminalRenderer(
            terminalState = emulator.screenState.value,
            onInput = { /* ... */ },
            onArrowKey = { _, _ -> },
            onLog = { /* ... */ }
        )
    }
}
```

---

### Recipe 4: Text Selection & Plain Text Extraction

#### Programmatic Selection
```kotlin
import com.johnan.terminal.core.TerminalSelection

val selection = TerminalSelection(startRow = 0, startCol = 0, endRow = 3, endCol = 80)

// Check if position is within selection
val isInside = selection.contains(row = 1, col = 10)

// Extract text from ScreenBuffer
val selectedText = selection.extractText(screenBuffer)
```

---

### Recipe 5: Mobile Touch Scroll Disambiguation (`tmux`, `vim`, `less`)

When connecting to terminal environments with mouse mode enabled (`set -g mouse on` in `tmux` or `mouse=a` in `vim`), touchscreen drag gestures normally emit mouse button 0 `Press` and `Drag` events. In `tmux`, this triggers the application's internal text selection (showing a yellow selection overlay) and snaps viewport scrollback upon finger release.

To enable smooth, persistent touch scrolling without triggering copy-mode selection, enable `touchScrollSendsWheelOnly`:

#### Example
```kotlin
val uiConfig = terminalUiConfig {
    gestures {
        // Send pure WheelUp / WheelDown mouse events on drag
        // Discrete taps without dragging continue to dispatch Press + Release
        touchScrollSendsWheelOnly = true
    }
}
```



### Recipe 6: Forking emulator state (additive API)

No migration is required; these APIs are purely additive. Replace hand-rolled replays of the full byte history with a copy:

#### Before
```kotlin
val next = TerminalEmulator(config)
next.processOutput(fullHistory + diff) // O(history) per state
```

#### After
```kotlin
val next = reference.copy()   // deep, independent copy, including mid-sequence parser state
next.processOutput(diff)
```

Also new: `TerminalEmulator.bellCount`, `clipboardWriteCount` and `lastClipboardWrite` (updated for every OSC 52 write
request regardless of `Osc52Policy`), and `ScreenBuffer.isRowWrapped(row)`. Fixed: `maxScrollback = 0` no longer
crashes when output scrolls past the bottom or a resize reflows overflowing rows.

---

### Recipe 7: Pure Kotlin `TerminalPalette` & Decoupled `:terminal-core`

`:terminal-core` is now completely decoupled from Compose UI dependencies, adding native compilation targets (`macosArm64`, `macosX64`, `linuxX64`).

- **Compose UI Users**: `TerminalColorScheme` and `TerminalColor.toComposeColor(...)` have moved to `:terminal-ui` inside `package com.johnan.terminal.core`. Since `:terminal-ui` already exposes an API dependency on `:terminal-core`, **existing Compose consumers require zero code or import changes**.
- **Non-UI / Backend / CLI Users**: Use `TerminalPalette` and `TerminalColor.toRgbInt(...)` for pure Kotlin 24-bit RGB packed colors (`0xRRGGBB`):

```kotlin
import com.johnan.terminal.core.TerminalColor
import com.johnan.terminal.core.TerminalPalette

// Pure Kotlin color resolution with packed RGB ints
val palette = TerminalPalette.SOLARIZED_DARK
val fgRgb = TerminalColor.toRgbInt(TerminalColor.Standard(1), palette = palette)
val r = TerminalColor.red(fgRgb)
val g = TerminalColor.green(fgRgb)
val b = TerminalColor.blue(fgRgb)
```

---

### Recipe 8: Using `:terminal-tui` for Terminal Applications & CLI Tools

The new `:terminal-tui` module provides a low-allocation differential ANSI renderer and VT100 / SGR input parser for building command-line TUI applications without Compose:

```kotlin
import com.johnan.terminal.tui.TuiAnsiRenderer
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

    // Draw UI components with rounded boxes and text
    surface.drawBox(row = 0, col = 0, height = size.rows, width = size.cols, title = "My TUI", borderFgRgb = 0x81A1C1, bgRgb = 0x2E3440)
    surface.drawText(row = 2, col = 4, text = "Press Q to exit", fgRgb = 0xECEFF4, bgRgb = 0x2E3440)

    // Render frame atomically with minimal ANSI diff
    io.writeStdout(renderer.render(surface))
} finally {
    io.exitRawTuiMode()
}
```

---

### Recipe 9: Synchronous Core API, Bulk Grid Packing & Native C-ABI / Rust FFI

`:terminal-core` introduces synchronous execution methods and a zero-allocation bulk grid serializer alongside native C-ABI library exports (`libterminal_core.so` / `libterminal_core.dylib` / `libterminal_core.a`). This enables non-JVM, single-threaded, and high-performance native contexts (such as Rust, C, and C++) to leverage the terminal engine.

> [!NOTE]
> All existing coroutine `suspend` methods (`processOutput`, `resize`, `clear`, `reset`, `search`) and reactive `StateFlow` / `SharedFlow` properties remain **100% unchanged and fully supported**. No migration is required for existing Compose Multiplatform or coroutine-based consumers.

#### Synchronous Core APIs (Kotlin)

When operating in environments where coroutines or dispatcher context switching are undesirable, use the synchronous companion methods:

```kotlin
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.TerminalPalette
import com.johnan.terminal.core.terminalConfig

val emulator = TerminalEmulator(
    config = terminalConfig {
        initialRows = 24
        initialCols = 80
        maxScrollback = 1000
    }
)

// Synchronous non-suspending ANSI byte processing
emulator.processOutputSync("\u001B[32mSuccess:\u001B[0m Operation completed.\r\n")

// Zero-allocation grid serialization into pre-allocated IntArray (4 Ints per cell: codepoint, fg, bg, bitmask)
val buffer = IntArray(24 * 80 * 4)
val cellsWritten = emulator.packGridToBuffer(
    outBuffer = buffer,
    palette = TerminalPalette.DEFAULT,
    isDark = true
)
```

#### Safe Rust FFI Usage (`kmp-terminal-rs` / `demo-rust`)

Rust consumers can use the `kmp-terminal-rs` crate, which provides safe RAII bindings over the Kotlin/Native C-ABI:

```rust
use kmp_terminal_rs::{PackedCell, Palette, Terminal};

fn main() {
    // RAII initialization via terminal_create
    let mut term = Terminal::new(24, 80);

    // Synchronous ANSI parsing via terminal_process_bytes
    term.process_str("\x1b[1;36mKMP Terminal Native Bridge\x1b[0m\r\n");

    // High-throughput zero-allocation bulk grid reading into pre-allocated slice
    let total = (term.rows() * term.cols()) as usize;
    let mut cells = vec![
        PackedCell {
            codepoint: ' ' as i32,
            fg_rgb: 0,
            bg_rgb: -1,
            attributes: 0,
        };
        total
    ];
    let written = term.read_grid_into(&mut cells, Palette::Default);

    println!(
        "Grid initialized at {}x{} with {} cells populated",
        term.rows(),
        term.cols(),
        written
    );
}
```

---

### Recipe 10: Kitty Graphics Protocol & Unicode Placeholders

`:terminal-core` and `:terminal-ui` provide comprehensive support for the Kitty Graphics Protocol, enabling inline terminal images with hardware-accelerated canvas rendering across JVM, Android, iOS, and WasmJS.

Key capabilities include:
- **Formats & Compression**: Direct RGBA (`f=32`), RGB (`f=24`), and raw PNG (`f=100`) with RFC 1950/1951 Deflate/zlib decompression (`o=z`).
- **Control Actions**: Transmit (`a=t`), Transmit & Display (`a=T`), Place (`a=p`), Delete (`a=d`), and Query (`a=q`).
- **Multiplexer Compatibility**: Unicode Placeholders (`U=1`, `U+10EEEE`) with combining diacritics table encoding for smooth reflow inside `tmux` and `zellij`.
- **Memory Safety**: LRU memory eviction constrained by `maxImageMemoryBytes` with decompression bomb safeguards.

#### Configuring Terminal Emulator

Enable graphics support and configure cache limits in `terminalConfig`:

```kotlin
val emulator = TerminalEmulator(
    config = terminalConfig {
        initialRows = 24
        initialCols = 80
        kittyGraphicsEnabled = true // Enabled by default
        maxImageMemoryBytes = 32 * 1024 * 1024 // 32MB LRU image cache
    }
)
```

#### Wiring Pixel Geometry in Compose Multiplatform

Connect `TerminalRenderer`'s `onCellMeasured` callback to `emulator.setCellDimensionsPx` so the emulator accurately maps image pixel dimensions to grid cells:

```kotlin
TerminalRenderer(
    emulator = emulator,
    onCellMeasured = { widthPx, heightPx ->
        emulator.setCellDimensionsPx(widthPx.toInt(), heightPx.toInt())
    }
)
```



