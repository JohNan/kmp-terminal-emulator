package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalSelectionLogicTest {
    @Test
    fun testTapSequenceTransitions() {
        val initial = SelectionState.CopyModeActive

        // First tap places start cursor
        val placed = TerminalSelectionLogic.onSingleTap(initial, 3, 5)
        assertTrue(placed is SelectionState.StartCursorPlaced)
        assertEquals(3, placed.startRow)
        assertEquals(5, placed.startCol)

        // Second tap completes selection
        val complete = TerminalSelectionLogic.onSingleTap(placed, 8, 20)
        assertTrue(complete is SelectionState.SelectionComplete)
        assertEquals(3, complete.selection.startRow)
        assertEquals(5, complete.selection.startCol)
        assertEquals(8, complete.selection.endRow)
        assertEquals(20, complete.selection.endCol)

        // Third tap clears selection
        val cleared = TerminalSelectionLogic.onSingleTap(complete, 1, 1)
        assertEquals(SelectionState.None, cleared)
    }

    @Test
    fun testSelectAllCalculatesFullBuffer() {
        val buffer = ScreenBuffer(initialRows = 10, initialCols = 40)
        // Push 5 lines into scrollback by writing and feeding
        for (i in 0 until 15) {
            buffer.writeChar('A')
            buffer.lineFeed()
            buffer.carriageReturn()
        }

        val selectionState = TerminalSelectionLogic.selectAll(buffer)
        assertTrue(selectionState is SelectionState.SelectionComplete)
        val sel = selectionState.selection
        assertEquals(0, sel.startRow)
        assertEquals(0, sel.startCol)
        val expectedTotalRows = buffer.getScrollback().size + buffer.rows
        assertEquals(expectedTotalRows - 1, sel.endRow)
        assertEquals(39, sel.endCol)
    }

    @Test
    fun testSelectVisibleConstrainedToViewport() {
        val selectionState =
            TerminalSelectionLogic.selectVisible(
                firstVisibleRow = 5,
                visibleRowCount = 20,
                cols = 80,
            )
        assertTrue(selectionState is SelectionState.SelectionComplete)
        val sel = selectionState.selection
        assertEquals(5, sel.startRow)
        assertEquals(0, sel.startCol)
        assertEquals(24, sel.endRow)
        assertEquals(79, sel.endCol)
    }

    @Test
    fun testCursorDraggingLifecycle() {
        val placed = SelectionState.StartCursorPlaced(2, 3)

        // Drag start cursor
        val startDragging = TerminalSelectionLogic.startDraggingStartCursor(placed, 2, 3)
        assertTrue(startDragging is SelectionState.StartCursorDragging)

        val updatedStart = TerminalSelectionLogic.updateStartCursor(startDragging, 4, 7)
        assertTrue(updatedStart is SelectionState.StartCursorDragging)
        assertEquals(4, updatedStart.startRow)
        assertEquals(7, updatedStart.startCol)

        val finalizedStart = TerminalSelectionLogic.finalizeStartCursor(updatedStart)
        assertTrue(finalizedStart is SelectionState.StartCursorPlaced)
        assertEquals(4, finalizedStart.startRow)
        assertEquals(7, finalizedStart.startCol)

        // Drag end cursor
        val startDraggingEnd = TerminalSelectionLogic.startDraggingEndCursor(finalizedStart, 6, 12)
        assertTrue(startDraggingEnd is SelectionState.EndCursorDragging)
        assertEquals(4, startDraggingEnd.startRow)
        assertEquals(7, startDraggingEnd.startCol)
        assertEquals(6, startDraggingEnd.endRow)
        assertEquals(12, startDraggingEnd.endCol)

        val updatedEnd = TerminalSelectionLogic.updateEndCursor(startDraggingEnd, 9, 15)
        assertTrue(updatedEnd is SelectionState.EndCursorDragging)
        assertEquals(9, updatedEnd.endRow)
        assertEquals(15, updatedEnd.endCol)

        val finalizedEnd = TerminalSelectionLogic.finalizeEndCursor(updatedEnd)
        assertTrue(finalizedEnd is SelectionState.SelectionComplete)
        assertEquals(4, finalizedEnd.selection.startRow)
        assertEquals(7, finalizedEnd.selection.startCol)
        assertEquals(9, finalizedEnd.selection.endRow)
        assertEquals(15, finalizedEnd.selection.endCol)
    }
}
