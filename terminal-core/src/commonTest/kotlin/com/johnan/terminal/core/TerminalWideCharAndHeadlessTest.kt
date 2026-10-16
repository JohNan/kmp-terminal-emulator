package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalWideCharAndHeadlessTest {
    @Test
    fun testWideEmojiAndCJKColumns() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))
        emulator.processOutputSync("🚀漢")

        val sb = emulator.getScreenBuffer()
        val row = sb.getRow(0)

        // Col 0: Rocket emoji (0x1F680)
        assertEquals(0x1F680, row[0].codepoint)
        assertEquals(2, row[0].width)
        assertFalse(row[0].isWideSpacer)
        assertEquals("🚀", row[0].glyphString())

        // Col 1: Wide spacer
        assertEquals(0, row[1].width)
        assertTrue(row[1].isWideSpacer)
        assertEquals("", row[1].glyphString())

        // Col 2: CJK character (0x6F22)
        assertEquals(0x6F22, row[2].codepoint)
        assertEquals(2, row[2].width)
        assertFalse(row[2].isWideSpacer)
        assertEquals("漢", row[2].glyphString())

        // Col 3: Wide spacer
        assertEquals(0, row[3].width)
        assertTrue(row[3].isWideSpacer)
        assertEquals("", row[3].glyphString())

        // Check text selection extraction without duplicate spaces
        val selection = TerminalSelection(startRow = 0, startCol = 0, endRow = 0, endCol = 3)
        assertEquals("🚀漢", selection.extractText(sb))

        // Check packViewportCells attributes
        val attrsList = mutableListOf<Int>()
        emulator.packViewportCells(
            maxCells = 4,
            scrollbackOffset = 0,
            palette = TerminalPalette.DEFAULT,
            isDark = true,
        ) { _, _, _, _, attrs ->
            attrsList.add(attrs)
        }

        assertTrue((attrsList[0] and TerminalEmulator.ATTR_WIDE_CHAR) != 0)
        assertEquals(0, attrsList[0] and TerminalEmulator.ATTR_WIDE_SPACER)

        assertTrue((attrsList[1] and TerminalEmulator.ATTR_WIDE_SPACER) != 0)
        assertEquals(0, attrsList[1] and TerminalEmulator.ATTR_WIDE_CHAR)

        assertTrue((attrsList[2] and TerminalEmulator.ATTR_WIDE_CHAR) != 0)
        assertEquals(0, attrsList[2] and TerminalEmulator.ATTR_WIDE_SPACER)

        assertTrue((attrsList[3] and TerminalEmulator.ATTR_WIDE_SPACER) != 0)
        assertEquals(0, attrsList[3] and TerminalEmulator.ATTR_WIDE_CHAR)
    }

    @Test
    fun testWideCharAutoWrapAtMargin() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 4, initialCols = 4))
        emulator.processOutputSync("ABC🚀")

        val sb = emulator.getScreenBuffer()
        val row0 = sb.getRow(0)
        val row1 = sb.getRow(1)

        // Row 0 has "ABC " with col 3 cleared to EMPTY and row soft-wrapped
        assertEquals('A'.code, row0[0].codepoint)
        assertEquals('B'.code, row0[1].codepoint)
        assertEquals('C'.code, row0[2].codepoint)
        assertTrue(row0[3].isDefault())
        assertTrue(sb.isRowWrapped(0))

        // Row 1 has "🚀" at cols 0..1
        assertEquals(0x1F680, row1[0].codepoint)
        assertEquals(2, row1[0].width)
        assertTrue(row1[1].isWideSpacer)
    }

    @Test
    fun testOverwritingWideCharCleansPairedHalf() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 2, initialCols = 10))
        emulator.processOutputSync("🚀")

        val sb = emulator.getScreenBuffer()
        // Overwrite leading wide cell with narrow char 'X'
        sb.setCursorPosition(0, 0)
        emulator.processOutputSync("X")

        val row = sb.getRow(0)
        assertEquals('X'.code, row[0].codepoint)
        assertEquals(1, row[0].width)
        assertFalse(row[0].isWideSpacer)
        // Col 1 must have been cleaned
        assertTrue(row[1].isDefault())

        // Now write "🚀" again and overwrite the continuation spacer with 'Y'
        sb.setCursorPosition(0, 0)
        emulator.processOutputSync("🚀")
        sb.setCursorPosition(0, 1)
        emulator.processOutputSync("Y")

        val rowAfterY = sb.getRow(0)
        // Col 0 must have been cleaned
        assertTrue(rowAfterY[0].isDefault())
        // Col 1 is 'Y'
        assertEquals('Y'.code, rowAfterY[1].codepoint)
        assertEquals(1, rowAfterY[1].width)
    }

    @Test
    fun testHeadlessModeSkipsScreenState() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))
        emulator.headlessMode = true

        val initialState = emulator.screenState.value
        emulator.processOutputSync("Hello Headless")

        // _screenState must NOT have been updated
        assertTrue(emulator.screenState.value === initialState)

        // Live buffer is updated and readable via packViewportCells
        val codepoints = mutableListOf<Int>()
        emulator.packViewportCells(
            maxCells = 14,
            scrollbackOffset = 0,
            palette = TerminalPalette.DEFAULT,
            isDark = true,
        ) { _, codepoint, _, _, _ ->
            codepoints.add(codepoint)
        }
        val text = codepoints.map { it.toChar() }.joinToString("")
        assertEquals("Hello Headless", text)

        // snapshotScreenState forces an update
        val snapshot = emulator.snapshotScreenState()
        assertTrue(emulator.screenState.value === snapshot)
        assertEquals('H'.code, snapshot.rows[0][0].codepoint)
    }

    @Test
    fun testRepWithWideChar() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 2, initialCols = 10))
        // 🚀 followed by CSI 2 b (repeat 2 more times)
        emulator.processOutputSync("🚀\u001B[2b")

        val sb = emulator.getScreenBuffer()
        val row = sb.getRow(0)
        assertEquals(0x1F680, row[0].codepoint)
        assertTrue(row[1].isWideSpacer)
        assertEquals(0x1F680, row[2].codepoint)
        assertTrue(row[3].isWideSpacer)
        assertEquals(0x1F680, row[4].codepoint)
        assertTrue(row[5].isWideSpacer)
    }

    @Test
    fun testRawColorsInPackViewportCells() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 2, initialCols = 10))
        // Standard red (31), 256-palette color 123 (38;5;123), 24-bit RGB (38;2;10;20;30)
        emulator.processOutputSync("\u001B[31mA\u001B[38;5;123mB\u001B[38;2;10;20;30mC")

        val fgColors = mutableListOf<Int>()
        emulator.packViewportCells(
            maxCells = 3,
            scrollbackOffset = 0,
            palette = null,
            isDark = true,
        ) { _, _, fgRgb, _, _ ->
            fgColors.add(fgRgb)
        }

        // Standard 1 (red): 0x0100_0000 or 1
        assertEquals(0x0100_0001, fgColors[0])
        // Palette256 123: 0x0200_0000 or 123
        assertEquals(0x0200_007B, fgColors[1])
        // RGB (10, 20, 30): (10 shl 16) or (20 shl 8) or 30
        assertEquals((10 shl 16) or (20 shl 8) or 30, fgColors[2])
    }

    @Test
    fun testEraseAndClearLineCleansWidePairedHalves() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 2, initialCols = 10))
        val sb = emulator.getScreenBuffer()

        // 1. CSI K (clear to end of line) from spacer position
        emulator.processOutputSync("🚀")
        sb.setCursorPosition(0, 1)
        emulator.processOutputSync("\u001B[K")
        val rowAfterEl = sb.getRow(0)
        assertTrue(rowAfterEl[0].isDefault(), "Lead wide cell should be cleared when spacer is cleared via EL")
        assertTrue(rowAfterEl[1].isDefault(), "Spacer cell should be cleared via EL")

        // 2. CSI 1 X (erase 1 character) from lead wide position
        emulator.processOutputSync("\u001B[2J\u001B[H")
        emulator.processOutputSync("🚀")
        sb.setCursorPosition(0, 0)
        emulator.processOutputSync("\u001B[1X")
        val rowAfterEchLead = sb.getRow(0)
        assertTrue(rowAfterEchLead[0].isDefault(), "Lead wide cell should be cleared via ECH")
        assertTrue(rowAfterEchLead[1].isDefault(), "Spacer cell should be cleaned when lead is erased via ECH")

        // 3. CSI 1 X (erase 1 character) from spacer position
        emulator.processOutputSync("\u001B[2J\u001B[H")
        emulator.processOutputSync("漢")
        sb.setCursorPosition(0, 1)
        emulator.processOutputSync("\u001B[1X")
        val rowAfterEchSpacer = sb.getRow(0)
        assertTrue(rowAfterEchSpacer[0].isDefault(), "Lead cell should be cleaned when spacer is erased via ECH")
        assertTrue(rowAfterEchSpacer[1].isDefault(), "Spacer cell should be cleared via ECH")
    }

    @Test
    fun testWideCharNonAutowrapMargin() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 2, initialCols = 4))
        // Disable DECAWM autowrap (CSI ? 7 l)
        emulator.processOutputSync("\u001B[?7l")
        emulator.processOutputSync("ABC🚀")

        val sb = emulator.getScreenBuffer()
        val row0 = sb.getRow(0)
        assertEquals('A'.code, row0[0].codepoint)
        assertEquals('B'.code, row0[1].codepoint)
        assertEquals('C'.code, row0[2].codepoint)
        // Col 3 should remain empty (not overwritten with half of 🚀)
        assertTrue(row0[3].isDefault())
    }
}
