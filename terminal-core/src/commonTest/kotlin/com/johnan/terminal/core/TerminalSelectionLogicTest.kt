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
    fun testSelectAllAndVisibleWithTerminalScreenState() {
        val buffer = ScreenBuffer(initialRows = 10, initialCols = 40)
        for (i in 0 until 15) {
            buffer.writeChar('B')
            buffer.lineFeed()
            buffer.carriageReturn()
        }
        val screenState = TerminalScreenState.from(buffer)

        val allSelection = TerminalSelectionLogic.selectAll(screenState)
        assertTrue(allSelection is SelectionState.SelectionComplete)
        assertEquals(0, allSelection.selection.startRow)
        assertEquals(0, allSelection.selection.startCol)
        assertEquals(screenState.scrollback.size + screenState.rows.size - 1, allSelection.selection.endRow)
        assertEquals(39, allSelection.selection.endCol)

        val visibleSelection = TerminalSelectionLogic.selectVisible(screenState)
        assertTrue(visibleSelection is SelectionState.SelectionComplete)
        assertEquals(screenState.scrollback.size, visibleSelection.selection.startRow)
        assertEquals(0, visibleSelection.selection.startCol)
        assertEquals(screenState.scrollback.size + screenState.rows.size - 1, visibleSelection.selection.endRow)
        assertEquals(39, visibleSelection.selection.endCol)
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

    @Test
    fun testFindWordBoundsWordsPathsAndUrls() {
        val pathCells = cellsFromString("cat /home/user-1/my_file.txt --flag")
        assertEquals(0..2, TerminalSelectionLogic.findWordBounds(pathCells, 0))
        assertEquals(0..2, TerminalSelectionLogic.findWordBounds(pathCells, 2))
        assertEquals(4..27, TerminalSelectionLogic.findWordBounds(pathCells, 4))
        assertEquals(4..27, TerminalSelectionLogic.findWordBounds(pathCells, 10))
        assertEquals(4..27, TerminalSelectionLogic.findWordBounds(pathCells, 27))
        assertEquals(29..34, TerminalSelectionLogic.findWordBounds(pathCells, 31))

        val tildeCells = cellsFromString("~/docs/readme.md")
        assertEquals(0..15, TerminalSelectionLogic.findWordBounds(tildeCells, 0))

        val urlCells = cellsFromString("open https://example.com:8080/v1/api?q=1")
        assertEquals(0..3, TerminalSelectionLogic.findWordBounds(urlCells, 1))
        assertEquals(5..35, TerminalSelectionLogic.findWordBounds(urlCells, 15))
        assertEquals(36..36, TerminalSelectionLogic.findWordBounds(urlCells, 36))
        assertEquals(37..37, TerminalSelectionLogic.findWordBounds(urlCells, 37))
        assertEquals(38..38, TerminalSelectionLogic.findWordBounds(urlCells, 38))
        assertEquals(39..39, TerminalSelectionLogic.findWordBounds(urlCells, 39))
    }

    @Test
    fun testFindWordBoundsWhitespacePunctuationEmptyAndClamping() {
        val mixedCells = cellsFromString("foo   \t\u0000 bar === (b) !!")
        assertEquals(3..8, TerminalSelectionLogic.findWordBounds(mixedCells, 3))
        assertEquals(3..8, TerminalSelectionLogic.findWordBounds(mixedCells, 6))
        assertEquals(3..8, TerminalSelectionLogic.findWordBounds(mixedCells, 7))
        assertEquals(13..15, TerminalSelectionLogic.findWordBounds(mixedCells, 14))
        assertEquals(17..17, TerminalSelectionLogic.findWordBounds(mixedCells, 17))
        assertEquals(21..22, TerminalSelectionLogic.findWordBounds(mixedCells, 21))

        assertEquals(IntRange.EMPTY, TerminalSelectionLogic.findWordBounds(emptyArray(), 0))
        assertEquals(IntRange.EMPTY, TerminalSelectionLogic.findWordBounds(emptyArray(), -1))

        val clampedCells = cellsFromString("abc def")
        assertEquals(0..2, TerminalSelectionLogic.findWordBounds(clampedCells, -10))
        assertEquals(4..6, TerminalSelectionLogic.findWordBounds(clampedCells, 99))
    }

    @Test
    fun testSelectWordAndSelectLine() {
        val emptySelection = TerminalSelectionLogic.selectWord(row = 2, col = 0, rowCells = emptyArray())
        assertEquals(SelectionState.None, emptySelection)

        val rowCells = cellsFromString("echo /tmp/out.log")
        val wordSelection = TerminalSelectionLogic.selectWord(row = 4, col = 8, rowCells = rowCells)
        assertTrue(wordSelection is SelectionState.SelectionComplete)
        assertEquals(TerminalSelection(startRow = 4, startCol = 5, endRow = 4, endCol = 16), wordSelection.selection)

        val lineSelection = TerminalSelectionLogic.selectLine(row = 7, cols = 80)
        assertTrue(lineSelection is SelectionState.SelectionComplete)
        assertEquals(TerminalSelection(startRow = 7, startCol = 0, endRow = 7, endCol = 79), lineSelection.selection)

        val singleColLine = TerminalSelectionLogic.selectLine(row = 2, cols = 1)
        assertTrue(singleColLine is SelectionState.SelectionComplete)
        assertEquals(TerminalSelection(startRow = 2, startCol = 0, endRow = 2, endCol = 0), singleColLine.selection)

        val zeroColLine = TerminalSelectionLogic.selectLine(row = 0, cols = 0)
        assertTrue(zeroColLine is SelectionState.SelectionComplete)
        assertEquals(TerminalSelection(startRow = 0, startCol = 0, endRow = 0, endCol = 0), zeroColLine.selection)
    }

    private fun cellsFromString(text: String): Array<TerminalCell> =
        Array(text.length) { index -> TerminalCell.create(text[index]) }
}
