package com.johnan.terminal.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalEmulatorSyncGridTest {
    @Test
    fun testProcessOutputSyncParity() = runTest {
        val emuAsync = TerminalEmulator(rows = 10, cols = 40)
        val emuSync = TerminalEmulator(rows = 10, cols = 40)

        val input = "Hello, world!\r\n\u001B[31mRed text\u001B[0m\r\n\u001B[1;4mBold Underline\u001B[0m"
        emuAsync.processOutput(input)
        emuSync.processOutputSync(input)

        val asyncState = emuAsync.screenState.value
        val syncState = emuSync.screenState.value

        assertEquals(asyncState.cursorRow, syncState.cursorRow)
        assertEquals(asyncState.cursorCol, syncState.cursorCol)
        assertEquals(asyncState.rows.size, syncState.rows.size)
        for (r in 0 until asyncState.rows.size) {
            val asyncRow = asyncState.rows[r]
            val syncRow = syncState.rows[r]
            for (c in 0 until asyncState.cols) {
                assertEquals(asyncRow[c], syncRow[c], "Cell mismatch at row $r col $c")
            }
        }

        // Test ByteArray overload parity
        val bytes = "More bytes\r\n".encodeToByteArray()
        emuAsync.processOutput(bytes)
        emuSync.processOutputSync(bytes)
        assertEquals(emuAsync.screenState.value.cursorRow, emuSync.screenState.value.cursorRow)
        assertEquals(emuAsync.screenState.value.cursorCol, emuSync.screenState.value.cursorCol)
    }

    @Test
    fun testResizeSyncParity() = runTest {
        val emuAsync = TerminalEmulator(rows = 24, cols = 80)
        val emuSync = TerminalEmulator(rows = 24, cols = 80)

        val asyncResized = emuAsync.resize(30, 100)
        val syncResized = emuSync.resizeSync(30, 100)

        assertTrue(asyncResized)
        assertTrue(syncResized)
        assertEquals(30, emuAsync.getScreenBuffer().rows)
        assertEquals(30, emuSync.getScreenBuffer().rows)
        assertEquals(100, emuAsync.getScreenBuffer().cols)
        assertEquals(100, emuSync.getScreenBuffer().cols)

        // Resizing to same size should return false
        val asyncUnchanged = emuAsync.resize(30, 100)
        val syncUnchanged = emuSync.resizeSync(30, 100)
        assertFalse(asyncUnchanged)
        assertFalse(syncUnchanged)
    }

    @Test
    fun testClearSyncParity() = runTest {
        val emuAsync = TerminalEmulator(rows = 5, cols = 20)
        val emuSync = TerminalEmulator(rows = 5, cols = 20)

        emuAsync.processOutput("Line 1\r\nLine 2\r\nLine 3")
        emuSync.processOutputSync("Line 1\r\nLine 2\r\nLine 3")

        emuAsync.clear()
        emuSync.clearSync()

        assertEquals(0, emuAsync.getScreenBuffer().cursorRow)
        assertEquals(0, emuAsync.getScreenBuffer().cursorCol)
        assertEquals(0, emuSync.getScreenBuffer().cursorRow)
        assertEquals(0, emuSync.getScreenBuffer().cursorCol)

        for (r in 0 until 5) {
            val asyncRow = emuAsync.getScreenBuffer().getRow(r)
            val syncRow = emuSync.getScreenBuffer().getRow(r)
            for (c in 0 until 20) {
                assertEquals(TerminalCell.EMPTY, asyncRow[c])
                assertEquals(TerminalCell.EMPTY, syncRow[c])
            }
        }
    }

    @Test
    fun testResetSyncParity() = runTest {
        val emuAsync = TerminalEmulator(rows = 5, cols = 20)
        val emuSync = TerminalEmulator(rows = 5, cols = 20)

        val styled = "\u001B[31;42;1mStyled text"
        emuAsync.processOutput(styled)
        emuSync.processOutputSync(styled)

        emuAsync.reset()
        emuSync.resetSync()

        assertEquals(0, emuAsync.getScreenBuffer().cursorRow)
        assertEquals(0, emuAsync.getScreenBuffer().cursorCol)
        assertEquals(0, emuSync.getScreenBuffer().cursorRow)
        assertEquals(0, emuSync.getScreenBuffer().cursorCol)

        for (r in 0 until 5) {
            val asyncRow = emuAsync.getScreenBuffer().getRow(r)
            val syncRow = emuSync.getScreenBuffer().getRow(r)
            for (c in 0 until 20) {
                assertEquals(TerminalCell.EMPTY, asyncRow[c])
                assertEquals(TerminalCell.EMPTY, syncRow[c])
            }
        }
    }

    @Test
    fun testSearchSyncParity() = runTest {
        val emuAsync = TerminalEmulator(rows = 10, cols = 40)
        val emuSync = TerminalEmulator(rows = 10, cols = 40)

        val text = "Apple BANANA Cherry\r\nbanana date FIG"
        emuAsync.processOutput(text)
        emuSync.processOutputSync(text)

        val asyncInsensitive = emuAsync.search("banana", ignoreCase = true)
        val syncInsensitive = emuSync.searchSync("banana", ignoreCase = true)
        assertEquals(asyncInsensitive.size, syncInsensitive.size)
        assertEquals(2, syncInsensitive.size)
        for (i in asyncInsensitive.indices) {
            assertEquals(asyncInsensitive[i], syncInsensitive[i])
        }

        val asyncSensitive = emuAsync.search("banana", ignoreCase = false)
        val syncSensitive = emuSync.searchSync("banana", ignoreCase = false)
        assertEquals(asyncSensitive.size, syncSensitive.size)
        assertEquals(1, syncSensitive.size)
        assertEquals(asyncSensitive[0], syncSensitive[0])
    }

    @Test
    fun testPackGridToBufferCodepointsAndBounds() {
        val emu = TerminalEmulator(rows = 2, cols = 4)
        emu.processOutputSync("ABCD\r\nEFGH")

        // Empty buffer
        val emptyBuf = IntArray(0)
        assertEquals(0, emu.packGridToBuffer(emptyBuf))

        // Buffer smaller than 4 ints
        val smallBuf = IntArray(3)
        assertEquals(0, emu.packGridToBuffer(smallBuf))

        // Buffer with space for exactly 2 cells (8 ints)
        val twoCellBuf = IntArray(8)
        val writtenTwo = emu.packGridToBuffer(twoCellBuf)
        assertEquals(2, writtenTwo)
        assertEquals('A'.code, twoCellBuf[0])
        assertEquals('B'.code, twoCellBuf[4])

        // Full grid buffer: 2 * 4 = 8 cells -> 32 ints
        val fullBuf = IntArray(8 * TerminalEmulator.PACKED_CELL_INTS)
        val writtenFull = emu.packGridToBuffer(fullBuf)
        assertEquals(8, writtenFull)
        val expectedChars = listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H')
        for (i in expectedChars.indices) {
            assertEquals(expectedChars[i].code, fullBuf[i * TerminalEmulator.PACKED_CELL_INTS])
        }
    }

    @Test
    fun testPackGridToBufferAnsi16AcrossPalettes() {
        val emu = TerminalEmulator(rows = 1, cols = 2)
        // Red fg (SGR 31, code 1), Green bg (SGR 42, code 2)
        emu.processOutputSync("\u001B[31;42mX")

        val palettes = listOf(
            TerminalPalette.DEFAULT,
            TerminalPalette.SOLARIZED_DARK,
            TerminalPalette.SOLARIZED_LIGHT,
            TerminalPalette.MONOKAI,
            TerminalPalette.NORD,
        )

        val buf = IntArray(8)
        for (palette in palettes) {
            val isDark = palette != TerminalPalette.SOLARIZED_LIGHT
            val written = emu.packGridToBuffer(buf, palette = palette, isDark = isDark)
            assertEquals(2, written)

            val charCode = buf[0]
            val fgRgb = buf[1]
            val bgRgb = buf[2]

            assertEquals('X'.code, charCode)
            assertEquals(palette.red, fgRgb, "FG red mismatch for palette ${palette.name}")
            assertEquals(palette.green, bgRgb, "BG green mismatch for palette ${palette.name}")
        }
    }

    @Test
    fun testPackGridToBuffer256AndTrueColor() {
        val emu = TerminalEmulator(rows = 1, cols = 3)
        // Cell 0: 256 color fg 196, bg 46
        // Cell 1: 24-bit TrueColor fg (12, 34, 56), bg (78, 90, 120)
        // Cell 2: Default fg and bg
        val seq = "\u001B[38;5;196;48;5;46m1\u001B[38;2;12;34;56;48;2;78;90;120m2\u001B[0m3"
        emu.processOutputSync(seq)

        val buf = IntArray(3 * TerminalEmulator.PACKED_CELL_INTS)
        val palette = TerminalPalette.DEFAULT
        emu.packGridToBuffer(buf, palette = palette, isDark = true)

        // Cell 0: 256 color
        assertEquals('1'.code, buf[0])
        val expected256Fg = TerminalColor.toRgbInt(TerminalColor.Palette256(196), palette, true, false)
        val expected256Bg = TerminalColor.toRgbInt(TerminalColor.Palette256(46), palette, true, true)
        assertEquals(expected256Fg, buf[1])
        assertEquals(expected256Bg, buf[2])

        // Cell 1: TrueColor
        assertEquals('2'.code, buf[4])
        val expectedTrueFg = (12 shl 16) or (34 shl 8) or 56
        val expectedTrueBg = (78 shl 16) or (90 shl 8) or 120
        assertEquals(expectedTrueFg, buf[5])
        assertEquals(expectedTrueBg, buf[6])

        // Cell 2: Default colors
        assertEquals('3'.code, buf[8])
        assertEquals(palette.foreground, buf[9])
        assertEquals(palette.background, buf[10])
    }

    @Test
    fun testPackGridToBufferReverseVideoSwapping() {
        val emu = TerminalEmulator(rows = 1, cols = 1)
        // SGR 31 (red fg), SGR 42 (green bg), SGR 7 (reverse)
        emu.processOutputSync("\u001B[31;42;7mR")

        val buf = IntArray(TerminalEmulator.PACKED_CELL_INTS)
        val palette = TerminalPalette.DEFAULT
        emu.packGridToBuffer(buf, palette = palette, isDark = true)

        assertEquals('R'.code, buf[0])
        // With reverse video, FG is green and BG is red
        assertEquals(palette.green, buf[1], "Reversed FG should equal green")
        assertEquals(palette.red, buf[2], "Reversed BG should equal red")
        assertTrue((buf[3] and TerminalEmulator.ATTR_REVERSE) != 0, "ATTR_REVERSE must be set")
    }

    @Test
    fun testPackGridToBufferAllStyleBitmasks() {
        val emu = TerminalEmulator(rows = 1, cols = 9)
        // Bold: 1, Dim: 2, Italic: 3, Underline: 4, Blink: 5, Reverse: 7, Conceal: 8, Strikethrough: 9, Overline: 53
        val seq = "\u001B[1mB\u001B[0;2mD\u001B[0;3mI\u001B[0;4mU\u001B[0;5mK" +
            "\u001B[0;7mR\u001B[0;8mC\u001B[0;9mS\u001B[0;53mO\u001B[0m"
        emu.processOutputSync(seq)

        val buf = IntArray(9 * TerminalEmulator.PACKED_CELL_INTS)
        emu.packGridToBuffer(buf)

        val expectedAttrs = listOf(
            TerminalEmulator.ATTR_BOLD,
            TerminalEmulator.ATTR_DIM,
            TerminalEmulator.ATTR_ITALIC,
            TerminalEmulator.ATTR_UNDERLINE,
            TerminalEmulator.ATTR_BLINK,
            TerminalEmulator.ATTR_REVERSE,
            TerminalEmulator.ATTR_CONCEAL,
            TerminalEmulator.ATTR_STRIKETHROUGH,
            TerminalEmulator.ATTR_OVERLINE,
        )

        for (i in expectedAttrs.indices) {
            val attr = buf[i * TerminalEmulator.PACKED_CELL_INTS + 3]
            val expected = expectedAttrs[i]
            assertTrue(
                (attr and expected) == expected,
                "Expected attribute bit 0x${expected.toString(16)} in cell $i but got 0x${attr.toString(16)}",
            )
        }
    }

    @Test
    fun testPackGridToBufferRowWrappedBitmask() {
        val config = TerminalConfig(initialRows = 2, initialCols = 5)
        val emu = TerminalEmulator(config)
        // Output 8 characters to a 5-column terminal -> row 0 wraps into row 1
        emu.processOutputSync("12345678")

        val buf = IntArray(10 * TerminalEmulator.PACKED_CELL_INTS)
        val written = emu.packGridToBuffer(buf)
        assertEquals(10, written)

        // Cells in row 0 (0..4) should have ATTR_ROW_WRAPPED set
        for (c in 0 until 5) {
            val attrs = buf[c * TerminalEmulator.PACKED_CELL_INTS + 3]
            assertTrue(
                (attrs and TerminalEmulator.ATTR_ROW_WRAPPED) != 0,
                "Row 0 cell $c should have ATTR_ROW_WRAPPED",
            )
        }

        // Cells in row 1 (5..9) should NOT have ATTR_ROW_WRAPPED set
        for (c in 5 until 10) {
            val attrs = buf[c * TerminalEmulator.PACKED_CELL_INTS + 3]
            assertFalse(
                (attrs and TerminalEmulator.ATTR_ROW_WRAPPED) != 0,
                "Row 1 cell $c should not have ATTR_ROW_WRAPPED",
            )
        }
    }
}
