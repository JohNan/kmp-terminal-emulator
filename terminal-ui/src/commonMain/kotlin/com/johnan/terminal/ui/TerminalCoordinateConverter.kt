package com.johnan.terminal.ui

import androidx.compose.ui.geometry.Offset

/**
 * Coordinate transformations between pixel offsets on screen and 0-indexed terminal row/column cells.
 */
object TerminalCoordinateConverter {
    /**
     * Maps canvas pixel offset to terminal buffer (row, col) coordinates.
     */
    fun screenToTerminal(
        offset: Offset,
        cellWidth: Float,
        cellHeight: Float,
        scrollbackLineCount: Int,
        totalRows: Int,
        cols: Int,
        verticalOffset: Float = 0f,
    ): Pair<Int, Int> {
        val adjustedY = offset.y - verticalOffset
        val row = maxOf(0f, adjustedY / cellHeight).toInt().coerceIn(0, totalRows - 1)
        val col = maxOf(0f, offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
        return Pair(row, col)
    }

    /**
     * Maps terminal (row, col) coordinates to screen pixel offset.
     */
    fun terminalToScreen(
        row: Int,
        col: Int,
        cellWidth: Float,
        cellHeight: Float,
        verticalOffset: Float = 0f,
    ): Offset {
        val x = col * cellWidth
        val y = row * cellHeight + verticalOffset
        return Offset(x, y)
    }

    /**
     * Maps pixel offset and scroll position to terminal (col, row) cell coordinates.
     */
    fun pixelToCellCoordinates(
        pixelX: Float,
        pixelY: Float,
        scrollOffset: Float,
        cellWidth: Float,
        cellHeight: Float,
        verticalOffset: Float = 0f,
        cols: Int,
        visibleRows: Int,
        scrollbackSize: Int,
    ): Pair<Int, Int> = com.johnan.terminal.ui.pixelToCellCoordinates(
        pixelX = pixelX,
        pixelY = pixelY,
        scrollOffset = scrollOffset,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        verticalOffset = verticalOffset,
        cols = cols,
        visibleRows = visibleRows,
        scrollbackSize = scrollbackSize,
    )
}

/**
 * Maps pixel offset and scroll position to terminal (col, row) cell coordinates.
 *
 * @param pixelX The x coordinate in pixels.
 * @param pixelY The y coordinate in pixels.
 * @param scrollOffset The vertical scroll offset in pixels (from scrollState.value).
 * @param cellWidth The width of a single character cell in pixels.
 * @param cellHeight The height of a single character cell in pixels.
 * @param verticalOffset Vertical offset applied to terminal canvas (0f by default).
 * @param cols Number of visible columns.
 * @param visibleRows Number of visible rows on screen.
 * @param scrollbackSize Number of lines in the scrollback buffer.
 * @return Pair of (col, row), where col is 0-indexed column and row is 0-indexed row across scrollback and visible rows.
 */
fun pixelToCellCoordinates(
    pixelX: Float,
    pixelY: Float,
    scrollOffset: Float,
    cellWidth: Float,
    cellHeight: Float,
    verticalOffset: Float = 0f,
    cols: Int,
    visibleRows: Int,
    scrollbackSize: Int,
): Pair<Int, Int> {
    val totalRows = visibleRows + scrollbackSize
    val totalY = pixelY + scrollOffset - verticalOffset
    val row = if (cellHeight > 0f && totalRows > 0) {
        maxOf(0f, totalY / cellHeight).toInt().coerceIn(0, totalRows - 1)
    } else {
        0
    }
    val col = if (cellWidth > 0f && cols > 0) {
        maxOf(0f, pixelX / cellWidth).toInt().coerceIn(0, cols - 1)
    } else {
        0
    }
    return Pair(col, row)
}
