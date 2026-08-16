package com.johnan.terminal.ui

import androidx.compose.ui.geometry.Offset

/**
 * Helper object for converting between screen coordinates and terminal coordinates
 */
object TerminalCoordinateConverter {
    /**
     * Convert screen pixel coordinates to terminal (row, column) coordinates
     *
     * @param offset Screen pixel offset from the top-left of the terminal canvas
     * @param cellWidth Width of a single terminal cell in pixels
     * @param cellHeight Height of a single terminal cell in pixels
     * @param scrollbackLineCount Number of lines in scrollback buffer
     * @param totalRows Total number of rows (scrollback + visible)
     * @param cols Number of columns in the terminal
     * @return Pair of (row, col) in terminal coordinates, clamped to valid bounds
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
        // Convert pixel offset to row/col, accounting for vertical alignment
        val adjustedY = offset.y - verticalOffset
        val row = maxOf(0f, adjustedY / cellHeight).toInt().coerceIn(0, totalRows - 1)
        val col = maxOf(0f, offset.x / cellWidth).toInt().coerceIn(0, cols - 1)

        return Pair(row, col)
    }

    /**
     * Convert terminal (row, column) coordinates to screen pixel coordinates
     *
     * @param row Terminal row (including scrollback)
     * @param col Terminal column
     * @param cellWidth Width of a single terminal cell in pixels
     * @param cellHeight Height of a single terminal cell in pixels
     * @param verticalOffset Vertical offset for alignment
     * @return Screen pixel offset from the top-left of the terminal canvas
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
