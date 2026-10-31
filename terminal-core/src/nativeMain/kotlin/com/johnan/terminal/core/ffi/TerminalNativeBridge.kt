@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.johnan.terminal.core.ffi

import com.johnan.terminal.core.CursorShape
import com.johnan.terminal.core.KeyEventType
import com.johnan.terminal.core.MouseTrackingMode
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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

internal class NativeTerminalHolder(
    val emulator: TerminalEmulator,
) {
    private val responseBuffer = StringBuilder()

    init {
        emulator.headlessMode = true
        emulator.onTerminalResponse = { resp -> appendResponse(resp) }
    }

    private var scratchBuffer = ByteArray(4096)

    fun borrowBuffer(len: Int): ByteArray {
        if (len > scratchBuffer.size) {
            var newSize = scratchBuffer.size
            while (newSize < len) {
                newSize = newSize shl 1
            }
            scratchBuffer = ByteArray(newSize)
        }
        return scratchBuffer
    }

    fun appendResponse(resp: String) {
        if (responseBuffer.length + resp.length <= 65536) {
            responseBuffer.append(resp)
        }
    }

    fun drainResponse(outUtf8: CPointer<ByteVar>?, capacity: Int): Int {
        if (responseBuffer.isEmpty()) return -1
        val bytes = responseBuffer.toString().encodeToByteArray()
        if (outUtf8 == null || capacity <= 0) {
            return bytes.size
        }
        val count = bytes.size.coerceAtMost(capacity)
        for (i in 0 until count) {
            outUtf8[i] = bytes[i]
        }
        if (capacity >= bytes.size) {
            responseBuffer.clear()
        }
        return bytes.size
    }
}

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
    val holder = NativeTerminalHolder(emulator)
    return StableRef.create(holder).asCPointer()
}

@CName("terminal_copy")
fun terminalCopy(handle: COpaquePointer): COpaquePointer {
    val holder = handle.asStableRef<NativeTerminalHolder>().get()
    val clone = holder.emulator.copy()
    val cloneHolder = NativeTerminalHolder(clone)
    return StableRef.create(cloneHolder).asCPointer()
}

@CName("terminal_destroy")
fun terminalDestroy(handle: COpaquePointer) {
    handle.asStableRef<NativeTerminalHolder>().dispose()
}

@CName("terminal_process_bytes")
fun terminalProcessBytes(
    handle: COpaquePointer,
    bytes: CPointer<ByteVar>?,
    len: Int,
) {
    if (bytes == null || len <= 0) return
    val holder = handle.asStableRef<NativeTerminalHolder>().get()
    val buf = holder.borrowBuffer(len)
    buf.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, len.convert())
    }
    holder.emulator.processOutputSync(buf, len)
}

@CName("terminal_resize")
fun terminalResize(
    handle: COpaquePointer,
    rows: Int,
    cols: Int,
): Int {
    if (rows <= 0 || cols <= 0) return 0
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return if (emulator.resizeSync(rows, cols)) 1 else 0
}

@CName("terminal_clear")
fun terminalClear(handle: COpaquePointer) {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    emulator.clearSync()
}

@CName("terminal_reset")
fun terminalReset(handle: COpaquePointer) {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    emulator.resetSync()
}

@CName("terminal_get_rows")
fun terminalGetRows(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.getScreenBuffer().rows
}

@CName("terminal_get_cols")
fun terminalGetCols(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.getScreenBuffer().cols
}

@CName("terminal_get_cursor_row")
fun terminalGetCursorRow(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.getScreenBuffer().cursorRow
}

@CName("terminal_get_cursor_col")
fun terminalGetCursorCol(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.getScreenBuffer().cursorCol
}

@CName("terminal_is_cursor_visible")
fun terminalIsCursorVisible(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return if (emulator.getScreenBuffer().cursorVisible) 1 else 0
}

@CName("terminal_get_bell_count")
fun terminalGetBellCount(handle: COpaquePointer): Long {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.bellCount
}

@CName("terminal_get_clipboard_write_count")
fun terminalGetClipboardWriteCount(handle: COpaquePointer): Long {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.clipboardWriteCount
}

@CName("terminal_get_scrollback_size")
fun terminalGetScrollbackSize(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.getScreenBuffer().scrollbackSize
}

@CName("terminal_get_mouse_tracking_mode")
fun terminalGetMouseTrackingMode(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return when (emulator.mouseTrackingMode) {
        MouseTrackingMode.None -> 0
        MouseTrackingMode.Click -> 1
        MouseTrackingMode.CellMotion -> 2
        MouseTrackingMode.AllMotion -> 3
    }
}

@CName("terminal_is_sgr_mouse_mode_enabled")
fun terminalIsSgrMouseModeEnabled(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return if (emulator.sgrMouseModeEnabled) 1 else 0
}

@CName("terminal_read_grid")
fun terminalReadGrid(
    handle: COpaquePointer,
    outBuffer: CPointer<IntVar>?,
    maxCells: Int,
    paletteOrdinal: Int,
): Int {
    if (outBuffer == null || maxCells <= 0) return 0
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
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

@CName("terminal_read_viewport")
fun terminalReadViewport(
    handle: COpaquePointer,
    outBuffer: CPointer<IntVar>?,
    maxCells: Int,
    scrollbackOffset: Int,
    paletteOrdinal: Int,
): Int {
    if (outBuffer == null || maxCells <= 0) return 0
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    val palette = if (paletteOrdinal < 0) {
        null
    } else {
        when (paletteOrdinal) {
            0 -> TerminalPalette.DEFAULT
            1 -> TerminalPalette.SOLARIZED_DARK
            2 -> TerminalPalette.SOLARIZED_LIGHT
            3 -> TerminalPalette.MONOKAI
            4 -> TerminalPalette.NORD
            else -> TerminalPalette.DEFAULT
        }
    }
    val isDark = paletteOrdinal != 2
    return emulator.packViewportCells(
        maxCells = maxCells,
        scrollbackOffset = scrollbackOffset,
        palette = palette,
        isDark = isDark,
    ) { outOffset, codepoint, fgRgb, bgRgb, attrs ->
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
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    val text = TerminalSelection(startRow, startCol, endRow, endCol).extractText(emulator.getScreenBuffer())
    return writeUtf8ToBuffer(text, outUtf8, capacity)
}

@CName("terminal_get_window_title")
fun terminalGetWindowTitle(
    handle: COpaquePointer,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    val title = emulator.windowTitle.value ?: return -1
    return writeUtf8ToBuffer(title, outUtf8, capacity)
}

@CName("terminal_get_last_clipboard_write")
fun terminalGetLastClipboardWrite(
    handle: COpaquePointer,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    val text = emulator.lastClipboardWrite ?: return -1
    return writeUtf8ToBuffer(text, outUtf8, capacity)
}

@CName("terminal_get_cursor_shape")
fun terminalGetCursorShape(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return when (emulator.cursorShape) {
        CursorShape.BLOCK -> 0
        CursorShape.UNDERLINE -> 1
        CursorShape.BEAM -> 2
    }
}

@CName("terminal_is_cursor_blinking")
fun terminalIsCursorBlinking(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return if (emulator.cursorBlinking) 1 else 0
}

@CName("terminal_is_synchronized_output_enabled")
fun terminalIsSynchronizedOutputEnabled(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return if (emulator.synchronizedOutputEnabled) 1 else 0
}

@CName("terminal_is_focus_reporting_enabled")
fun terminalIsFocusReportingEnabled(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return if (emulator.focusReportingEnabled) 1 else 0
}

@CName("terminal_get_kitty_keyboard_flags")
fun terminalGetKittyKeyboardFlags(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.kittyKeyboardFlags
}

@CName("terminal_encode_key_event")
fun terminalEncodeKeyEvent(
    handle: COpaquePointer,
    codepoint: Int,
    modifiers: Int,
    eventTypeOrdinal: Int,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    val eventType = when (eventTypeOrdinal) {
        2 -> KeyEventType.REPEAT
        3 -> KeyEventType.RELEASE
        else -> KeyEventType.PRESS
    }
    val encoded = emulator.encodeKeyEvent(
        codepoint = codepoint,
        modifiers = modifiers,
        eventType = eventType,
    )
    return writeUtf8ToBuffer(encoded, outUtf8, capacity)
}

@CName("terminal_get_working_directory")
fun terminalGetWorkingDirectory(
    handle: COpaquePointer,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    val cwd = emulator.currentWorkingDirectory.value ?: return -1
    return writeUtf8ToBuffer(cwd, outUtf8, capacity)
}

@CName("terminal_get_last_exit_code")
fun terminalGetLastExitCode(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return emulator.lastExitCode.value ?: -1
}

@CName("terminal_is_command_running")
fun terminalIsCommandRunning(handle: COpaquePointer): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    return if (emulator.isCommandRunning.value) 1 else 0
}

@CName("terminal_extract_last_command_output")
fun terminalExtractLastCommandOutput(
    handle: COpaquePointer,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val emulator = handle.asStableRef<NativeTerminalHolder>().get().emulator
    val output = emulator.extractLastCommandOutput() ?: return -1
    return writeUtf8ToBuffer(output, outUtf8, capacity)
}

@CName("terminal_drain_response")
fun terminalDrainResponse(
    handle: COpaquePointer,
    outUtf8: CPointer<ByteVar>?,
    capacity: Int,
): Int {
    val holder = handle.asStableRef<NativeTerminalHolder>().get()
    return holder.drainResponse(outUtf8, capacity)
}
