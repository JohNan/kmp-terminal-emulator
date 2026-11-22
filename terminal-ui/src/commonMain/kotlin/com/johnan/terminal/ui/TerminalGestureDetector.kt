package com.johnan.terminal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.johnan.terminal.core.SelectionState
import com.johnan.terminal.core.TerminalCell
import kotlin.math.roundToInt

/**
 * Controller encapsulating text selection gesture callbacks.
 */
@Immutable
class SelectionController(
    val onStartDraggingStartCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    val onUpdateStartCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    val onFinalizeStartCursor: () -> Unit = {},
    val onStartDraggingEndCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    val onUpdateEndCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    val onFinalizeEndCursorAndCopy: () -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onStartDirectTouchSelection: (row: Int, col: Int) -> Unit = { _, _ -> },
    val onSelectRange: ((startRow: Int, startCol: Int, endRow: Int, endCol: Int) -> Unit)? = null,
)

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
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) {
                    return@awaitEachGesture
                }
                val up = waitForUpOrCancellation()
                if (up != null) {
                    up.consume()
                    val (row, col) =
                        TerminalCoordinateConverter.screenToTerminal(
                            offset = up.position,
                            cellWidth = currentCellWidth.value,
                            cellHeight = currentCellHeight.value,
                            scrollbackLineCount = currentScrollbackLineCount.value,
                            totalRows = currentAllRows.value.size,
                            cols = currentTerminalCols.value,
                            verticalOffset = currentVerticalOffset.value,
                        )
                    currentOnSingleTap.value(row, col)
                }
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed || (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed)) {
                    return@awaitEachGesture
                }
                val dragStart = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

                val state = currentSelectionState.value
                var isDraggingStart = false
                var isDraggingEnd = false
                var initialCursorRow = 0
                var initialCursorCol = 0
                var totalDragDistance = Offset.Zero

                when (state) {
                    is SelectionState.StartCursorPlaced -> {
                        isDraggingStart = true
                        initialCursorRow = state.startRow
                        initialCursorCol = state.startCol
                        currentOnStartDraggingStartCursor.value(initialCursorRow, initialCursorCol)
                    }
                    is SelectionState.SelectionComplete -> {
                        isDraggingEnd = true
                        initialCursorRow = state.selection.endRow
                        initialCursorCol = state.selection.endCol
                        currentOnStartDraggingEndCursor.value(initialCursorRow, initialCursorCol)
                    }
                    else -> return@awaitEachGesture
                }

                drag(dragStart.id) { change ->
                    val dragAmount = change.positionChange()
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

                if (isDraggingStart) {
                    currentOnFinalizeStartCursor.value()
                } else if (isDraggingEnd) {
                    currentOnFinalizeEndCursor.value()
                }
            }
        }
}

/**
 * Attaches pointer gesture recognizers for touch selection placement and long-press handle dragging using SelectionController.
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
    selectionController: SelectionController,
    onSingleTap: (row: Int, col: Int) -> Unit = { _, _ -> },
): Modifier = terminalGestures(
    selectionState = selectionState,
    terminalCols = terminalCols,
    allRows = allRows,
    cellWidth = cellWidth,
    cellHeight = cellHeight,
    scrollbackLineCount = scrollbackLineCount,
    verticalOffset = verticalOffset,
    onSingleTap = onSingleTap,
    onStartDraggingStartCursor = selectionController.onStartDraggingStartCursor,
    onUpdateStartCursor = selectionController.onUpdateStartCursor,
    onFinalizeStartCursor = selectionController.onFinalizeStartCursor,
    onStartDraggingEndCursor = selectionController.onStartDraggingEndCursor,
    onUpdateEndCursor = selectionController.onUpdateEndCursor,
    onFinalizeEndCursor = selectionController.onFinalizeEndCursorAndCopy,
    onExitCopyMode = selectionController.onClearSelection,
)
