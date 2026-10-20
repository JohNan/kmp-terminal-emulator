package com.johnan.terminal.ui.gesture

import com.johnan.terminal.ui.SelectionController
import com.johnan.terminal.ui.pixelToCellCoordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalGestureDetectorTest {
    @Test
    fun testSelectionControllerDefaultCallbacks() {
        val controller = SelectionController()
        // Ensure default no-op lambdas can be invoked safely without throwing
        controller.onUpdateStartCursor(0, 0)
        controller.onFinalizeStartCursor()
        controller.onStartDraggingEndCursor(1, 1)
        controller.onUpdateEndCursor(2, 2)
        controller.onFinalizeEndCursorAndCopy()
        controller.onClearSelection()
        controller.onStartDirectTouchSelection(3, 3)
    }

    @Test
    fun testSelectionControllerCustomOnStartDirectTouchSelection() {
        var recordedRow = -1
        var recordedCol = -1
        var wasInvoked = false

        val controller = SelectionController(
            onStartDirectTouchSelection = { row, col ->
                wasInvoked = true
                recordedRow = row
                recordedCol = col
            },
        )

        controller.onStartDirectTouchSelection(12, 34)
        assertTrue(wasInvoked, "onStartDirectTouchSelection should be invoked")
        assertEquals(12, recordedRow)
        assertEquals(34, recordedCol)
    }

    @Test
    fun testSelectionControllerAllCallbacksWired() {
        var startCursorUpdated = false
        var startCursorFinalized = false
        var endCursorDragStarted = false
        var endCursorUpdated = false
        var endCursorFinalizedAndCopied = false
        var selectionCleared = false
        var directTouchSelectionStarted = false

        val controller = SelectionController(
            onUpdateStartCursor = { _, _ -> startCursorUpdated = true },
            onFinalizeStartCursor = { startCursorFinalized = true },
            onStartDraggingEndCursor = { _, _ -> endCursorDragStarted = true },
            onUpdateEndCursor = { _, _ -> endCursorUpdated = true },
            onFinalizeEndCursorAndCopy = { endCursorFinalizedAndCopied = true },
            onClearSelection = { selectionCleared = true },
            onStartDirectTouchSelection = { _, _ -> directTouchSelectionStarted = true },
        )

        controller.onUpdateStartCursor(1, 2)
        controller.onFinalizeStartCursor()
        controller.onStartDraggingEndCursor(3, 4)
        controller.onUpdateEndCursor(5, 6)
        controller.onFinalizeEndCursorAndCopy()
        controller.onClearSelection()
        controller.onStartDirectTouchSelection(7, 8)

        assertTrue(startCursorUpdated)
        assertTrue(startCursorFinalized)
        assertTrue(endCursorDragStarted)
        assertTrue(endCursorUpdated)
        assertTrue(endCursorFinalizedAndCopied)
        assertTrue(selectionCleared)
        assertTrue(directTouchSelectionStarted)
    }

    @Test
    fun testPixelToCellCoordinatesWithZeroVerticalOffset() {
        val cellWidth = 10f
        val cellHeight = 20f
        val cols = 80
        val visibleRows = 24
        val scrollbackSize = 100

        // Origin at top-left
        val (col0, row0) = pixelToCellCoordinates(
            pixelX = 0f,
            pixelY = 0f,
            scrollOffset = 0f,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            verticalOffset = 0f,
            cols = cols,
            visibleRows = visibleRows,
            scrollbackSize = scrollbackSize,
        )
        assertEquals(0, col0)
        assertEquals(0, row0)

        // Middle of cell (col 5, row 2)
        val (col1, row1) = pixelToCellCoordinates(
            pixelX = 55f,
            pixelY = 45f,
            scrollOffset = 0f,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            verticalOffset = 0f,
            cols = cols,
            visibleRows = visibleRows,
            scrollbackSize = scrollbackSize,
        )
        assertEquals(5, col1)
        assertEquals(2, row1)
    }

    @Test
    fun testPixelToCellCoordinatesWithScrollOffset() {
        val cellWidth = 10f
        val cellHeight = 20f
        val cols = 80
        val visibleRows = 24
        val scrollbackSize = 100

        // Scrolled down by 50 lines (50 * 20 = 1000f px)
        val (col, row) = pixelToCellCoordinates(
            pixelX = 75f,
            pixelY = 40f,
            scrollOffset = 1000f,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            verticalOffset = 0f,
            cols = cols,
            visibleRows = visibleRows,
            scrollbackSize = scrollbackSize,
        )
        // 75 / 10 = 7
        assertEquals(7, col)
        // (40 + 1000) / 20 = 52
        assertEquals(52, row)
    }

    @Test
    fun testPixelToCellCoordinatesBoundaryClamping() {
        val cellWidth = 10f
        val cellHeight = 20f
        val cols = 80
        val visibleRows = 24
        val scrollbackSize = 100
        val totalRows = visibleRows + scrollbackSize

        // Negative coordinates clamp to (0, 0)
        val (negCol, negRow) = pixelToCellCoordinates(
            pixelX = -50f,
            pixelY = -100f,
            scrollOffset = 0f,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            verticalOffset = 0f,
            cols = cols,
            visibleRows = visibleRows,
            scrollbackSize = scrollbackSize,
        )
        assertEquals(0, negCol)
        assertEquals(0, negRow)

        // Beyond maximum coordinates clamp to (cols - 1, totalRows - 1)
        val (maxCol, maxRow) = pixelToCellCoordinates(
            pixelX = 5000f,
            pixelY = 50000f,
            scrollOffset = 0f,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            verticalOffset = 0f,
            cols = cols,
            visibleRows = visibleRows,
            scrollbackSize = scrollbackSize,
        )
        assertEquals(cols - 1, maxCol)
        assertEquals(totalRows - 1, maxRow)
    }

    @Test
    fun testPixelToCellCoordinatesZeroDimensionsSafety() {
        val (col, row) = pixelToCellCoordinates(
            pixelX = 100f,
            pixelY = 100f,
            scrollOffset = 0f,
            cellWidth = 0f,
            cellHeight = 0f,
            verticalOffset = 0f,
            cols = 0,
            visibleRows = 0,
            scrollbackSize = 0,
        )
        assertEquals(0, col)
        assertEquals(0, row)
    }
}
