package com.johnan.terminal.core

/**
 * Represents a text selection in the terminal.
 *
 * Selection is defined by start and end positions in (row, column) coordinates.
 * Positions are 0-indexed and relative to the terminal buffer (including scrollback).
 *
 * @param startRow Start row (inclusive, 0-indexed)
 * @param startCol Start column (inclusive, 0-indexed)
 * @param endRow End row (inclusive, 0-indexed)
 * @param endCol End column (inclusive, 0-indexed)
 */
data class TerminalSelection(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
) {
    /**
     * Returns a normalized selection where start is always before or equal to end.
     * This handles cases where the user drags backwards.
     */
    fun normalized(): TerminalSelection {
        return if (startRow < endRow || (startRow == endRow && startCol <= endCol)) {
            this
        } else {
            TerminalSelection(endRow, endCol, startRow, startCol)
        }
    }

    /**
     * Checks if a given position is within the selection range.
     *
     * @param row Row to check (0-indexed)
     * @param col Column to check (0-indexed)
     * @return true if the position is within the selection
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
     * Extracts the selected text from the terminal buffer.
     *
     * Features:
     * - Respects terminal column wrapping
     * - Strips ANSI escape sequences (only plain text)
     * - Preserves line breaks as displayed
     * - Handles scrollback buffer + visible rows
     *
     * @param screenBuffer The terminal screen buffer
     * @return The selected text as a plain string
     */
    fun extractText(screenBuffer: ScreenBuffer): String {
        val norm = normalized()
        val scrollback = screenBuffer.getScrollback()
        val visibleRows = screenBuffer.getAllRows()
        val allRows = scrollback + visibleRows

        // Validate bounds
        if (norm.startRow < 0 || norm.endRow >= allRows.size) {
            return ""
        }

        val lines = mutableListOf<String>()

        for (row in norm.startRow..norm.endRow) {
            if (row >= allRows.size) break

            val cells = allRows[row]
            val startCol = if (row == norm.startRow) norm.startCol else 0
            val endCol = if (row == norm.endRow) norm.endCol else cells.size - 1

            // Extract characters from this row
            val lineBuilder = StringBuilder()
            for (col in startCol..endCol.coerceAtMost(cells.lastIndex)) {
                lineBuilder.append(cells[col].char)
            }

            // Trim trailing spaces from each line
            lines.add(lineBuilder.toString().trimEnd())
        }

        // Join lines with newlines
        return lines.joinToString("\n")
    }
}

/**
 * Identifies which cursor is currently active for movement.
 */
enum class ActiveCursor {
    START,
    END,
}

/**
 * Represents the state of copy mode and text selection in the terminal.
 *
 * Copy Mode Flow:
 * 1. None → CopyModeActive: User taps copy button in toolbar
 * 2. CopyModeActive → StartCursorPlaced: User taps to place start cursor
 * 3. StartCursorPlaced → StartCursorDragging: User drags (one-finger) to move start cursor
 * 4. StartCursorPlaced/StartCursorDragging → EndCursorDragging: User long-presses to enter end cursor mode
 * 5. EndCursorDragging: User drags while holding to select text
 * 6. EndCursorDragging → SelectionComplete: User releases → auto-copy to clipboard
 * 7. SelectionComplete: Copy mode remains active, user can tap to reposition or exit
 */
sealed class SelectionState {
    /** No copy mode active */
    data object None : SelectionState()

    /**
     * Copy mode is active, waiting for user to tap and place start cursor.
     * Terminal input is disabled. Scrolling is allowed.
     */
    data object CopyModeActive : SelectionState()

    /**
     * Start cursor has been placed at a specific position.
     * Single cursor visible, no selection yet.
     * @param startRow Row position of start cursor
     * @param startCol Column position of start cursor
     */
    data class StartCursorPlaced(
        val startRow: Int,
        val startCol: Int,
    ) : SelectionState()

    /**
     * User is dragging (one-finger) to move the start cursor.
     * Cursor moves continuously as finger moves anywhere on screen.
     * @param startRow Current row position of start cursor
     * @param startCol Current column position of start cursor
     */
    data class StartCursorDragging(
        val startRow: Int,
        val startCol: Int,
    ) : SelectionState()

    /**
     * User is long-pressing and dragging to move the end cursor and create selection.
     * Live selection is shown from start cursor to current end cursor position.
     * @param startRow Start cursor row (locked)
     * @param startCol Start cursor column (locked)
     * @param endRow Current end cursor row
     * @param endCol Current end cursor column
     */
    data class EndCursorDragging(
        val startRow: Int,
        val startCol: Int,
        val endRow: Int,
        val endCol: Int,
    ) : SelectionState()

    /**
     * Selection is complete and copied to clipboard.
     * Copy mode remains active. User can tap to reposition or exit copy mode.
     * @param selection The completed selection
     */
    data class SelectionComplete(
        val selection: TerminalSelection,
    ) : SelectionState()

    /**
     * Get the current terminal selection if one exists.
     * Returns null if copy mode is not active or no cursor is placed.
     */
    fun toTerminalSelection(): TerminalSelection? =
        when (this) {
            is StartCursorPlaced -> TerminalSelection(startRow, startCol, startRow, startCol)
            is StartCursorDragging -> TerminalSelection(startRow, startCol, startRow, startCol)
            is EndCursorDragging -> TerminalSelection(startRow, startCol, endRow, endCol)
            is SelectionComplete -> selection
            else -> null
        }

    /**
     * Check if copy mode is active (any state except None)
     */
    fun isCopyModeActive(): Boolean = this !is None
}
