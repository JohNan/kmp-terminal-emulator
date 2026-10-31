# Migration Guide: Configuration System & Decoupled APIs

This document provides explicit, recipe-driven before-and-after migration instructions designed for both human developers and AI coding agents.

---

## Overview of Architectural Changes

1. **`TerminalConfig`**: Pure Kotlin Multiplatform configuration for `terminal-core` (dimensions, scrollback buffer, OSC 52 policy, tab stops, bell behavior). Zero Compose/UI dependencies.
2. **`TerminalUiConfig`**: Modular Compose Multiplatform UI configuration for `terminal-ui` (`TerminalTypographyConfig`, `TerminalCursorConfig`, `TerminalGestureConfig`, `TerminalKeyBarConfig`, `TerminalRenderOptimizationConfig`, `LocalTerminalUiConfig`).
3. **Cursor Styling**: Added support for `TerminalCursorStyle.BLOCK`, `TerminalCursorStyle.UNDERLINE`, and `TerminalCursorStyle.BEAM`.
4. **Text Selection & Extraction**: Fully built into `terminal-core` (`TerminalSelection`, `SelectionState`) and rendered via `terminal-ui` (`SelectionOverlay`).
5. **Synchronous Core APIs & Native C-ABI / Rust FFI**: Additive synchronous execution APIs in `TerminalEmulator`, zero-allocation grid serialization (`packGridToBuffer`), native C-ABI library exports (`libterminal_core`), and safe Rust bindings (`kmp-terminal-rs`).
6. **Modern DEC Modes & Terminal Identification**: Added DEC Mode 2026 (Synchronized Output), DEC Mode 1004 (Focus Reporting), DECSCUSR cursor shapes (`CursorShape.BLOCK`, `CursorShape.UNDERLINE`, `CursorShape.BEAM`), DECRQM mode query responses, Secondary Device Attributes (DA2), and XTVERSION querying.
7. **Extended SGR Underlines & Colors**: Added ITU-T T.416 sub-parameter styled underlines (`UnderlineStyle.NONE`, `SINGLE`, `DOUBLE`, `CURLY`, `DOTTED`, `DASHED`) via SGR 4:x / 21, and TrueColor/indexed underline coloring via SGR 58:2 / 58:5 and SGR 59.
8. **Semantic Shell Integration & Explicit Hyperlinks**: Added OSC 8 explicit hyperlink support (`TerminalCell.hyperlinkUrl`, `UrlRange.url`), OSC 7 and OSC 9;9 Current Working Directory tracking (`TerminalEmulator.currentWorkingDirectory`), and OSC 133 semantic prompt mark integration (`PromptMark`, `PromptMarkType`, `lastExitCode`, `isCommandRunning`, `findPreviousPromptLine`, `findNextPromptLine`, `extractLastCommandOutput`).
9. **Kitty Progressive Keyboard Protocol**: Added progressive keyboard enhancement protocol support in `terminal-core` (`KittyKeyboardProtocol`, `KeyEventType`, `kittyKeyboardFlags`, `encodeKeyEvent`) and extended `:terminal-tui` (`TuiInputParser`, `TuiEvent.Key.eventType`, `toAnsiSequence(..., kittyKeyboardFlags)`).
10. **Dynamic Palette & Color Overrides (OSC 4/10/11/12/104/110/111/112)** & **DCS Capability Queries (XTGETTCAP / DECRQSS)**: Added dynamic RGB color queries/overrides for foreground, background, cursor, and 256-color palette indices, alongside terminfo capability retrieval (`DCS + q`) and status string queries (`DCS $ q`).
11. **`:terminal-ui` Protocol Visual Binding & Enhanced Keyboard Handling**: Added `showPromptMarks` to `TerminalRenderOptimizationConfig`, dynamic DECSCUSR cursor shape rendering (`CursorShape.BLOCK`, `UNDERLINE`, `BEAM`), OSC 10/11/12 dynamic canvas background and cursor color binding, and `kittyKeyboardFlags` parameter to `applyModifierToChar` and `handleHardwareKeyEvent`.
12. **`:terminal-tui` Protocol Parity & Full C-ABI / Rust FFI Bindings**: Extended `:terminal-tui` with `UnderlineStyle` (SGR `4:x`), 24-bit underline RGB (SGR `58;2;r;g;b`), `OVERLINE` (SGR `53`), and `OSC 8` explicit hyperlinks in `TuiSurface` and `TuiAnsiRenderer`. Expanded native C-ABI exports and safe Rust crate (`kmp-terminal-rs`) to full protocol parity (`CursorShape`, `UnderlineStyle`, `drain_response`, `kitty_keyboard_flags`, `encode_key_event`, `working_directory`, `last_exit_code`, `is_command_running`, `extract_last_command_output`).

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

---

### Recipe 11: Extended Underlines (SGR 4:x / 21) & TrueColor Underline Colors (SGR 58:2)

Modern terminals support styled underlines (curly, dotted, dashed, double) and distinct underline colors. `:terminal-core` and `:terminal-ui` parse and render these natively with zero allocation during draw loops.

#### Underline Styles
- `\u001B[4m` or `\u001B[4:1m`: Single underline (`UnderlineStyle.SINGLE`)
- `\u001B[21m` or `\u001B[4:2m`: Double underline (`UnderlineStyle.DOUBLE`)
- `\u001B[4:3m`: Curly underline (`UnderlineStyle.CURLY`)
- `\u001B[4:4m`: Dotted underline (`UnderlineStyle.DOTTED`)
- `\u001B[4:5m`: Dashed underline (`UnderlineStyle.DASHED`)
- `\u001B[24m` or `\u001B[4:0m`: Underline off (`UnderlineStyle.NONE`)

#### Underline Coloring
- `\u001B[58;2;r;g;bm` or `\u001B[58:2::r:g:bm`: 24-bit TrueColor underline color (`TerminalColor.Rgb(r, g, b)`)
- `\u001B[58;5;cm` or `\u001B[58:5:cm`: 256-color indexed underline color (`TerminalColor.Standard(c)`)
- `\u001B[59m`: Reset underline color to default foreground (`TerminalColor.Default`)

#### Example
```kotlin
// Emitting a curly red underline under warning text
emulator.processOutput("\u001B[4:3m\u001B[58:2::255:0:0mSyntax Error\u001B[59m\u001B[4:0m")
```

---

### Recipe 12: DEC Mode 2026 (Synchronized Output) & Mode 1004 (Focus Reporting)

#### Synchronized Output (DEC 2026)
Synchronized output allows TUI applications to batch screen updates atomically, eliminating tearing and intermediate renders.

- `\u001B[?2026h`: Begin synchronized update (`terminalState.synchronizedOutputEnabled == true`)
- `\u001B[?2026l`: End synchronized update (`terminalState.synchronizedOutputEnabled == false`)

When active, `screenState` updates can be paused or buffered until the matching `\u001B[?2026l` is received.

#### Focus Reporting (DEC 1004)
When enabled by full-screen terminal applications (e.g. `vim`, `helix`, `tmux`), the terminal reports window focus events automatically:

- `\u001B[?1004h`: Enable focus reporting (`terminalState.focusReportingEnabled == true`)
- `\u001B[?1004l`: Disable focus reporting (`terminalState.focusReportingEnabled == false`)
- Terminal receives focus: emits `\u001B[I` to `onInput`
- Terminal loses focus: emits `\u001B[O` to `onInput`

---

### Recipe 13: DECSCUSR Dynamic Cursor Shapes & Terminal Query Responses

#### Cursor Shape Control (DECSCUSR)
Applications can dynamically change the active cursor shape via `\u001B[<Ps> q`:
- `\u001B[0 q` or `\u001B[1 q`: Blinking block (`CursorShape.BLOCK`)
- `\u001B[2 q`: Steady block (`CursorShape.BLOCK`)
- `\u001B[3 q`: Blinking underline (`CursorShape.UNDERLINE`)
- `\u001B[4 q`: Steady underline (`CursorShape.UNDERLINE`)
- `\u001B[5 q`: Blinking beam / bar (`CursorShape.BEAM`)
- `\u001B[6 q`: Steady beam / bar (`CursorShape.BEAM`)

The current shape is tracked in `terminalState.cursorShape`.

#### Terminal Queries & Identification
The terminal automatically responds to identification and capability queries through the `onTerminalResponse` callback:
- **DA1 (Primary Device Attributes)**: `\u001B[c` &rarr; responds `\u001B[?1;2c` (VT100 with Advanced Video Option)
- **DA2 (Secondary Device Attributes)**: `\u001B[>c` &rarr; responds `\u001B[>0;10;0c` (VT100 family emulator)
- **XTVERSION**: `\u001B[>0q` &rarr; responds `\u001BP>|kmp-terminal 1.0\u001B\`
- **DECRQM (DEC Request Mode)**: `\u001B[?<mode>$p` &rarr; responds `\u001B[?<mode>;<status>$y` (e.g. status 1 for set, 2 for reset)

---

### Recipe 14: OSC 8 Explicit Hyperlinks & URL Detection

#### Before (Plain-text regex only, manual text slicing)
Consumer applications had to inspect terminal rows using regex pattern matching on plain text, without metadata support for explicit HTML-style hyperlinks.
```kotlin
val row = emulator.screenState.value.rows[cursorRow]
val text = row.map { it.char }.joinToString("")
val matches = "(https?://\\S+)".toRegex().findAll(text)
```

#### After (OSC 8 Hyperlinks & Unified URL Ranges)
Explicit hyperlinks emitted via `\e]8;;<url>\e\Click Here\e]8;;\e\` are attached directly to `TerminalCell.hyperlinkUrl`. `ScreenBuffer.getUrlRanges(row)` returns both explicit OSC 8 hyperlinks and plain-text URLs, prioritizing explicit hyperlinks and disambiguating with `UrlRange.url`:
```kotlin
val row = emulator.screenState.value.rows[cursorRow]

// Direct cell query
val cellHyperlink = row[col].hyperlinkUrl // String? containing explicit OSC 8 target

// Unified URL range detection on row
val ranges: List<UrlRange> = ScreenBuffer.getUrlRanges(row)
for (range in ranges) {
    val url = ScreenBuffer.extractUrl(row, range)
    if (range.url != null) {
        println("Explicit OSC 8 link: $url (cols ${range.startCol}..${range.endCol})")
    } else {
        println("Plain-text detected URL: $url (cols ${range.startCol}..${range.endCol})")
    }
}
```

---

### Recipe 15: OSC 7 & OSC 9;9 Current Working Directory Tracking

#### Before (No shell working directory awareness)
The emulator did not parse shell directory notification escape sequences; consumer applications had to inspect PTY processes externally.

#### After (Reactive CWD StateFlow via OSC 7 and OSC 9;9)
The emulator listens for standard OSC 7 directory reports (`\e]7;file://hostname/path\a`) with RFC 3986 percent-decoding (including UTF-8 multibyte characters) and ConEmu / Windows Terminal OSC 9;9 reports (`\e]9;9;"path"\a`), publishing updates to `TerminalEmulator.currentWorkingDirectory` and `TerminalScreenState.currentWorkingDirectory` without raising desktop notification events:
```kotlin
// In Jetpack Compose / Compose Multiplatform UI:
val currentWorkingDir by emulator.currentWorkingDirectory.collectAsState()
Text(text = "Current Dir: ${currentWorkingDir ?: "Unknown"}")

// Direct synchronous access:
val cwd = emulator.currentWorkingDirectory.value
// Or from immutable screen state:
val screenCwd = emulator.screenState.value.currentWorkingDirectory
```

---

### Recipe 16: OSC 133 Semantic Shell Integration (Prompts, Exit Codes, Output Extraction)

#### Before (Unstructured terminal stream)
Shell output was flat text with no distinction between prompt strings, user commands, and command output.

#### After (FinalTerm / FTCS Semantic Shell Marks)
Shells configured with OSC 133 emit semantic boundaries (`A` prompt start, `B` command start, `C` output start, `D` command finished with exit code). `TerminalEmulator` captures these events into `PromptMark` records, manages execution state flows, enables command-to-command jumping, and isolates command output:
```kotlin
// Reactive command execution status and exit code
val isRunning by emulator.isCommandRunning.collectAsState()
val lastExitCode by emulator.lastExitCode.collectAsState()

// Jump between prompt lines in scrollback and visible screen
val prevPromptLine = emulator.findPreviousPromptLine()
val nextPromptLine = emulator.findNextPromptLine()

// Cleanly isolate command output between output start (C) and finished (D)
val commandOutput = emulator.extractLastCommandOutput()
println("Last command output:\n$commandOutput")

// Inspect all recorded prompt marks
val marks: List<PromptMark> = emulator.getPromptMarks()
marks.forEach { mark ->
    when (mark.type) {
        PromptMarkType.PROMPT_START -> println("Prompt at line ${mark.absLine}")
        PromptMarkType.COMMAND_START -> println("Command at line ${mark.absLine}")
        PromptMarkType.OUTPUT_START -> println("Output at line ${mark.absLine}")
        PromptMarkType.COMMAND_FINISHED -> println("Exit code ${mark.exitCode} at line ${mark.absLine}")
    }
}
```

---

### Recipe 17: Kitty Progressive Keyboard Protocol Integration

#### Before (Ambiguous legacy VT keys and lack of event types)
Applications received legacy ANSI escape codes where modifier states (Shift, Alt, Ctrl) were ambiguous or caused collisions (e.g. `Ctrl+I` was indistinguishable from `Tab`, `Ctrl+[` was indistinguishable from `Escape`), and key repeat or key release events could not be reported.

```kotlin
// Legacy keyboard input handling without Kitty protocol support
val inputBytes = byteArrayOf(0x1B) // Ambiguous: could be standalone Escape or beginning of escape sequence
val events = parser.parse(inputBytes)
val rawSeq = parser.toAnsiSequence(TuiEvent.Key(TuiKey.ESCAPE)) // "\u001b"
```

#### After (Kitty Progressive Keyboard Protocol)
Full support for Kitty keyboard negotiation sequences:
- `CSI > <flags> u`: Push current flags to the 16-deep active screen stack and apply new flags.
- `CSI < <count> u`: Pop `<count>` flags from the active screen stack.
- `CSI = <flags> ; <mode> u`: Set flags (`mode = 1` replace, `mode = 2` bitwise OR, `mode = 3` bitwise AND NOT).
- `CSI ? u`: Query active flags (terminal responds with `\u001B[?<flags>u`).

Consumer applications and TUI frontends can inspect `kittyKeyboardFlags`, encode key events, and parse `CSI u` sequences with modifiers and event types (`PRESS`, `REPEAT`, `RELEASE`):

```kotlin
import com.johnan.terminal.core.KeyEventType
import com.johnan.terminal.core.KittyKeyboardProtocol
import com.johnan.terminal.tui.TuiEvent
import com.johnan.terminal.tui.TuiInputParser
import com.johnan.terminal.tui.TuiKey

// 1. Inspect current keyboard enhancement flags from emulator or screen state
val flags = emulator.kittyKeyboardFlags
val isDisambiguateActive = (flags and KittyKeyboardProtocol.DISAMBIGUATE_ESCAPE_CODES) != 0

// 2. High-fidelity key event encoding via core emulator
val escapeCode = emulator.encodeKeyEvent(codepoint = 27) // "\u001B[27u" when DISAMBIGUATE_ESCAPE_CODES is set
val releaseEvent = emulator.encodeKeyEvent(
    codepoint = 'a'.code,
    eventType = KeyEventType.RELEASE,
) // "\u001B[97;1:3u" when REPORT_EVENT_TYPES is set

// 3. Differential TUI input parsing & ANSI serialization
val parser = TuiInputParser()
val parsed = parser.parse("\u001b[97;5:2u".encodeToByteArray())
// -> TuiEvent.Key(key = TuiKey.CHAR, char = 'a', ctrl = true, eventType = 2)

val ansiOutput = parser.toAnsiSequence(
    event = TuiEvent.Key(key = TuiKey.ESCAPE),
    kittyKeyboardFlags = flags,
)
```

---

### Recipe 18: Dynamic Color Queries, Overrides (OSC 4/10/11/12) & DCS Capability Negotiation (XTGETTCAP / DECRQSS)

Modern terminal applications (`tmux`, `neovim`, and modern CLI utilities) query and dynamically adjust terminal color palettes and capability features at runtime.

#### Querying and Setting Colors Dynamically
- **Foreground / Background / Cursor Overrides**:
  - `\u001B]10;rgb:12/34/56\u0007` or `\u001B]10;#123456\u0007`: sets dynamic foreground
  - `\u001B]11;#1e1e1e\u001B\\`: sets dynamic background
  - `\u001B]12;#e0e0e0\u0007`: sets dynamic cursor color
  - Chained: `\u001B]10;#111111;#222222;#333333\u0007`: sets foreground, background, and cursor simultaneously
- **Dynamic Color Resets**:
  - `\u001B]110\u0007`: resets dynamic foreground to palette default
  - `\u001B]111\u0007`: resets dynamic background to palette default
  - `\u001B]112\u0007`: resets dynamic cursor to palette default
  - `\u001B]104;1\u0007`: resets palette entry 1; `\u001B]104\u0007`: resets all overridden palette entries
- **Color Queries**:
  - `\u001B]10;?\u001B\\` &rarr; responds `\u001B]10;rgb:rrrr/gggg/bbbb\u001B\`
  - `\u001B]4;1;?\u001B\\` &rarr; responds `\u001B]4;1;rgb:rrrr/gggg/bbbb\u001B\`

#### DCS Capability Negotiation & Status Reporting
- **XTGETTCAP (`DCS + q <hex-caps> ST`)**:
  - `\u001BP+q544E;524742\u001B\\` queries `TN` and `RGB`
  - Responds with `\u001BP1+r544E=787465726D2D323536636F6C6F72\u001B\` and `\u001BP1+r524742\u001B\`
  - Unknown capabilities respond with `\u001BP0+r\u001B\`
- **DECRQSS (`DCS $ q <Pt> ST`)**:
  - `\u001BP$qm\u001B\\` queries SGR graphics rendition &rarr; responds `\u001BP1$r0m\u001B\`
  - `\u001BP$qr\u001B\\` queries 1-indexed scroll margins &rarr; responds `\u001BP1$r<top>;<bottom>r\u001B\`
  - `\u001BP$q q\u001B\\` queries DECSCUSR cursor shape &rarr; responds `\u001BP1$r<Ps> q\u001B\`
  - Unknown queries respond with `\u001BP0$r\u001B\`

#### Accessing Dynamic Color State
```kotlin
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.TerminalPalette

val emulator = TerminalEmulator()

val fg = emulator.dynamicForegroundRgb // null if using default palette
val bg = emulator.dynamicBackgroundRgb
val cursor = emulator.dynamicCursorRgb
val pal1 = emulator.getDynamicPaletteColor(1)

// Effective colors resolving overrides or falling back to palette:
val effectiveFg = emulator.getEffectiveForegroundRgb()
val effectiveBg = emulator.getEffectiveBackgroundRgb()
val effectiveCursor = emulator.getEffectiveCursorRgb()
val effectivePal1 = emulator.getEffectivePaletteRgb(1)
```

---

### Recipe 19: `:terminal-ui` Protocol Visual Binding & Enhanced Keyboard Handling

Consumers of `:terminal-ui` now benefit from automatic visual synchronization with core protocol states (DECSCUSR cursor shape, OSC 10/11/12 dynamic background/cursor colors, and OSC 133 semantic prompt gutter marks), alongside Kitty Progressive Keyboard Protocol hardware and soft-keyboard event emission.

#### Visual Synchronization in `TerminalRenderer`
- **Dynamic Cursor Shapes (DECSCUSR)**: `TerminalRenderer` automatically maps `terminalState.cursorShape` (`CursorShape.BLOCK`, `UNDERLINE`, `BEAM`) to the rendered cursor. If `config.cursor.style` is explicitly set to a non-default style, it serves as the user preference or fallback.
- **Dynamic Colors (OSC 10/11/12)**: `TerminalRenderer` applies `terminalState.dynamicBackgroundRgb` and `terminalState.dynamicCursorRgb` to canvas drawing passes.
- **Semantic Prompt Gutter Marks (OSC 133)**: Renders a 3dp left-gutter status bar next to prompt and command execution blocks:
  - Green for successfully executed commands (`exitCode == 0`).
  - Red for failed commands (`exitCode != 0`).
  - Cyan for running commands or active input prompts.

#### Before (`TerminalUiConfig` without semantic prompt marks)
```kotlin
val uiConfig = terminalUiConfig {
    render {
        cacheRowBatchCapacity = 300
    }
}
```

#### After (Enabling or disabling semantic prompt marks)
```kotlin
val uiConfig = terminalUiConfig {
    render {
        cacheRowBatchCapacity = 300
        showPromptMarks = true // Defaults to true; set to false to disable left-gutter indicators
    }
}
```

#### Before (Keyboard handling without Kitty Keyboard Protocol)
```kotlin
// Soft-key bar / modifier character transformation
val modifiedText = applyModifierToChar('a', modifierState)

// Hardware key event dispatch
handleHardwareKeyEvent(
    keyEvent = event,
    onInput = { input -> sendToPty(input) },
    onArrowKey = { arrow, shift -> sendArrow(arrow, shift) },
)
```

#### After (Passing `kittyKeyboardFlags` from active terminal state)
```kotlin
val activeFlags = terminalState.kittyKeyboardFlags

// Soft-key bar character transformation with active Kitty flags
val modifiedText = applyModifierToChar(
    char = 'a',
    modifierState = modifierState,
    kittyKeyboardFlags = activeFlags,
) // Emits "\u001B[97;5u" when DISAMBIGUATE_ESCAPE_CODES is active

// Hardware key event dispatch with active Kitty flags
handleHardwareKeyEvent(
    keyEvent = event,
    onInput = { input -> sendToPty(input) },
    onArrowKey = { arrow, shift -> sendArrow(arrow, shift) },
    kittyKeyboardFlags = activeFlags,
)
```

---

### Recipe 20: `:terminal-tui` Protocol Parity & Full C-ABI / Rust FFI Bindings

#### 1. `:terminal-tui` Extended Underlines, Colors, Overline & Hyperlinks

##### Before
```kotlin
import com.johnan.terminal.tui.TuiAnsiRenderer
import com.johnan.terminal.tui.TuiCellStyle
import com.johnan.terminal.tui.TuiSurface

val surface = TuiSurface(rows = 10, cols = 40)
// Basic underline only, no overline, underline color, or OSC 8 hyperlinks
surface.setCell(0, 0, 'X', 0xFFFFFF, 0x000000, TuiCellStyle.UNDERLINE)
```

##### After
```kotlin
import com.johnan.terminal.core.UnderlineStyle
import com.johnan.terminal.tui.TuiAnsiRenderer
import com.johnan.terminal.tui.TuiCellStyle
import com.johnan.terminal.tui.TuiSurface

val surface = TuiSurface(rows = 10, cols = 40)

// Combine styling attributes with styled underlines and overline
val style = TuiCellStyle.withUnderlineStyle(
    TuiCellStyle.BOLD or TuiCellStyle.OVERLINE,
    UnderlineStyle.CURLY, // NONE, SINGLE, DOUBLE, CURLY, DOTTED, DASHED
)

// Set cell with TrueColor underline (SGR 58;2;r;g;b) and OSC 8 hyperlink URL
surface.setCell(
    row = 0,
    col = 0,
    char = 'X',
    fgRgb = 0xE2E8F0,
    bgRgb = 0x090D16,
    style = style,
    underlineRgb = 0xFF5050, // -1 for default underline color
    hyperlinkUrl = "https://example.com",
)

// Differential renderer automatically emits:
// - SGR 4:3 for curly underline
// - SGR 53 for overline
// - SGR 58;2;255;80;80 for underline RGB
// - OSC 8 hyperlink escape sequences: \e]8;;https://example.com\e\ ... \e]8;;\e\
val renderer = TuiAnsiRenderer()
val ansiString = renderer.render(surface)
```

#### 2. Native C-ABI & Rust FFI Protocol Parity (`kmp-terminal-rs`)

##### Before
```rust
use kmp_terminal_rs::{PackedCell, Palette, Terminal};

let mut term = Terminal::new(24, 80);
term.process_str("hello\r\n");
```

##### After
```rust
use kmp_terminal_rs::{CursorShape, PackedCell, Palette, Terminal, UnderlineStyle};

let mut term = Terminal::new(24, 80);

// Synchronously feed ANSI stream with FTCS semantic prompts and OSC 7 CWD
term.process_str("\x1b]133;A\x1b\\\x1b]7;file://localhost/tmp\x1b\\$ \x1b]133;B\x1b\\ls\r\n\x1b]133;C\x1b\\file.txt\r\n\x1b]133;D;0\x1b\\");

// Inspect semantic shell state
let cwd = term.working_directory(); // Option<String>
let running = term.is_command_running(); // bool
let exit_code = term.last_exit_code(); // Option<i32>
let output = term.extract_last_command_output(); // Option<String>

// Drain terminal responses (CPR cursor position, DA1 device attributes, etc.)
let mut resp_buf = [0u8; 512];
if let Some(resp) = term.drain_response(&mut resp_buf) {
    println!("Response: {:?}", String::from_utf8_lossy(resp));
}

// Query active DECSCUSR cursor shape and Kitty Progressive Keyboard flags
let shape: CursorShape = term.cursor_shape(); // CursorShape::Block, Underline, Beam
let kitty_flags: u32 = term.kitty_keyboard_flags();

// Encode key event via Kitty progressive keyboard protocol
let encoded: Option<String> = term.encode_key_event(b'x' as i32, 0, 1, 0, None);

// Read cell grid with styled underline and overline support
let mut cells = vec![PackedCell::default(); (term.rows() * term.cols()) as usize];
let count = term.read_grid_into(&mut cells, Palette::Default);

for cell in &cells[..count] {
    let ul_style: UnderlineStyle = cell.underline_style();
    let is_overline: bool = cell.attrs().overline();
    let is_hyperlink: bool = cell.attrs().hyperlink();
}
```






