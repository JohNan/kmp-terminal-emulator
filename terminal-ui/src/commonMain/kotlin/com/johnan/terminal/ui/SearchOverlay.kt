package com.johnan.terminal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.johnan.terminal.core.SearchState
import com.johnan.terminal.core.TerminalMatch

/**
 * Highlights find-in-terminal query matches and active match index across terminal cells.
 */
@Composable
fun SearchOverlay(
    searchState: SearchState,
    cellWidth: Float,
    cellHeight: Float,
    scrollbackLineCount: Int,
    totalRows: Int,
    modifier: Modifier = Modifier,
    matchColor: Color = Color(0x80FFFF00),
    currentMatchColor: Color = Color(0x80FF8000),
) {
    if (!searchState.isVisible || searchState.matches.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val contentHeight = totalRows * cellHeight
        val verticalOffset = if (contentHeight < size.height) size.height - contentHeight else 0f

        val matchesSize = searchState.matches.size
        for (index in 0 until matchesSize) {
            val match = searchState.matches[index]
            val color = if (index == searchState.currentMatchIndex) currentMatchColor else matchColor

            drawMatch(
                match = match,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                color = color,
                verticalOffset = verticalOffset,
            )
        }
    }
}

private fun DrawScope.drawMatch(
    match: TerminalMatch,
    cellWidth: Float,
    cellHeight: Float,
    color: Color,
    verticalOffset: Float,
) {
    val row = match.startRow
    val startCol = match.startCol
    val endCol = match.endCol

    val x = startCol * cellWidth
    val y = row * cellHeight + verticalOffset
    val width = (endCol - startCol) * cellWidth

    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, cellHeight),
    )
}
