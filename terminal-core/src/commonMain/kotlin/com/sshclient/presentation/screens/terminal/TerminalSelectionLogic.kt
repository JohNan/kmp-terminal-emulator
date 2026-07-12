package com.sshclient.presentation.screens.terminal

import com.sshclient.data.terminal.ScreenBuffer

object TerminalSelectionLogic {
    fun onSingleTap(
        state: TerminalUiState.Active,
        row: Int,
        col: Int
    ): TerminalUiState.Active {
        return when (val selState = state.selectionState) {
            is SelectionState.CopyModeActive -> {
                // Step 2: Tap places start cursor
                state.copy(selectionState = SelectionState.StartCursorPlaced(row, col))
            }
            is SelectionState.StartCursorPlaced -> {
                // Step 4 (Tap variant): Tap places end cursor and finalizes selection
                val selection = TerminalSelection(
                    startRow = selState.startRow,
                    startCol = selState.startCol,
                    endRow = row,
                    endCol = col,
                )
                // Transition to SelectionComplete state
                state.copy(selectionState = SelectionState.SelectionComplete(selection))
            }
            is SelectionState.SelectionComplete -> {
                // Tap on completed selection exits copy mode
                state.copy(selectionState = SelectionState.None)
            }
            else -> {
                // In other copy mode states (dragging), tap exits copy mode
                state.copy(selectionState = SelectionState.None)
            }
        }
    }

    fun placeStartCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int
    ): TerminalUiState.Active {
        return if (state.selectionState is SelectionState.CopyModeActive) {
            state.copy(selectionState = SelectionState.StartCursorPlaced(row, col))
        } else {
            state
        }
    }

    fun startDraggingStartCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int
    ): TerminalUiState.Active {
        return when (val selState = state.selectionState) {
            is SelectionState.StartCursorPlaced -> {
                state.copy(selectionState = SelectionState.StartCursorDragging(row, col))
            }
            is SelectionState.StartCursorDragging -> {
                state.copy(selectionState = SelectionState.StartCursorDragging(row, col))
            }
            else -> state
        }
    }

    fun updateStartCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int
    ): TerminalUiState.Active {
        return if (state.selectionState is SelectionState.StartCursorDragging) {
            state.copy(selectionState = SelectionState.StartCursorDragging(row, col))
        } else {
            state
        }
    }

    fun finalizeStartCursor(
        state: TerminalUiState.Active
    ): TerminalUiState.Active {
        return when (val selState = state.selectionState) {
            is SelectionState.StartCursorDragging -> {
                state.copy(
                    selectionState = SelectionState.StartCursorPlaced(selState.startRow, selState.startCol)
                )
            }
            else -> state
        }
    }

    fun startDraggingEndCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int
    ): TerminalUiState.Active {
        return when (val selState = state.selectionState) {
            is SelectionState.StartCursorPlaced -> {
                state.copy(
                    selectionState = SelectionState.EndCursorDragging(
                        startRow = selState.startRow,
                        startCol = selState.startCol,
                        endRow = row,
                        endCol = col,
                    )
                )
            }
            is SelectionState.StartCursorDragging -> {
                state.copy(
                    selectionState = SelectionState.EndCursorDragging(
                        startRow = selState.startRow,
                        startCol = selState.startCol,
                        endRow = row,
                        endCol = col,
                    )
                )
            }
            is SelectionState.SelectionComplete -> {
                val sel = selState.selection
                state.copy(
                    selectionState = SelectionState.EndCursorDragging(
                        startRow = sel.startRow,
                        startCol = sel.startCol,
                        endRow = row,
                        endCol = col,
                    )
                )
            }
            else -> state
        }
    }

    fun updateEndCursor(
        state: TerminalUiState.Active,
        row: Int,
        col: Int
    ): TerminalUiState.Active {
        return if (state.selectionState is SelectionState.EndCursorDragging) {
            val selState = state.selectionState
            state.copy(
                selectionState = SelectionState.EndCursorDragging(
                    startRow = selState.startRow,
                    startCol = selState.startCol,
                    endRow = row,
                    endCol = col,
                )
            )
        } else {
            state
        }
    }

    fun finalizeEndCursor(
        state: TerminalUiState.Active
    ): TerminalUiState.Active {
        return when (val selState = state.selectionState) {
            is SelectionState.EndCursorDragging -> {
                val selection = TerminalSelection(
                    startRow = selState.startRow,
                    startCol = selState.startCol,
                    endRow = selState.endRow,
                    endCol = selState.endCol,
                )
                state.copy(selectionState = SelectionState.SelectionComplete(selection))
            }
            else -> state
        }
    }

    fun getSelectedText(
        state: TerminalUiState.Active,
        buffer: ScreenBuffer
    ): String? {
        val selection = state.selectionState.toTerminalSelection() ?: return null
        return selection.extractText(buffer)
    }

    fun enterCopyMode(state: TerminalUiState.Active): TerminalUiState.Active {
        return state.copy(selectionState = SelectionState.CopyModeActive)
    }

    fun exitCopyMode(state: TerminalUiState.Active): TerminalUiState.Active {
        return state.copy(selectionState = SelectionState.None)
    }
}
