@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.johnan.terminal.core.ffi

import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.TerminalPalette
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.set
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalNativeBridgeTest {
    private fun MemScope.allocBytes(bytes: ByteArray): CPointer<ByteVar> {
        val ptr = allocArray<ByteVar>(bytes.size)
        for (i in bytes.indices) {
            ptr[i] = bytes[i]
        }
        return ptr
    }

    private fun MemScope.allocString(str: String): CPointer<ByteVar> =
        allocBytes(str.encodeToByteArray())

    @Test
    fun testTerminalCreateAndProperties() {
        val handle = terminalCreate(rows = 24, cols = 80, maxScrollback = 100, osc52PolicyOrdinal = 1)
        try {
            assertEquals(24, terminalGetRows(handle))
            assertEquals(80, terminalGetCols(handle))
            assertEquals(0, terminalGetCursorRow(handle))
            assertEquals(0, terminalGetCursorCol(handle))
            assertEquals(1, terminalIsCursorVisible(handle))
            assertEquals(0L, terminalGetBellCount(handle))
            assertEquals(0L, terminalGetClipboardWriteCount(handle))
            assertEquals(0, terminalGetScrollbackSize(handle))
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalProcessBytesAndCursor() = memScoped {
        val handle = terminalCreate(rows = 10, cols = 40, maxScrollback = 50, osc52PolicyOrdinal = 0)
        try {
            val text = "Line 1\r\nLine 2"
            val bytes = text.encodeToByteArray()
            val ptr = allocBytes(bytes)

            terminalProcessBytes(handle, ptr, bytes.size)
            assertEquals(1, terminalGetCursorRow(handle))
            assertEquals(6, terminalGetCursorCol(handle))

            // Null pointer and zero length handling
            terminalProcessBytes(handle, null, 10)
            terminalProcessBytes(handle, ptr, 0)
            terminalProcessBytes(handle, ptr, -1)
            assertEquals(1, terminalGetCursorRow(handle))
            assertEquals(6, terminalGetCursorCol(handle))
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalResize() {
        val handle = terminalCreate(rows = 24, cols = 80, maxScrollback = 100, osc52PolicyOrdinal = 0)
        try {
            val resized = terminalResize(handle, rows = 30, cols = 100)
            assertEquals(1, resized)
            assertEquals(30, terminalGetRows(handle))
            assertEquals(100, terminalGetCols(handle))

            // Invalid dimensions
            assertEquals(0, terminalResize(handle, rows = 0, cols = 100))
            assertEquals(0, terminalResize(handle, rows = 30, cols = -1))
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalClearAndReset() = memScoped {
        val handle = terminalCreate(rows = 5, cols = 20, maxScrollback = 10, osc52PolicyOrdinal = 0)
        try {
            val text = "Hello"
            val ptr = allocString(text)
            terminalProcessBytes(handle, ptr, text.length)
            assertEquals(5, terminalGetCursorCol(handle))

            terminalClear(handle)
            assertEquals(0, terminalGetCursorRow(handle))
            assertEquals(0, terminalGetCursorCol(handle))

            terminalProcessBytes(handle, ptr, text.length)
            assertEquals(5, terminalGetCursorCol(handle))

            terminalReset(handle)
            assertEquals(0, terminalGetCursorRow(handle))
            assertEquals(0, terminalGetCursorCol(handle))
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalCopy() = memScoped {
        val handle = terminalCreate(rows = 10, cols = 30, maxScrollback = 20, osc52PolicyOrdinal = 0)
        try {
            val initial = "ABC"
            terminalProcessBytes(handle, allocString(initial), initial.length)

            val cloneHandle = terminalCopy(handle)
            try {
                assertEquals(10, terminalGetRows(cloneHandle))
                assertEquals(30, terminalGetCols(cloneHandle))
                assertEquals(3, terminalGetCursorCol(cloneHandle))

                // Mutating clone does not mutate original
                val more = "DEF"
                terminalProcessBytes(cloneHandle, allocString(more), more.length)
                assertEquals(6, terminalGetCursorCol(cloneHandle))
                assertEquals(3, terminalGetCursorCol(handle))
            } finally {
                terminalDestroy(cloneHandle)
            }
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalCursorVisibilityToggle() = memScoped {
        val handle = terminalCreate(rows = 5, cols = 20, maxScrollback = 10, osc52PolicyOrdinal = 0)
        try {
            assertEquals(1, terminalIsCursorVisible(handle))

            // Hide cursor DECTCSR: \u001B[?25l
            val hideSeq = "\u001B[?25l"
            terminalProcessBytes(handle, allocString(hideSeq), hideSeq.length)
            assertEquals(0, terminalIsCursorVisible(handle))

            // Show cursor DECTCSR: \u001B[?25h
            val showSeq = "\u001B[?25h"
            terminalProcessBytes(handle, allocString(showSeq), showSeq.length)
            assertEquals(1, terminalIsCursorVisible(handle))
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalBellCount() = memScoped {
        val handle = terminalCreate(rows = 5, cols = 20, maxScrollback = 10, osc52PolicyOrdinal = 0)
        try {
            assertEquals(0L, terminalGetBellCount(handle))

            // Send two BEL characters (ASCII 7)
            val belSeq = "\u0007\u0007"
            terminalProcessBytes(handle, allocString(belSeq), belSeq.length)
            assertEquals(2L, terminalGetBellCount(handle))
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalClipboardAndLastWrite() = memScoped {
        // osc52PolicyOrdinal = 1 -> ALWAYS_ALLOW
        val handle = terminalCreate(rows = 5, cols = 20, maxScrollback = 10, osc52PolicyOrdinal = 1)
        try {
            assertEquals(0L, terminalGetClipboardWriteCount(handle))
            assertEquals(-1, terminalGetLastClipboardWrite(handle, null, 0))

            // OSC 52 write: "Hello" in Base64 is "SGVsbG8="
            val osc52 = "\u001B]52;c;SGVsbG8=\u0007"
            terminalProcessBytes(handle, allocString(osc52), osc52.length)

            assertEquals(1L, terminalGetClipboardWriteCount(handle))

            // Query length with null buffer
            val len = terminalGetLastClipboardWrite(handle, null, 0)
            assertEquals(5, len)

            // Read into buffer
            val clipBuf = allocArray<ByteVar>(len)
            val written = terminalGetLastClipboardWrite(handle, clipBuf, len)
            assertEquals(5, written)
            assertEquals("Hello", clipBuf.readBytes(written).decodeToString())
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalScrollbackSize() = memScoped {
        val handle = terminalCreate(rows = 2, cols = 10, maxScrollback = 10, osc52PolicyOrdinal = 0)
        try {
            assertEquals(0, terminalGetScrollbackSize(handle))

            // Print 4 newlines to scroll text into scrollback
            val lines = "R1\r\nR2\r\nR3\r\nR4\r\n"
            terminalProcessBytes(handle, allocString(lines), lines.length)

            assertTrue(terminalGetScrollbackSize(handle) > 0)
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalReadGrid() = memScoped {
        val handle = terminalCreate(rows = 1, cols = 2, maxScrollback = 0, osc52PolicyOrdinal = 0)
        try {
            // Null buffer or non-positive capacity returns 0
            assertEquals(0, terminalReadGrid(handle, null, 2, 0))

            val gridBuf = allocArray<IntVar>(8)
            assertEquals(0, terminalReadGrid(handle, gridBuf, 0, 0))

            // Red FG (SGR 31), Green BG (SGR 42), Bold (SGR 1)
            val seq = "\u001B[31;42;1mXY"
            terminalProcessBytes(handle, allocString(seq), seq.length)

            val written = terminalReadGrid(handle, gridBuf, 2, paletteOrdinal = 0)
            assertEquals(2, written)

            // Cell 0 ('X')
            assertEquals('X'.code, gridBuf[0])
            assertEquals(TerminalPalette.DEFAULT.red, gridBuf[1])
            assertEquals(TerminalPalette.DEFAULT.green, gridBuf[2])
            assertTrue((gridBuf[3] and TerminalEmulator.ATTR_BOLD) != 0)

            // Cell 1 ('Y')
            assertEquals('Y'.code, gridBuf[4])
            assertEquals(TerminalPalette.DEFAULT.red, gridBuf[5])
            assertEquals(TerminalPalette.DEFAULT.green, gridBuf[6])
            assertTrue((gridBuf[7] and TerminalEmulator.ATTR_BOLD) != 0)

            // Test palette ordinal 2 (Solarized Light)
            terminalReadGrid(handle, gridBuf, 2, paletteOrdinal = 2)
            assertEquals(TerminalPalette.SOLARIZED_LIGHT.red, gridBuf[1])
            assertEquals(TerminalPalette.SOLARIZED_LIGHT.green, gridBuf[2])
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalExtractSelection() = memScoped {
        val handle = terminalCreate(rows = 2, cols = 20, maxScrollback = 0, osc52PolicyOrdinal = 0)
        try {
            val text = "SelectionTest"
            terminalProcessBytes(handle, allocString(text), text.length)

            // Query selection length with null buffer
            val selLen = terminalExtractSelection(handle, 0, 0, 0, 8, null, 0)
            assertEquals(9, selLen)

            // Read selection
            val selBuf = allocArray<ByteVar>(selLen)
            val written = terminalExtractSelection(handle, 0, 0, 0, 8, selBuf, selLen)
            assertEquals(9, written)
            assertEquals("Selection", selBuf.readBytes(written).decodeToString())
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalWindowTitle() = memScoped {
        val handle = terminalCreate(rows = 5, cols = 20, maxScrollback = 0, osc52PolicyOrdinal = 0)
        try {
            // Unset window title returns -1
            assertEquals(-1, terminalGetWindowTitle(handle, null, 0))

            // Set window title via OSC 0: \u001B]0;My Terminal\u0007
            val seq = "\u001B]0;My Terminal\u0007"
            terminalProcessBytes(handle, allocString(seq), seq.length)

            val titleLen = terminalGetWindowTitle(handle, null, 0)
            val expectedTitle = "My Terminal"
            assertEquals(expectedTitle.length, titleLen)

            val titleBuf = allocArray<ByteVar>(titleLen)
            val written = terminalGetWindowTitle(handle, titleBuf, titleLen)
            assertEquals(titleLen, written)
            assertEquals(expectedTitle, titleBuf.readBytes(written).decodeToString())
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalMouseTrackingAndSgr() = memScoped {
        val handle = terminalCreate(rows = 5, cols = 20, maxScrollback = 0, osc52PolicyOrdinal = 0)
        try {
            assertEquals(0, terminalGetMouseTrackingMode(handle))
            assertEquals(0, terminalIsSgrMouseModeEnabled(handle))

            // Normal tracking (1000h)
            val clickSeq = "\u001B[?1000h"
            terminalProcessBytes(handle, allocString(clickSeq), clickSeq.length)
            assertEquals(1, terminalGetMouseTrackingMode(handle))

            // Button event tracking (1002h)
            val buttonSeq = "\u001B[?1002h"
            terminalProcessBytes(handle, allocString(buttonSeq), buttonSeq.length)
            assertEquals(2, terminalGetMouseTrackingMode(handle))

            // Any event tracking (1003h)
            val anySeq = "\u001B[?1003h"
            terminalProcessBytes(handle, allocString(anySeq), anySeq.length)
            assertEquals(3, terminalGetMouseTrackingMode(handle))

            // SGR mouse mode (1006h)
            val sgrOn = "\u001B[?1006h"
            terminalProcessBytes(handle, allocString(sgrOn), sgrOn.length)
            assertEquals(1, terminalIsSgrMouseModeEnabled(handle))

            val sgrOff = "\u001B[?1006l"
            terminalProcessBytes(handle, allocString(sgrOff), sgrOff.length)
            assertEquals(0, terminalIsSgrMouseModeEnabled(handle))
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalReadViewportWithScrollbackAndRawColors() = memScoped {
        val handle = terminalCreate(rows = 2, cols = 4, maxScrollback = 10, osc52PolicyOrdinal = 0)
        try {
            // Write 3 lines: line 1 scrolled to scrollback, line 2 in scrollback, line 3 live
            val text = "\u001B[31mA\r\n\u001B[32mB\r\n\u001B[34mC"
            terminalProcessBytes(handle, allocString(text), text.length)

            assertTrue(terminalGetScrollbackSize(handle) >= 1)

            val maxCells = 8
            val viewBuf = allocArray<IntVar>(maxCells * 4)

            // Read with scrollbackOffset = 1 and paletteOrdinal = -1 (raw ANSI colors)
            val count = terminalReadViewport(handle, viewBuf, maxCells, scrollbackOffset = 1, paletteOrdinal = -1)
            assertEquals(8, count)

            // When offset = 1, startLine is sbSize - 1, which corresponds to the line with 'A'
            assertEquals('A'.code, viewBuf[0])
            // Standard Red (code 1): 0x0100_0000 or 1
            assertEquals(0x0100_0001, viewBuf[1])

            // Next row in viewport is live line 0 with 'B' (Standard Green: 0x0100_0002)
            val row1Offset = 4 * 4
            assertEquals('B'.code, viewBuf[row1Offset])
            assertEquals(0x0100_0002, viewBuf[row1Offset + 1])

            // Live viewport with scrollbackOffset = 0: shows 'B' and 'C'
            terminalReadViewport(handle, viewBuf, maxCells, scrollbackOffset = 0, paletteOrdinal = -1)
            assertEquals('B'.code, viewBuf[0])
            assertEquals(0x0100_0002, viewBuf[1])
            assertEquals('C'.code, viewBuf[row1Offset])
            assertEquals(0x0100_0004, viewBuf[row1Offset + 1])
        } finally {
            terminalDestroy(handle)
        }
    }

    @Test
    fun testTerminalReadViewportWideChar() = memScoped {
        val handle = terminalCreate(rows = 2, cols = 6, maxScrollback = 0, osc52PolicyOrdinal = 0)
        try {
            val text = "🚀漢"
            terminalProcessBytes(handle, allocString(text), text.encodeToByteArray().size)

            val maxCells = 12
            val viewBuf = allocArray<IntVar>(maxCells * 4)
            val count = terminalReadViewport(handle, viewBuf, maxCells, scrollbackOffset = 0, paletteOrdinal = 0)
            assertEquals(12, count)

            // Cell 0: Rocket emoji (0x1F680)
            assertEquals(0x1F680, viewBuf[0])
            assertTrue((viewBuf[3] and TerminalEmulator.ATTR_WIDE_CHAR) != 0)
            assertEquals(0, viewBuf[3] and TerminalEmulator.ATTR_WIDE_SPACER)

            // Cell 1: Wide spacer
            assertTrue((viewBuf[7] and TerminalEmulator.ATTR_WIDE_SPACER) != 0)
            assertEquals(0, viewBuf[7] and TerminalEmulator.ATTR_WIDE_CHAR)

            // Cell 2: CJK character (0x6F22)
            assertEquals(0x6F22, viewBuf[8])
            assertTrue((viewBuf[11] and TerminalEmulator.ATTR_WIDE_CHAR) != 0)
            assertEquals(0, viewBuf[11] and TerminalEmulator.ATTR_WIDE_SPACER)

            // Cell 3: Wide spacer
            assertTrue((viewBuf[15] and TerminalEmulator.ATTR_WIDE_SPACER) != 0)
            assertEquals(0, viewBuf[15] and TerminalEmulator.ATTR_WIDE_CHAR)
        } finally {
            terminalDestroy(handle)
        }
    }
}
