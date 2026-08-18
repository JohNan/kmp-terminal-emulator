package com.johnan.terminal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.johnan.terminal.core.SelectionState
import com.johnan.terminal.core.TerminalSelection

/**
 * Visual highlight overlay for active text selection and start/end handle indicators.
 */
@Composable
fun SelectionOverlay(
    selectionState: SelectionState,
    cellWidth: Float,
    cellHeight: Float,
    scrollbackLineCount: Int,
    totalRows: Int,
    modifier: Modifier = Modifier,
    selectionColor: Color = Color(0x4000AAFF),
) {
    val selection = selectionState.toTerminalSelection() ?: return
    val normalized = selection.normalized()

    Canvas(modifier = modifier.fillMaxSize()) {
        val contentHeight = totalRows * cellHeight
        val verticalOffset = if (contentHeight < size.height) size.height - contentHeight else 0f

        drawSelection(
            selection = normalized,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            scrollbackLineCount = scrollbackLineCount,
            selectionColor = selectionColor,
            verticalOffset = verticalOffset,
        )

        when (selectionState) {
            is SelectionState.StartCursorPlaced -> {
                drawCursorIndicator(
                    row = selectionState.startRow,
                    col = selectionState.startCol,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    color = Color(0xFF00FF00),
                    verticalOffset = verticalOffset,
                )
            }
            is SelectionState.StartCursorDragging -> {
                drawCursorIndicator(
                    row = selectionState.startRow,
                    col = selectionState.startCol,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    color = Color(0xFF00FF00),
                    verticalOffset = verticalOffset,
                )
            }
            is SelectionState.EndCursorDragging -> {
                drawCursorIndicator(
                    row = selectionState.startRow,
                    col = selectionState.startCol,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    color = Color(0xFF00FF00),
                    verticalOffset = verticalOffset,
                )
                drawCursorIndicator(
                    row = selectionState.endRow,
                    col = selectionState.endCol,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    color = Color(0xFFFF0000),
                    verticalOffset = verticalOffset,
                )
            }
            is SelectionState.SelectionComplete -> {
                drawCursorIndicator(
                    row = normalized.startRow,
                    col = normalized.startCol,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    color = Color(0xFF00FF00),
                    verticalOffset = verticalOffset,
                )
                drawCursorIndicator(
                    row = normalized.endRow,
                    col = normalized.endCol,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    color = Color(0xFFFF0000),
                    verticalOffset = verticalOffset,
                )
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawCursorIndicator(
    row: Int,
    col: Int,
    cellWidth: Float,
    cellHeight: Float,
    color: Color,
    verticalOffset: Float,
) {
    val x = col * cellWidth
    val y = row * cellHeight + verticalOffset

    drawRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(x, y),
        size = Size(cellWidth, cellHeight),
    )
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(cellWidth, cellHeight),
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawSelection(
    selection: TerminalSelection,
    cellWidth: Float,
    cellHeight: Float,
    scrollbackLineCount: Int,
    selectionColor: Color,
    verticalOffset: Float,
) {
    for (row in selection.startRow..selection.endRow) {
        val startCol = if (row == selection.startRow) selection.startCol else 0
        val endCol = if (row == selection.endRow) selection.endCol else Int.MAX_VALUE

        val maxColsForRow = (size.width / cellWidth).toInt().coerceAtLeast(80)
        val actualEndCol = endCol.coerceAtMost(maxColsForRow - 1)
        val x = startCol * cellWidth
        val y = row * cellHeight + verticalOffset
        val width = (actualEndCol - startCol + 1) * cellWidth

        drawRect(
            color = selectionColor,
            topLeft = Offset(x, y),
            size = Size(width, cellHeight),
        )
    }
}
