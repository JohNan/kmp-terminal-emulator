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
}
