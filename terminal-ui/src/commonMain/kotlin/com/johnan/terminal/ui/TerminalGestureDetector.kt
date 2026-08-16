package com.johnan.terminal.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.johnan.terminal.core.SelectionState
import com.johnan.terminal.core.TerminalCell
import kotlin.math.roundToInt

/**
 * Modifier that handles terminal gestures for copy mode.
 *
 * Supports:
 * - Single tap to place start cursor or move end cursor (depending on state)
 * - Long press & drag to refine start cursor position (relative movement)
 * - Long press & drag to refine end cursor position (relative movement)
 */
@Composable
fun Modifier.terminalGestures(
    selectionState: SelectionState,
    terminalCols: Int,
    allRows: List<Array<TerminalCell>>,
    cellWidth: Float,
    cellHeight: Float,
    scrollbackLineCount: Int,
    verticalOffset: Float,
    onSingleTap: (row: Int, col: Int) -> Unit,
    onStartDraggingStartCursor: (row: Int, col: Int) -> Unit,
    onUpdateStartCursor: (row: Int, col: Int) -> Unit,
    onFinalizeStartCursor: () -> Unit,
    onStartDraggingEndCursor: (row: Int, col: Int) -> Unit,
    onUpdateEndCursor: (row: Int, col: Int) -> Unit,
    onFinalizeEndCursor: () -> Unit,
    onExitCopyMode: () -> Unit,
): Modifier {
    val density = LocalDensity.current

    // Remember callbacks to avoid recomposition issues within the gesture detector
    val currentOnSingleTap = rememberUpdatedState(onSingleTap)
    val currentOnStartDraggingStartCursor = rememberUpdatedState(onStartDraggingStartCursor)
    val currentOnUpdateStartCursor = rememberUpdatedState(onUpdateStartCursor)
    val currentOnFinalizeStartCursor = rememberUpdatedState(onFinalizeStartCursor)
    val currentOnStartDraggingEndCursor = rememberUpdatedState(onStartDraggingEndCursor)
    val currentOnUpdateEndCursor = rememberUpdatedState(onUpdateEndCursor)
    val currentOnFinalizeEndCursor = rememberUpdatedState(onFinalizeEndCursor)
    val currentOnExitCopyMode = rememberUpdatedState(onExitCopyMode)
    val currentSelectionState = rememberUpdatedState(selectionState)
    val currentAllRows = rememberUpdatedState(allRows)

    // Remember dynamic parameters
    val currentTerminalCols = rememberUpdatedState(terminalCols)
    val currentCellWidth = rememberUpdatedState(cellWidth)
    val currentCellHeight = rememberUpdatedState(cellHeight)
    val currentScrollbackLineCount = rememberUpdatedState(scrollbackLineCount)
    val currentVerticalOffset = rememberUpdatedState(verticalOffset)

    return this
        // Tap handler
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    val (row, col) =
                        TerminalCoordinateConverter.screenToTerminal(
                            offset = offset,
                            cellWidth = currentCellWidth.value,
                            cellHeight = currentCellHeight.value,
                            scrollbackLineCount = currentScrollbackLineCount.value,
                            totalRows = currentAllRows.value.size,
                            cols = currentTerminalCols.value,
                            verticalOffset = currentVerticalOffset.value,
                        )
                    currentOnSingleTap.value(row, col)
                },
            )
        }
        // Long press and drag handler (Relative Movement)
        .pointerInput(Unit) {
            var isDraggingStart = false
            var isDraggingEnd = false

            // State for relative dragging
            var initialCursorRow = 0
            var initialCursorCol = 0
            var totalDragDistance = Offset.Zero

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val state = currentSelectionState.value
                    totalDragDistance = Offset.Zero

                    // Determine which cursor to drag based on PHASE
                    when (state) {
                        is SelectionState.StartCursorPlaced -> {
                            isDraggingStart = true
                            isDraggingEnd = false
                            initialCursorRow = state.startRow
                            initialCursorCol = state.startCol

                            // Notify start of drag (this keeps the state consistent, though we won't snap to touch)
                            currentOnStartDraggingStartCursor.value(initialCursorRow, initialCursorCol)
                        }
                        is SelectionState.SelectionComplete -> {
                            // "End Placed" -> Refine End Cursor
                            isDraggingStart = false
                            isDraggingEnd = true
                            initialCursorRow = state.selection.endRow
                            initialCursorCol = state.selection.endCol

                            // Notify start of drag
                            currentOnStartDraggingEndCursor.value(initialCursorRow, initialCursorCol)
                        }
                        else -> {
                            // Other states (CopyModeActive, or already dragging)
                            // If we are already dragging, we shouldn't be here (new gesture).
                            // If CopyModeActive, long press does nothing.
                            isDraggingStart = false
                            isDraggingEnd = false
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    if (isDraggingStart || isDraggingEnd) {
                        change.consume()

                        // Accumulate drag distance
                        totalDragDistance += dragAmount

                        // Calculate delta in rows/cols
                        val rowDelta = (totalDragDistance.y / currentCellHeight.value).roundToInt()
                        val colDelta = (totalDragDistance.x / currentCellWidth.value).roundToInt()

                        // Apply delta to initial position
                        val newRow = (initialCursorRow + rowDelta).coerceIn(0, currentAllRows.value.size - 1)
                        val newCol = (initialCursorCol + colDelta).coerceIn(0, currentTerminalCols.value - 1)

                        if (isDraggingStart) {
                            currentOnUpdateStartCursor.value(newRow, newCol)
                        } else {
                            currentOnUpdateEndCursor.value(newRow, newCol)
                        }
                    }
                },
                onDragEnd = {
                    if (isDraggingStart) {
                        currentOnFinalizeStartCursor.value()
                    } else if (isDraggingEnd) {
                        currentOnFinalizeEndCursor.value()
                    }
                    isDraggingStart = false
                    isDraggingEnd = false
                },
                onDragCancel = {
                    if (isDraggingStart) {
                        currentOnFinalizeStartCursor.value()
                    } else if (isDraggingEnd) {
                        currentOnFinalizeEndCursor.value()
                    }
                    isDraggingStart = false
                    isDraggingEnd = false
                },
            )
        }
}
