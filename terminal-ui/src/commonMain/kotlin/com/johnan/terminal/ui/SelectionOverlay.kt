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
 * Renders the text selection overlay on top of the terminal
 *
 * Features:
 * - Highlights selected cells with semi-transparent color
 * - Handles multi-line selections
 * - Respects terminal cell boundaries
 * - Shows cursor indicators for start and end positions with prominent colors
 */
@Composable
fun SelectionOverlay(
    selectionState: SelectionState,
    cellWidth: Float,
    cellHeight: Float,
    scrollbackLineCount: Int,
    totalRows: Int,
    modifier: Modifier = Modifier,
    // Semi-transparent blue
    selectionColor: Color = Color(0x4000AAFF),
) {
    // Get the selection if one exists
    val selection = selectionState.toTerminalSelection()

    if (selection != null) {
        val normalized = selection.normalized()

        Canvas(modifier = modifier.fillMaxSize()) {
            // Calculate vertical offset to align content to bottom if it's smaller than the canvas
            val contentHeight = totalRows * cellHeight
            val verticalOffset =
                if (contentHeight < size.height) {
                    size.height - contentHeight
                } else {
                    0f
                }

            drawSelection(
                selection = normalized,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                scrollbackLineCount = scrollbackLineCount,
                selectionColor = selectionColor,
                verticalOffset = verticalOffset,
            )

            // Draw cursor indicators based on selection state
            when (selectionState) {
                is SelectionState.StartCursorPlaced -> {
                    // Single cursor at start position
                    drawCursorIndicator(
                        row = selectionState.startRow,
                        col = selectionState.startCol,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        // Bright green for start cursor
                        color = Color(0xFF00FF00),
                        label = "S",
                        verticalOffset = verticalOffset,
                    )
                }
                is SelectionState.StartCursorDragging -> {
                    // Moving start cursor
                    drawCursorIndicator(
                        row = selectionState.startRow,
                        col = selectionState.startCol,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        // Bright green for start cursor
                        color = Color(0xFF00FF00),
                        label = "S",
                        verticalOffset = verticalOffset,
                    )
                }
                is SelectionState.EndCursorDragging -> {
                    // Both cursors visible during selection
                    drawCursorIndicator(
                        row = selectionState.startRow,
                        col = selectionState.startCol,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        // Bright green for start cursor
                        color = Color(0xFF00FF00),
                        label = "S",
                        verticalOffset = verticalOffset,
                    )
                    drawCursorIndicator(
                        row = selectionState.endRow,
                        col = selectionState.endCol,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        // Bright red for end cursor
                        color = Color(0xFFFF0000),
                        label = "E",
                        verticalOffset = verticalOffset,
                    )
                }
                is SelectionState.SelectionComplete -> {
                    // Show both cursors for completed selection
                    drawCursorIndicator(
                        row = normalized.startRow,
                        col = normalized.startCol,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        // Bright green for start cursor
                        color = Color(0xFF00FF00),
                        label = "S",
                        verticalOffset = verticalOffset,
                    )
                    drawCursorIndicator(
                        row = normalized.endRow,
                        col = normalized.endCol,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        // Bright red for end cursor
                        color = Color(0xFFFF0000),
                        label = "E",
                        verticalOffset = verticalOffset,
                    )
                }
                else -> {
                    // No cursor indicators needed
                }
            }
        }
    }
}

/**
 * Draw a cursor indicator at the specified position
 */
private fun DrawScope.drawCursorIndicator(
    row: Int,
    col: Int,
    cellWidth: Float,
    cellHeight: Float,
    color: Color,
    label: String,
    verticalOffset: Float,
) {
    val x = col * cellWidth
    val y = row * cellHeight + verticalOffset

    // Draw a semi-transparent rectangle over the letter
    drawRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(x, y),
        size = Size(cellWidth, cellHeight),
    )

    // Draw a thin border for better definition
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(cellWidth, cellHeight),
        style = Stroke(width = 2f),
    )
}

/**
 * Draw the selection highlight
 */
private fun DrawScope.drawSelection(
    selection: TerminalSelection,
    cellWidth: Float,
    cellHeight: Float,
    scrollbackLineCount: Int,
    selectionColor: Color,
    verticalOffset: Float,
) {
    // Draw selection row by row
    for (row in selection.startRow..selection.endRow) {
        val startCol = if (row == selection.startRow) selection.startCol else 0
        val endCol = if (row == selection.endRow) selection.endCol else Int.MAX_VALUE

        // Calculate the width for this row's selection
        // Clamp to reasonable terminal width (typically 80-120 cols, but use canvas width as upper bound)
        val maxColsForRow = (size.width / cellWidth).toInt().coerceAtLeast(80)
        val actualEndCol = endCol.coerceAtMost(maxColsForRow - 1)
        val x = startCol * cellWidth
        val y = row * cellHeight + verticalOffset
        val width = (actualEndCol - startCol + 1) * cellWidth

        // Draw selection rectangle for this row
        drawRect(
            color = selectionColor,
            topLeft = Offset(x, y),
            size = Size(width, cellHeight),
        )
    }
}
