@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.johnan.terminal.core.ffi

import com.johnan.terminal.core.Osc52Policy
import com.johnan.terminal.core.TerminalConfig
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.TerminalPalette
import com.johnan.terminal.core.TerminalSelection
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.set
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

@CName("terminal_create")
fun terminalCreate(
    rows: Int,
    cols: Int,
    maxScrollback: Int,
    osc52PolicyOrdinal: Int,
): COpaquePointer {
    val safeRows = rows.coerceAtLeast(1)
    val safeCols = cols.coerceAtLeast(1)
    val safeMaxScrollback = maxScrollback.coerceAtLeast(0)
    val policy = when (osc52PolicyOrdinal) {
        0 -> Osc52Policy.ASK
        1 -> Osc52Policy.ALWAYS_ALLOW
        2 -> Osc52Policy.ALWAYS_DENY
        else -> Osc52Policy.ASK
    }
    val config = TerminalConfig(
        initialRows = safeRows,
        initialCols = safeCols,
        maxScrollback = safeMaxScrollback,
        osc52Policy = policy,
    )
    val emulator = TerminalEmulator(config)
    return StableRef.create(emulator).asCPointer()
}

@CName("terminal_copy")
fun terminalCopy(handle: COpaquePointer): COpaquePointer {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    val clone = emulator.copy()
    return StableRef.create(clone).asCPointer()
}

@CName("terminal_destroy")
fun terminalDestroy(handle: COpaquePointer) {
    handle.asStableRef<TerminalEmulator>().dispose()
}

@CName("terminal_process_bytes")
fun terminalProcessBytes(
    handle: COpaquePointer,
    bytes: CPointer<ByteVar>?,
    len: Int,
) {
    if (bytes == null || len <= 0) return
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    val data = bytes.readBytes(len)
    emulator.processOutputSync(data)
}

@CName("terminal_resize")
fun terminalResize(
    handle: COpaquePointer,
    rows: Int,
    cols: Int,
): Int {
    if (rows <= 0 || cols <= 0) return 0
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return if (emulator.resizeSync(rows, cols)) 1 else 0
}

@CName("terminal_clear")
fun terminalClear(handle: COpaquePointer) {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    emulator.clearSync()
}

@CName("terminal_reset")
fun terminalReset(handle: COpaquePointer) {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    emulator.resetSync()
}

@CName("terminal_get_rows")
fun terminalGetRows(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return emulator.getScreenBuffer().rows
}

@CName("terminal_get_cols")
fun terminalGetCols(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return emulator.getScreenBuffer().cols
}

@CName("terminal_get_cursor_row")
fun terminalGetCursorRow(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return emulator.getScreenBuffer().cursorRow
}

@CName("terminal_get_cursor_col")
fun terminalGetCursorCol(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return emulator.getScreenBuffer().cursorCol
}

@CName("terminal_is_cursor_visible")
fun terminalIsCursorVisible(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return if (emulator.getScreenBuffer().cursorVisible) 1 else 0
}

@CName("terminal_get_bell_count")
fun terminalGetBellCount(handle: COpaquePointer): Long {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return emulator.bellCount
}

@CName("terminal_get_clipboard_write_count")
fun terminalGetClipboardWriteCount(handle: COpaquePointer): Long {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return emulator.clipboardWriteCount
}

@CName("terminal_get_scrollback_size")
fun terminalGetScrollbackSize(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    return emulator.getScreenBuffer().getScrollback().size
}

@CName("terminal_read_grid")
fun terminalReadGrid(
    handle: COpaquePointer,
    outBuffer: CPointer<IntVar>?,
    maxCells: Int,
    paletteOrdinal: Int,
): Int {
    if (outBuffer == null || maxCells <= 0) return 0
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    val palette = when (paletteOrdinal) {
        0 -> TerminalPalette.DEFAULT
        1 -> TerminalPalette.SOLARIZED_DARK
        2 -> TerminalPalette.SOLARIZED_LIGHT
        3 -> TerminalPalette.MONOKAI
        4 -> TerminalPalette.NORD
        else -> TerminalPalette.DEFAULT
    }
    val isDark = paletteOrdinal != 2
    return emulator.packGridCells(maxCells, palette, isDark) { outOffset, codepoint, fgRgb, bgRgb, attrs ->
        outBuffer[outOffset] = codepoint
        outBuffer[outOffset + 1] = fgRgb
        outBuffer[outOffset + 2] = bgRgb
        outBuffer[outOffset + 3] = attrs
    }
}

private fun writeUtf8ToBuffer(
    text: String,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val bytes = text.encodeToByteArray()
    if (outUtf8 != null && capacity > 0) {
        val count = bytes.size.coerceAtMost(capacity)
        for (i in 0 until count) {
            outUtf8[i] = bytes[i]
        }
    }
    return bytes.size
}

@CName("terminal_extract_selection")
fun terminalExtractSelection(
    handle: COpaquePointer,
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    val text = TerminalSelection(startRow, startCol, endRow, endCol).extractText(emulator.getScreenBuffer())
    return writeUtf8ToBuffer(text, outUtf8, capacity)
}

@CName("terminal_get_window_title")
fun terminalGetWindowTitle(
    handle: COpaquePointer,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    val title = emulator.windowTitle.value ?: return -1
    return writeUtf8ToBuffer(title, outUtf8, capacity)
}

@CName("terminal_get_last_clipboard_write")
fun terminalGetLastClipboardWrite(
    handle: COpaquePointer,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<TerminalEmulator>().get()
    val text = emulator.lastClipboardWrite ?: return -1
    return writeUtf8ToBuffer(text, outUtf8, capacity)
}
