# Migration Guide: Configuration System & Decoupled APIs

This document provides explicit, recipe-driven before-and-after migration instructions designed for both human developers and AI coding agents.

---

## Overview of Architectural Changes

1. **`TerminalConfig`**: Pure Kotlin Multiplatform configuration for `terminal-core` (dimensions, scrollback buffer, OSC 52 policy, tab stops, bell behavior). Zero Compose/UI dependencies.
2. **`TerminalUiConfig`**: Modular Compose Multiplatform UI configuration for `terminal-ui` (`TerminalTypographyConfig`, `TerminalCursorConfig`, `TerminalGestureConfig`, `TerminalKeyBarConfig`, `TerminalRenderOptimizationConfig`, `LocalTerminalUiConfig`).
3. **Cursor Styling**: Added support for `TerminalCursorStyle.BLOCK`, `TerminalCursorStyle.UNDERLINE`, and `TerminalCursorStyle.BEAM`.
4. **Text Selection & Extraction**: Fully built into `terminal-core` (`TerminalSelection`, `SelectionState`) and rendered via `terminal-ui` (`SelectionOverlay`).

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

