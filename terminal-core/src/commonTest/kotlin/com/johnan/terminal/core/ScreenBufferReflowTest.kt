package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenBufferReflowTest {
    @Test
    fun testAutoWrapMarksRowAsWrapped() {
        val buffer = ScreenBuffer(initialRows = 5, initialCols = 10)
        buffer.writeText("0123456789X")
        assertTrue(buffer.getTerminalRow(0).isWrapped, "Row 0 should be marked as wrapped")
        assertFalse(buffer.getTerminalRow(1).isWrapped, "Row 1 should not be wrapped")
        assertEquals('X', buffer.getTerminalRow(1).cells[0].char)
    }

    @Test
    fun testExplicitNewlineDoesNotMarkWrapped() {
        val buffer = ScreenBuffer(initialRows = 5, initialCols = 80)
        buffer.writeText("Hello World")
        buffer.carriageReturn()
        buffer.lineFeed()
        assertFalse(buffer.getTerminalRow(0).isWrapped, "Row 0 should not be marked as wrapped on explicit newline")
    }

    @Test
    fun testNarrowingReflowsWrappedLines() {
        val buffer = ScreenBuffer(initialRows = 24, initialCols = 80)
        buffer.setTextAttributes(bold = true)
        val testText = "A".repeat(60)
        buffer.writeText(testText)

        buffer.resize(24, 30)

        val row0 = buffer.getTerminalRow(0)
        val row1 = buffer.getTerminalRow(1)

        assertTrue(row0.isWrapped, "Row 0 should be marked as wrapped")
        assertFalse(row1.isWrapped, "Row 1 should not be marked as wrapped")

        val row0Text = row0.cells.take(30).map { it.char }.joinToString("")
        val row1Text = row1.cells.take(30).map { it.char }.joinToString("")
        assertEquals("A".repeat(30), row0Text)
        assertEquals("A".repeat(30), row1Text)

        for (i in 0 until 30) {
            assertTrue(row0.cells[i].bold, "Row 0 cell $i should preserve bold attribute")
            assertTrue(row1.cells[i].bold, "Row 1 cell $i should preserve bold attribute")
        }
    }

    @Test
    fun testWideningUnwrapsLines() {
        val buffer = ScreenBuffer(initialRows = 24, initialCols = 30)
        val text = "B".repeat(60)
        buffer.writeText(text)

        assertTrue(buffer.getTerminalRow(0).isWrapped, "Precondition: Row 0 is wrapped")
        assertFalse(buffer.getTerminalRow(1).isWrapped, "Precondition: Row 1 is not wrapped")

        buffer.resize(24, 80)

        val row0 = buffer.getTerminalRow(0)
        assertFalse(row0.isWrapped, "Row 0 should unwrap and not be marked as wrapped")
        val unwrappedText = row0.cells.take(60).map { it.char }.joinToString("")
        assertEquals(text, unwrappedText, "Text should unwrap into a single row of 60 chars")
    }

    @Test
    fun testCursorTranslationAcrossReflow() {
        val buffer = ScreenBuffer(initialRows = 24, initialCols = 80)
        buffer.writeText("C".repeat(60))
        buffer.setCursorPosition(row = 0, col = 55)

        buffer.resize(24, 30)

        assertEquals(1, buffer.cursorRow, "Cursor row should translate across reflow")
        assertEquals(25, buffer.cursorCol, "Cursor col should translate across reflow")
    }

    @Test
    fun testScrollbackOverflowOnNarrowing() {
        val buffer = ScreenBuffer(initialRows = 4, initialCols = 80)
        buffer.writeText("1".repeat(60))
        buffer.carriageReturn()
        buffer.lineFeed()
        buffer.writeText("2".repeat(60))
        buffer.carriageReturn()
        buffer.lineFeed()
        buffer.writeText("3".repeat(30))
        buffer.carriageReturn()
        buffer.lineFeed()
        buffer.writeText("4".repeat(30))

        assertEquals(4, buffer.rows)
        assertEquals(0, buffer.getScrollback().size)

        buffer.resize(4, 30)

        assertEquals(4, buffer.rows)
        val scrollback = buffer.getScrollback()
        assertEquals(2, scrollback.size, "Excess 2 rows should push into scrollback")
        val scrollbackRow0 = scrollback[0].take(30).map { it.char }.joinToString("")
        val scrollbackRow1 = scrollback[1].take(30).map { it.char }.joinToString("")
        assertEquals("1".repeat(30), scrollbackRow0)
        assertEquals("1".repeat(30), scrollbackRow1)
    }

    @Test
    fun testAlternateBufferBypassesReflow() {
        val buffer = ScreenBuffer(initialRows = 10, initialCols = 80)
        buffer.useAlternateScreen()
        assertTrue(buffer.isUsingAlternateScreen())

        buffer.setCursorPosition(row = 0, col = 0)
        buffer.writeText("GRID-HEADER")
        buffer.setCursorPosition(row = 0, col = 40)
        buffer.writeText("COL-40")

        buffer.resize(10, 30)

        val row0 = buffer.getTerminalRow(0)
        assertFalse(row0.isWrapped, "Alternate buffer should not wrap rows")
        val visibleText = row0.cells.take(11).map { it.char }.joinToString("")
        assertEquals("GRID-HEADER", visibleText)
        val row1 = buffer.getTerminalRow(1)
        assertTrue(row1.isEmpty(), "Row 1 should remain empty without reflow")
    }
}
