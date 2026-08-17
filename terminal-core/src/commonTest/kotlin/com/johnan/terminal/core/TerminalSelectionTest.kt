package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalSelectionTest {

    private fun writeStringToBuffer(buffer: ScreenBuffer, row: Int, col: Int, text: String) {
        buffer.setCursorPosition(row, col)
        text.forEach { buffer.writeChar(it) }
    }

    @Test
    fun testTerminalSelectionNormalization() {
        // Forward selection - unchanged
        val forward = TerminalSelection(startRow = 2, startCol = 5, endRow = 4, endCol = 10)
        val normForward = forward.normalized()
        assertEquals(2, normForward.startRow)
        assertEquals(5, normForward.startCol)
        assertEquals(4, normForward.endRow)
        assertEquals(10, normForward.endCol)

        // Backward row selection - inverted
        val backwardRow = TerminalSelection(startRow = 5, startCol = 10, endRow = 2, endCol = 3)
        val normBackwardRow = backwardRow.normalized()
        assertEquals(2, normBackwardRow.startRow)
        assertEquals(3, normBackwardRow.startCol)
        assertEquals(5, normBackwardRow.endRow)
        assertEquals(10, normBackwardRow.endCol)

        // Same row backward column selection - inverted
        val backwardCol = TerminalSelection(startRow = 3, startCol = 15, endRow = 3, endCol = 5)
        val normBackwardCol = backwardCol.normalized()
        assertEquals(3, normBackwardCol.startRow)
        assertEquals(5, normBackwardCol.startCol)
        assertEquals(3, normBackwardCol.endRow)
        assertEquals(15, normBackwardCol.endCol)
    }

    @Test
    fun testTerminalSelectionContains() {
        val selection = TerminalSelection(startRow = 2, startCol = 5, endRow = 4, endCol = 10)

        // Before start row
        assertFalse(selection.contains(1, 10))

        // After end row
        assertFalse(selection.contains(5, 0))

        // Start row before start col
        assertFalse(selection.contains(2, 4))
        // Start row at/after start col
        assertTrue(selection.contains(2, 5))
        assertTrue(selection.contains(2, 20))

        // Middle row (entire row is selected)
        assertTrue(selection.contains(3, 0))
        assertTrue(selection.contains(3, 50))

        // End row at/before end col
        assertTrue(selection.contains(4, 0))
        assertTrue(selection.contains(4, 10))
        // End row after end col
        assertFalse(selection.contains(4, 11))

        // Single row selection
        val singleRow = TerminalSelection(startRow = 1, startCol = 3, endRow = 1, endCol = 8)
        assertFalse(singleRow.contains(1, 2))
        assertTrue(singleRow.contains(1, 3))
        assertTrue(singleRow.contains(1, 5))
        assertTrue(singleRow.contains(1, 8))
        assertFalse(singleRow.contains(1, 9))
    }

    @Test
    fun testExtractTextSingleLine() {
        val buffer = ScreenBuffer(initialRows = 5, initialCols = 20)
        writeStringToBuffer(buffer, 0, 0, "Hello World")

        val selection = TerminalSelection(startRow = 0, startCol = 0, endRow = 0, endCol = 4)
        val extracted = selection.extractText(buffer)
        assertEquals("Hello", extracted)

        val subSelection = TerminalSelection(startRow = 0, startCol = 6, endRow = 0, endCol = 10)
        assertEquals("World", subSelection.extractText(buffer))
    }

    @Test
    fun testExtractTextMultiLine() {
        val buffer = ScreenBuffer(initialRows = 5, initialCols = 20)
        writeStringToBuffer(buffer, 0, 0, "Line One")
        writeStringToBuffer(buffer, 1, 0, "Line Two")
        writeStringToBuffer(buffer, 2, 0, "Line Three")

        val selection = TerminalSelection(startRow = 0, startCol = 5, endRow = 2, endCol = 3)
        val extracted = selection.extractText(buffer)
        val lines = extracted.split("\n")

        assertEquals(3, lines.size)
        assertEquals("One", lines[0])
        assertEquals("Line Two", lines[1])
        assertEquals("Line", lines[2])
    }

    @Test
    fun testExtractTextWithScrollback() = kotlinx.coroutines.test.runTest {
        val emulator = TerminalEmulator(rows = 3, cols = 20)
        emulator.processOutput("Line 1\r\nLine 2\r\nLine 3\r\nLine 4\r\n")

        // Terminal has scrolled so scrollback contains scrolled lines
        val screenBuffer = emulator.screenState.value
        assertTrue(screenBuffer.scrollback.isNotEmpty())
    }

    @Test
    fun testExtractTextOutOfBoundsReturnsEmpty() {
        val buffer = ScreenBuffer(initialRows = 2, initialCols = 10)
        val outOfBoundsNegative = TerminalSelection(startRow = -1, startCol = 0, endRow = 0, endCol = 5)
        assertEquals("", outOfBoundsNegative.extractText(buffer))

        val outOfBoundsPastEnd = TerminalSelection(startRow = 0, startCol = 0, endRow = 10, endCol = 5)
        assertEquals("", outOfBoundsPastEnd.extractText(buffer))
    }

    @Test
    fun testSelectionStateToTerminalSelection() {
        assertNull(SelectionState.None.toTerminalSelection())
        assertNull(SelectionState.CopyModeActive.toTerminalSelection())

        val placed = SelectionState.StartCursorPlaced(startRow = 1, startCol = 2)
        assertEquals(TerminalSelection(1, 2, 1, 2), placed.toTerminalSelection())

        val startDragging = SelectionState.StartCursorDragging(startRow = 3, startCol = 4)
        assertEquals(TerminalSelection(3, 4, 3, 4), startDragging.toTerminalSelection())

        val endDragging = SelectionState.EndCursorDragging(startRow = 1, startCol = 2, endRow = 5, endCol = 8)
        assertEquals(TerminalSelection(1, 2, 5, 8), endDragging.toTerminalSelection())

        val complete = SelectionState.SelectionComplete(selection = TerminalSelection(2, 0, 4, 10))
        assertEquals(TerminalSelection(2, 0, 4, 10), complete.toTerminalSelection())
    }

    @Test
    fun testActiveCursorEnum() {
        assertEquals(2, ActiveCursor.entries.size)
        assertTrue(ActiveCursor.entries.contains(ActiveCursor.START))
        assertTrue(ActiveCursor.entries.contains(ActiveCursor.END))
    }
}
