package com.johnan.terminal.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.johnan.terminal.core.SelectionState
import com.johnan.terminal.core.TerminalCell
import kotlin.math.roundToInt

/**
 * Attaches pointer gesture recognizers for touch selection placement and long-press handle dragging.
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
    val currentOnSingleTap = rememberUpdatedState(onSingleTap)
    val currentOnStartDraggingStartCursor = rememberUpdatedState(onStartDraggingStartCursor)
    val currentOnUpdateStartCursor = rememberUpdatedState(onUpdateStartCursor)
    val currentOnFinalizeStartCursor = rememberUpdatedState(onFinalizeStartCursor)
    val currentOnStartDraggingEndCursor = rememberUpdatedState(onStartDraggingEndCursor)
    val currentOnUpdateEndCursor = rememberUpdatedState(onUpdateEndCursor)
    val currentOnFinalizeEndCursor = rememberUpdatedState(onFinalizeEndCursor)
    val currentSelectionState = rememberUpdatedState(selectionState)
    val currentAllRows = rememberUpdatedState(allRows)

    val currentTerminalCols = rememberUpdatedState(terminalCols)
    val currentCellWidth = rememberUpdatedState(cellWidth)
    val currentCellHeight = rememberUpdatedState(cellHeight)
    val currentScrollbackLineCount = rememberUpdatedState(scrollbackLineCount)
    val currentVerticalOffset = rememberUpdatedState(verticalOffset)

    return this
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
        .pointerInput(Unit) {
            var isDraggingStart = false
            var isDraggingEnd = false

            var initialCursorRow = 0
            var initialCursorCol = 0
            var totalDragDistance = Offset.Zero

            detectDragGesturesAfterLongPress(
                onDragStart = { _ ->
                    val state = currentSelectionState.value
                    totalDragDistance = Offset.Zero

                    when (state) {
                        is SelectionState.StartCursorPlaced -> {
                            isDraggingStart = true
                            isDraggingEnd = false
                            initialCursorRow = state.startRow
                            initialCursorCol = state.startCol
                            currentOnStartDraggingStartCursor.value(initialCursorRow, initialCursorCol)
                        }
                        is SelectionState.SelectionComplete -> {
                            isDraggingStart = false
                            isDraggingEnd = true
                            initialCursorRow = state.selection.endRow
                            initialCursorCol = state.selection.endCol
                            currentOnStartDraggingEndCursor.value(initialCursorRow, initialCursorCol)
                        }
                        else -> {
                            isDraggingStart = false
                            isDraggingEnd = false
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    if (isDraggingStart || isDraggingEnd) {
                        change.consume()

                        totalDragDistance += dragAmount

                        val rowDelta = (totalDragDistance.y / currentCellHeight.value).roundToInt()
                        val colDelta = (totalDragDistance.x / currentCellWidth.value).roundToInt()

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
