package com.johnan.terminal.ui.gesture

import com.johnan.terminal.core.MouseButton
import com.johnan.terminal.core.MouseEvent
import com.johnan.terminal.core.PointerMouseEvent
import com.johnan.terminal.core.TerminalCell
import com.johnan.terminal.ui.MAX_WHEEL_STEPS_PER_EVENT
import com.johnan.terminal.ui.SelectionController
import com.johnan.terminal.ui.emitPointerMouseEvent
import com.johnan.terminal.ui.pixelToCellCoordinates
import com.johnan.terminal.ui.resolveMouseClickSelection
import com.johnan.terminal.ui.resolveMouseWheelSteps
import com.johnan.terminal.ui.selectCellRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalGestureDetectorTest {
    @Test
    fun testSelectionControllerDefaultCallbacks() {
        val controller = SelectionController()
        // Ensure default no-op lambdas can be invoked safely without throwing
        controller.onStartDraggingStartCursor(0, 0)
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
        var startCursorDragStarted = false
        var startCursorUpdated = false
        var startCursorFinalized = false
        var endCursorDragStarted = false
        var endCursorUpdated = false
        var endCursorFinalizedAndCopied = false
        var selectionCleared = false
        var directTouchSelectionStarted = false

        val controller = SelectionController(
            onStartDraggingStartCursor = { _, _ -> startCursorDragStarted = true },
            onUpdateStartCursor = { _, _ -> startCursorUpdated = true },
            onFinalizeStartCursor = { startCursorFinalized = true },
            onStartDraggingEndCursor = { _, _ -> endCursorDragStarted = true },
            onUpdateEndCursor = { _, _ -> endCursorUpdated = true },
            onFinalizeEndCursorAndCopy = { endCursorFinalizedAndCopied = true },
            onClearSelection = { selectionCleared = true },
            onStartDirectTouchSelection = { _, _ -> directTouchSelectionStarted = true },
        )

        controller.onStartDraggingStartCursor(0, 1)
        controller.onUpdateStartCursor(1, 2)
        controller.onFinalizeStartCursor()
        controller.onStartDraggingEndCursor(3, 4)
        controller.onUpdateEndCursor(5, 6)
        controller.onFinalizeEndCursorAndCopy()
        controller.onClearSelection()
        controller.onStartDirectTouchSelection(7, 8)

        assertTrue(startCursorDragStarted)
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

    @Test
    fun testSelectionControllerOnSelectRangeDefaultAndCustom() {
        val defaultController = SelectionController()
        assertNull(defaultController.onSelectRange)

        var recordedStartRow = -1
        var recordedStartCol = -1
        var recordedEndRow = -1
        var recordedEndCol = -1
        val customController = SelectionController(
            onSelectRange = { sr, sc, er, ec ->
                recordedStartRow = sr
                recordedStartCol = sc
                recordedEndRow = er
                recordedEndCol = ec
            },
        )

        customController.onSelectRange?.invoke(2, 4, 2, 11)
        assertEquals(2, recordedStartRow)
        assertEquals(4, recordedStartCol)
        assertEquals(2, recordedEndRow)
        assertEquals(11, recordedEndCol)
    }

    @Test
    fun testResolveMouseWheelStepsDirectionScalingAndClamping() {
        assertNull(resolveMouseWheelSteps(0f, 1.0f, 3))
        assertNull(resolveMouseWheelSteps(-0f, 1.0f, 3))
        assertNull(resolveMouseWheelSteps(Float.NaN, 1.0f, 3))
        assertNull(resolveMouseWheelSteps(Float.POSITIVE_INFINITY, 1.0f, 3))
        assertNull(resolveMouseWheelSteps(Float.NEGATIVE_INFINITY, 1.0f, 3))
        assertNull(resolveMouseWheelSteps(1.0f, 0f, 3))
        assertNull(resolveMouseWheelSteps(-1.0f, -1.0f, 3))
        assertNull(resolveMouseWheelSteps(1.0f, Float.NaN, 3))
        assertNull(resolveMouseWheelSteps(1.0f, Float.POSITIVE_INFINITY, 3))

        assertEquals(MouseEvent.WheelDown to 3, resolveMouseWheelSteps(1.0f, 1.0f, 3))
        assertEquals(MouseEvent.WheelUp to 9, resolveMouseWheelSteps(-2.0f, 1.5f, 3))
        assertEquals(MouseEvent.WheelDown to 1, resolveMouseWheelSteps(0.1f, 1.0f, 1))
        assertEquals(MouseEvent.WheelUp to 1, resolveMouseWheelSteps(-1.0f, 1.0f, 0))
        assertEquals(
            MouseEvent.WheelDown to MAX_WHEEL_STEPS_PER_EVENT,
            resolveMouseWheelSteps(500.0f, 1.0f, 3),
        )
    }

    @Test
    fun testResolveMouseClickSelectionWordLineAndSingleClick() {
        val rowText = "echo /tmp/out.log --flag"
        val rowCells = Array(rowText.length) { i -> TerminalCell.create(rowText[i]) }

        // Single click (or clickCount < 2) returns null
        assertNull(resolveMouseClickSelection(clickCount = 0, row = 0, col = 1, rowCells = rowCells, cols = 80))
        assertNull(resolveMouseClickSelection(clickCount = 1, row = 0, col = 1, rowCells = rowCells, cols = 80))

        // Double click resolves word / path bounds
        assertEquals(
            0..3,
            resolveMouseClickSelection(clickCount = 2, row = 0, col = 1, rowCells = rowCells, cols = 80),
        )
        assertEquals(
            5..16,
            resolveMouseClickSelection(clickCount = 2, row = 0, col = 8, rowCells = rowCells, cols = 80),
        )
        assertNull(resolveMouseClickSelection(clickCount = 2, row = 0, col = 0, rowCells = null, cols = 80))
        assertNull(resolveMouseClickSelection(clickCount = 2, row = 0, col = 0, rowCells = emptyArray(), cols = 80))

        // Triple click (or higher) resolves entire row column range
        assertEquals(
            0..79,
            resolveMouseClickSelection(clickCount = 3, row = 4, col = 10, rowCells = rowCells, cols = 80),
        )
        assertEquals(
            0..79,
            resolveMouseClickSelection(clickCount = 4, row = 4, col = 10, rowCells = null, cols = 80),
        )
        assertEquals(
            0..0,
            resolveMouseClickSelection(clickCount = 3, row = 0, col = 0, rowCells = rowCells, cols = 0),
        )
    }

    @Test
    fun testSelectCellRangeUsesOnSelectRangeAndFallback() {
        var selectRangeCalled = false
        var fallbackCalled = false
        var rangeArgs: List<Int> = emptyList()

        val controllerWithRange = SelectionController(
            onStartDirectTouchSelection = { _, _ -> fallbackCalled = true },
            onUpdateEndCursor = { _, _ -> fallbackCalled = true },
            onFinalizeEndCursorAndCopy = { fallbackCalled = true },
            onSelectRange = { sr, sc, er, ec ->
                selectRangeCalled = true
                rangeArgs = listOf(sr, sc, er, ec)
            },
        )

        selectCellRange(controllerWithRange, row = 3, startCol = 5, endCol = 16)
        assertTrue(selectRangeCalled)
        assertFalse(fallbackCalled)
        assertEquals(listOf(3, 5, 3, 16), rangeArgs)

        val events = mutableListOf<String>()
        val fallbackController = SelectionController(
            onStartDirectTouchSelection = { r, c -> events.add("start:$r,$c") },
            onUpdateEndCursor = { r, c -> events.add("update:$r,$c") },
            onFinalizeEndCursorAndCopy = { events.add("finalize") },
        )

        selectCellRange(fallbackController, row = 7, startCol = 0, endCol = 79)
        assertEquals(listOf("start:7,0", "update:7,79", "finalize"), events)
    }

    @Test
    fun testEmitPointerMouseEventLegacyFallbackForwardsLeftAndWheelOnly() {
        val legacyEvents = mutableListOf<Triple<MouseEvent, Int, Int>>()
        val onMouseEvent: (MouseEvent, Int, Int) -> Unit = { type, row, col ->
            legacyEvents.add(Triple(type, row, col))
        }

        emitPointerMouseEvent(null, onMouseEvent, MouseEvent.Press, MouseButton.LEFT, 1, 2, false, false, false)
        emitPointerMouseEvent(null, onMouseEvent, MouseEvent.Drag, MouseButton.LEFT, 3, 4, false, false, false)
        emitPointerMouseEvent(null, onMouseEvent, MouseEvent.Release, MouseButton.LEFT, 3, 4, false, false, false)
        emitPointerMouseEvent(null, onMouseEvent, MouseEvent.WheelUp, MouseButton.NONE, 5, 6, false, false, false)
        emitPointerMouseEvent(null, onMouseEvent, MouseEvent.WheelDown, MouseButton.NONE, 7, 8, false, false, false)
        emitPointerMouseEvent(null, onMouseEvent, MouseEvent.Press, MouseButton.RIGHT, 9, 10, false, false, false)
        emitPointerMouseEvent(null, onMouseEvent, MouseEvent.Press, MouseButton.MIDDLE, 11, 12, false, false, false)

        assertEquals(
            listOf(
                Triple(MouseEvent.Press, 1, 2),
                Triple(MouseEvent.Drag, 3, 4),
                Triple(MouseEvent.Release, 3, 4),
                Triple(MouseEvent.WheelUp, 5, 6),
                Triple(MouseEvent.WheelDown, 7, 8),
            ),
            legacyEvents,
        )

        val richEvents = mutableListOf<PointerMouseEvent>()
        val unusedLegacy = mutableListOf<Triple<MouseEvent, Int, Int>>()
        emitPointerMouseEvent(
            onPointerMouseEvent = { richEvents.add(it) },
            onMouseEvent = { type, row, col -> unusedLegacy.add(Triple(type, row, col)) },
            type = MouseEvent.Press,
            button = MouseButton.RIGHT,
            row = 2,
            col = 9,
            shift = true,
            alt = false,
            ctrl = true,
        )
        assertTrue(unusedLegacy.isEmpty())
        assertEquals(
            listOf(
                PointerMouseEvent(
                    type = MouseEvent.Press,
                    button = MouseButton.RIGHT,
                    row = 2,
                    col = 9,
                    shiftPressed = true,
                    altPressed = false,
                    ctrlPressed = true,
                ),
            ),
            richEvents,
        )
    }
}
