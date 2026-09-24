package com.johnan.terminal.core

/**
 * State transition reducer for touch selection and cursor dragging workflows.
 */
object TerminalSelectionLogic {
    // --- Pure SelectionState Reducers ---

    fun onSingleTap(
        current: SelectionState,
        row: Int,
        col: Int,
    ): SelectionState =
        when (current) {
            is SelectionState.CopyModeActive -> {
                SelectionState.StartCursorPlaced(row, col)
            }
            is SelectionState.StartCursorPlaced -> {
                val selection =
                    TerminalSelection(
                        startRow = current.startRow,
                        startCol = current.startCol,
                        endRow = row,
                        endCol = col,
                    )
                SelectionState.SelectionComplete(selection)
            }
            is SelectionState.SelectionComplete -> {
                SelectionState.None
            }
            else -> {
                SelectionState.None
            }
        }

    fun placeStartCursor(
        current: SelectionState,
        row: Int,
        col: Int,
    ): SelectionState =
        if (current is SelectionState.CopyModeActive) {
            SelectionState.StartCursorPlaced(row, col)
        } else {
            current
        }

    fun startDraggingStartCursor(
        current: SelectionState,
        row: Int,
        col: Int,
    ): SelectionState =
        when (current) {
            is SelectionState.StartCursorPlaced, is SelectionState.StartCursorDragging -> {
                SelectionState.StartCursorDragging(row, col)
            }
            else -> current
        }

    fun updateStartCursor(
        current: SelectionState,
        row: Int,
        col: Int,
    ): SelectionState =
        if (current is SelectionState.StartCursorDragging) {
            SelectionState.StartCursorDragging(row, col)
        } else {
            current
        }

    fun finalizeStartCursor(current: SelectionState): SelectionState =
        when (current) {
            is SelectionState.StartCursorDragging -> {
                SelectionState.StartCursorPlaced(current.startRow, current.startCol)
            }
            else -> current
        }

    fun startDraggingEndCursor(
        current: SelectionState,
        row: Int,
        col: Int,
    ): SelectionState =
        when (current) {
            is SelectionState.StartCursorPlaced -> {
                SelectionState.EndCursorDragging(
                    startRow = current.startRow,
                    startCol = current.startCol,
                    endRow = row,
                    endCol = col,
                )
            }
            is SelectionState.StartCursorDragging -> {
                SelectionState.EndCursorDragging(
                    startRow = current.startRow,
                    startCol = current.startCol,
                    endRow = row,
                    endCol = col,
                )
            }
            is SelectionState.SelectionComplete -> {
                val sel = current.selection
                SelectionState.EndCursorDragging(
                    startRow = sel.startRow,
                    startCol = sel.startCol,
                    endRow = row,
                    endCol = col,
                )
            }
            else -> current
        }

    fun updateEndCursor(
        current: SelectionState,
        row: Int,
        col: Int,
    ): SelectionState =
        if (current is SelectionState.EndCursorDragging) {
            SelectionState.EndCursorDragging(
                startRow = current.startRow,
                startCol = current.startCol,
                endRow = row,
                endCol = col,
            )
        } else {
            current
        }

    fun finalizeEndCursor(current: SelectionState): SelectionState =
        when (current) {
            is SelectionState.EndCursorDragging -> {
                val selection =
                    TerminalSelection(
                        startRow = current.startRow,
                        startCol = current.startCol,
                        endRow = current.endRow,
                        endCol = current.endCol,
                    )
                SelectionState.SelectionComplete(selection)
            }
            else -> current
        }

    fun enterCopyMode(): SelectionState = SelectionState.CopyModeActive

    fun exitCopyMode(): SelectionState = SelectionState.None

    fun selectAll(buffer: ScreenBuffer): SelectionState {
        val totalRows = buffer.getScrollback().size + buffer.rows
        val totalCols = buffer.cols
        val selection =
            TerminalSelection(
                startRow = 0,
                startCol = 0,
                endRow = (totalRows - 1).coerceAtLeast(0),
                endCol = (totalCols - 1).coerceAtLeast(0),
            )
        return SelectionState.SelectionComplete(selection)
    }

    fun selectVisible(
        firstVisibleRow: Int,
        visibleRowCount: Int,
        cols: Int,
    ): SelectionState {
        val selection =
            TerminalSelection(
                startRow = firstVisibleRow.coerceAtLeast(0),
                startCol = 0,
                endRow = (firstVisibleRow + visibleRowCount - 1).coerceAtLeast(0),
                endCol = (cols - 1).coerceAtLeast(0),
            )
        return SelectionState.SelectionComplete(selection)
    }

    // --- Backward-Compatible Overloads Operating on TerminalUiState.Active ---

    fun onSingleTap(
        state: TerminalUiState.Active,
        row: Int,
        col: Int,
    ): TerminalUiState.Active = state.copy(selectionState = onSingleTap(state.selectionState, row, col))

    fun placeStartCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int,
    ): TerminalUiState.Active = state.copy(selectionState = placeStartCursor(state.selectionState, row, col))

    fun startDraggingStartCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int,
    ): TerminalUiState.Active = state.copy(selectionState = startDraggingStartCursor(state.selectionState, row, col))

    fun updateStartCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int,
    ): TerminalUiState.Active = state.copy(selectionState = updateStartCursor(state.selectionState, row, col))

    fun finalizeStartCursor(state: TerminalUiState.Active): TerminalUiState.Active =
        state.copy(selectionState = finalizeStartCursor(state.selectionState))

    fun startDraggingEndCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int,
    ): TerminalUiState.Active = state.copy(selectionState = startDraggingEndCursor(state.selectionState, row, col))

    fun updateEndCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int,
    ): TerminalUiState.Active = state.copy(selectionState = updateEndCursor(state.selectionState, row, col))

    fun finalizeEndCursor(state: TerminalUiState.Active): TerminalUiState.Active =
        state.copy(selectionState = finalizeEndCursor(state.selectionState))

    fun getSelectedText(
        state: TerminalUiState.Active,
        buffer: ScreenBuffer,
    ): String? {
        val selection = state.selectionState.toTerminalSelection() ?: return null
        return selection.extractText(buffer)
    }

    fun enterCopyMode(state: TerminalUiState.Active): TerminalUiState.Active =
        state.copy(selectionState = enterCopyMode())

    fun exitCopyMode(state: TerminalUiState.Active): TerminalUiState.Active =
        state.copy(selectionState = exitCopyMode())
}
