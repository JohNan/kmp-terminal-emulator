package com.johnan.terminal.core

/**
 * Rectangular or linear text selection coordinate boundaries within the terminal buffer.
 */
data class TerminalSelection(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
) {
    /**
     * Returns a normalized selection ensuring start coordinates precede end coordinates.
     */
    fun normalized(): TerminalSelection {
        return if (startRow < endRow || (startRow == endRow && startCol <= endCol)) {
            this
        } else {
            TerminalSelection(endRow, endCol, startRow, startCol)
        }
    }

    /**
     * Checks if the specified buffer cell position falls within this selection.
     */
    fun contains(
        row: Int,
        col: Int,
    ): Boolean {
        val norm = normalized()
        return when {
            row < norm.startRow || row > norm.endRow -> false
            row == norm.startRow && row == norm.endRow -> col >= norm.startCol && col <= norm.endCol
            row == norm.startRow -> col >= norm.startCol
            row == norm.endRow -> col <= norm.endCol
            else -> true
        }
    }

    /**
     * Extracts plain text string contained in this selection across scrollback and active visible rows.
     */
    fun extractText(screenBuffer: ScreenBuffer): String {
        val norm = normalized()
        val scrollback = screenBuffer.getScrollback()
        val visibleRows = screenBuffer.getAllRows()
        val allRows = scrollback + visibleRows

        if (norm.startRow < 0 || norm.endRow >= allRows.size) {
            return ""
        }

        val lines = mutableListOf<String>()
        for (row in norm.startRow..norm.endRow) {
            if (row >= allRows.size) break

            val cells = allRows[row]
            val startCol = if (row == norm.startRow) norm.startCol else 0
            val endCol = if (row == norm.endRow) norm.endCol else cells.size - 1

            val lineBuilder = StringBuilder()
            for (col in startCol..endCol.coerceAtMost(cells.lastIndex)) {
                lineBuilder.append(cells[col].char)
            }

            lines.add(lineBuilder.toString().trimEnd())
        }

        return lines.joinToString("\n")
    }
}

enum class ActiveCursor {
    START,
    END,
}

/**
 * Text selection lifecycle and copy-mode interaction state.
 */
sealed class SelectionState {
    data object None : SelectionState()

    data object CopyModeActive : SelectionState()

    data class StartCursorPlaced(
        val startRow: Int,
        val startCol: Int,
    ) : SelectionState()

    data class StartCursorDragging(
        val startRow: Int,
        val startCol: Int,
    ) : SelectionState()

    data class EndCursorDragging(
        val startRow: Int,
        val startCol: Int,
        val endRow: Int,
        val endCol: Int,
    ) : SelectionState()

    data class SelectionComplete(
        val selection: TerminalSelection,
    ) : SelectionState()

    /**
     * Converts current state to [TerminalSelection] boundaries if active.
     */
    fun toTerminalSelection(): TerminalSelection? =
        when (this) {
            is StartCursorPlaced -> TerminalSelection(startRow, startCol, startRow, startCol)
            is StartCursorDragging -> TerminalSelection(startRow, startCol, startRow, startCol)
            is EndCursorDragging -> TerminalSelection(startRow, startCol, endRow, endCol)
            is SelectionComplete -> selection
            else -> null
        }

    fun isCopyModeActive(): Boolean = this !is None
}
