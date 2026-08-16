package com.johnan.terminal.core

/**
 * Internal wrapper for a row of terminal cells with version tracking.
 */
internal class TerminalRow(val cols: Int) {
    val cells: Array<TerminalCell> = Array(cols) { TerminalCell.EMPTY }
    var version: Long = 0
        private set

    // Optimization: Track number of non-default cells to allow O(1) empty checks.
    // This avoids O(Cols) scan in TerminalRenderer to find content height.
    var nonDefaultCells: Int = 0
        private set

    // Optimization: Cache the snapshot of this row's cells to avoid Map lookups in TerminalEmulator.
    // This allows O(1) access to the immutable snapshot required for structural sharing in the UI state.
    var cachedSnapshot: Array<TerminalCell>? = null
    var cachedSnapshotVersion: Long = -1

    /**
     * Check if this row is visually empty (all cells are default).
     */
    fun isEmpty(): Boolean = nonDefaultCells == 0

    operator fun get(index: Int) = cells[index]

    operator fun set(
        index: Int,
        value: TerminalCell,
    ) {
        if (setCell(index, value)) {
            version++
        }
    }

    /**
     * Set multiple cells to empty without incrementing version.
     * Use this for batch clear updates, followed by [incrementVersion].
     * @return true if any cells were actually modified
     */
    internal fun clearCells(
        startIndex: Int,
        endIndex: Int,
    ): Boolean {
        // Fast path for when the row is already empty
        if (nonDefaultCells == 0) return false

        var changed = false
        for (i in startIndex until endIndex) {
            val cell = cells[i]
            // Bolt: Add identity fast-path to bypass expensive isDefault property checks
            if (cell !== TerminalCell.EMPTY) {
                if (!cell.isDefault()) {
                    nonDefaultCells--
                }
                cells[i] = TerminalCell.EMPTY
                changed = true
            }
        }
        return changed
    }

    /**
     * Set cell without incrementing version.
     * Use this for batch updates, followed by [incrementVersion].
     * @return true if the cell was actually modified
     */
    internal fun setCell(
        index: Int,
        value: TerminalCell,
    ): Boolean {
        val oldValue = cells[index]

        // Fast path: Exact object reference match
        if (oldValue === value) return false

        // Only update count if default status changes.
        // Bolt: Add identity fast-paths to bypass expensive isDefault property checks
        val oldIsDefault = oldValue === TerminalCell.EMPTY || oldValue.isDefault()
        val newIsDefault = value === TerminalCell.EMPTY || value.isDefault()

        if (oldIsDefault != newIsDefault) {
            if (newIsDefault) {
                nonDefaultCells--
            } else {
                nonDefaultCells++
            }
        }

        cells[index] = value
        return true
    }

    /**
     * Manually increment version.
     */
    internal fun incrementVersion() {
        version++
    }

    /**
     * Recalculate nonDefaultCells count.
     * Must be called after bulk operations that bypass setCell (e.g. array copies).
     */
    fun recalculateNonDefaultCells() {
        var count = 0
        for (i in 0 until cells.size) {
            // Fast path check to avoid method call overhead
            if (cells[i] !== TerminalCell.EMPTY && !cells[i].isDefault()) {
                count++
            }
        }
        nonDefaultCells = count
    }

    fun copyInto(
        destination: Array<TerminalCell>,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = cells.size,
    ) {
        cells.copyInto(destination, destinationOffset, startIndex, endIndex)
    }

    fun copyOf(): Array<TerminalCell> = cells.copyOf()
}
