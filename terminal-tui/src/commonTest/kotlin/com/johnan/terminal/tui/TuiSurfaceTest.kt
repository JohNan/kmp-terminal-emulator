package com.johnan.terminal.tui

import com.johnan.terminal.core.ScreenBuffer
import com.johnan.terminal.core.TerminalColor
import com.johnan.terminal.core.TerminalPalette
import com.johnan.terminal.core.TerminalScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TuiSurfaceTest {
    @Test
    fun testInitializationAndClear() {
        val surface = TuiSurface(rows = 10, cols = 20)
        assertEquals(10, surface.rows)
        assertEquals(20, surface.cols)
        assertEquals(0, surface.cursorRow)
        assertEquals(0, surface.cursorCol)
        assertTrue(surface.cursorVisible)

        for (r in 0 until 10) {
            for (c in 0 until 20) {
                assertEquals(' ', surface.getChar(r, c))
                assertEquals(0xE2E8F0, surface.getFgRgb(r, c))
                assertEquals(0x090D16, surface.getBgRgb(r, c))
                assertEquals(TuiCellStyle.NONE, surface.getStyle(r, c))
            }
        }
    }

    @Test
    fun testSetCellAndBounds() {
        val surface = TuiSurface(rows = 5, cols = 5)
        surface.setCell(2, 2, 'X', 0xFF0000, 0x00FF00, TuiCellStyle.BOLD or TuiCellStyle.UNDERLINE)

        assertEquals('X', surface.getChar(2, 2))
        assertEquals(0xFF0000, surface.getFgRgb(2, 2))
        assertEquals(0x00FF00, surface.getBgRgb(2, 2))
        assertEquals(TuiCellStyle.BOLD or TuiCellStyle.UNDERLINE, surface.getStyle(2, 2))

        // Out of bounds sets should be safely ignored
        surface.setCell(-1, 0, 'Z', 0, 0)
        surface.setCell(0, -1, 'Z', 0, 0)
        surface.setCell(5, 0, 'Z', 0, 0)
        surface.setCell(0, 5, 'Z', 0, 0)

        // Out of bounds gets should return safe defaults
        assertEquals(' ', surface.getChar(-1, 0))
        assertEquals(' ', surface.getChar(5, 5))
        assertEquals(0, surface.getFgRgb(-1, -1))
        assertEquals(0, surface.getBgRgb(10, 10))
        assertEquals(TuiCellStyle.NONE, surface.getStyle(10, 10))
    }

    @Test
    fun testFillRect() {
        val surface = TuiSurface(rows = 6, cols = 8)
        surface.fillRect(row = 1, col = 2, height = 3, width = 4, char = '#', fgRgb = 0x112233, bgRgb = 0x445566)

        for (r in 1..3) {
            for (c in 2..5) {
                assertEquals('#', surface.getChar(r, c))
                assertEquals(0x112233, surface.getFgRgb(r, c))
                assertEquals(0x445566, surface.getBgRgb(r, c))
            }
        }

        // Surrounding cells should remain untouched
        assertEquals(' ', surface.getChar(0, 0))
        assertEquals(' ', surface.getChar(4, 2))
    }

    @Test
    fun testDrawText() {
        val surface = TuiSurface(rows = 4, cols = 10)
        surface.drawText(row = 1, col = 2, text = "HELLO", fgRgb = 0xFFFFFF, bgRgb = 0x000000)

        assertEquals('H', surface.getChar(1, 2))
        assertEquals('E', surface.getChar(1, 3))
        assertEquals('L', surface.getChar(1, 4))
        assertEquals('L', surface.getChar(1, 5))
        assertEquals('O', surface.getChar(1, 6))

        // Draw with maxWidth clipping
        surface.drawText(row = 2, col = 7, text = "WORLD", fgRgb = 0xFFFFFF, bgRgb = 0x000000, maxWidth = 2)
        assertEquals('W', surface.getChar(2, 7))
        assertEquals('O', surface.getChar(2, 8))
        assertEquals(' ', surface.getChar(2, 9))
    }

    @Test
    fun testDrawBox() {
        val surface = TuiSurface(rows = 5, cols = 10)
        surface.drawBox(
            row = 0,
            col = 0,
            height = 5,
            width = 10,
            title = "Test",
            borderFgRgb = 0xAAAAAA,
            bgRgb = 0x111111
        )

        assertEquals('╭', surface.getChar(0, 0))
        assertEquals('╮', surface.getChar(0, 9))
        assertEquals('╰', surface.getChar(4, 0))
        assertEquals('╯', surface.getChar(4, 9))
        assertEquals('│', surface.getChar(1, 0))
        assertEquals('│', surface.getChar(1, 9))
        assertEquals('─', surface.getChar(4, 5))

        // Check title rendered at row 0, col 2..7 (" Test ")
        val row0 = surface.extractRowText(0)
        assertTrue(row0.contains("Test"))
    }

    @Test
    fun testBlitScreenState() {
        val buffer = ScreenBuffer(initialRows = 5, initialCols = 10)
        buffer.writeText("Hello")
        buffer.carriageReturn()
        buffer.lineFeed()
        buffer.setTextAttributes(foreground = TerminalColor.Standard(1))
        buffer.writeText("Red")

        val state = TerminalScreenState.from(buffer)
        val surface = TuiSurface(rows = 10, cols = 20)

        surface.blitScreenState(
            state = state,
            destRow = 2,
            destCol = 3,
            height = 5,
            width = 10,
            palette = TerminalPalette.DEFAULT
        )

        assertEquals('H', surface.getChar(2, 3))
        assertEquals('e', surface.getChar(2, 4))
        assertEquals('l', surface.getChar(2, 5))
        assertEquals('l', surface.getChar(2, 6))
        assertEquals('o', surface.getChar(2, 7))

        assertEquals('R', surface.getChar(3, 3))
        assertEquals(TerminalPalette.DEFAULT.red, surface.getFgRgb(3, 3))

        // Cursor should be updated to blit destination + cursor position
        assertEquals(2 + state.cursorRow, surface.cursorRow)
        assertEquals(3 + state.cursorCol, surface.cursorCol)
    }

    @Test
    fun testBlitScreenStateWithSupplementaryCodepoint() {
        val buffer = ScreenBuffer(initialRows = 1, initialCols = 5)
        buffer.writeCodepoint(0xF06A9)
        buffer.writeText("A")

        val state = TerminalScreenState.from(buffer)
        val surface = TuiSurface(rows = 1, cols = 5)
        surface.blitScreenState(
            state = state,
            destRow = 0,
            destCol = 0,
            height = 1,
            width = 5,
        )

        assertEquals(' ', surface.getChar(0, 0))
        assertEquals(0xF06A9, surface.getCodepoint(0, 0))
        assertEquals('A', surface.getChar(0, 1))
        assertEquals('A'.code, surface.getCodepoint(0, 1))
    }

    @Test
    fun testBlitScreenStateWithScrollback() {
        val buffer = ScreenBuffer(initialRows = 3, initialCols = 10)
        for (i in 1..5) {
            buffer.writeText("Line $i")
            if (i < 5) {
                buffer.carriageReturn()
                buffer.lineFeed()
            }
        }

        val state = TerminalScreenState.from(buffer)
        assertTrue(state.scrollback.isNotEmpty())

        val surface = TuiSurface(rows = 3, cols = 10)
        // Scroll back by 2 lines
        surface.blitScreenState(
            state = state,
            destRow = 0,
            destCol = 0,
            height = 3,
            width = 10,
            scrollOffset = 2
        )

        assertFalse(surface.cursorVisible)
    }

    @Test
    fun testCopyFrom() {
        val s1 = TuiSurface(rows = 3, cols = 3)
        s1.setCell(1, 1, 'Q', 0x123456, 0x654321, TuiCellStyle.ITALIC)
        s1.cursorRow = 1
        s1.cursorCol = 2
        s1.cursorVisible = false

        val s2 = TuiSurface(rows = 3, cols = 3)
        s2.copyFrom(s1)

        assertEquals('Q', s2.getChar(1, 1))
        assertEquals(0x123456, s2.getFgRgb(1, 1))
        assertEquals(0x654321, s2.getBgRgb(1, 1))
        assertEquals(TuiCellStyle.ITALIC, s2.getStyle(1, 1))
        assertEquals(1, s2.cursorRow)
        assertEquals(2, s2.cursorCol)
        assertFalse(s2.cursorVisible)
    }
}
