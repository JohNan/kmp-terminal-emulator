package com.johnan.terminal.core

/**
 * Internal mutable row of terminal cells with version and occupancy tracking.
 */
internal class TerminalRow(val cols: Int) {
    val cells: Array<TerminalCell> = Array(cols) { TerminalCell.EMPTY }
    var version: Long = 0
        private set

    var nonDefaultCells: Int = 0
        private set

    var cachedSnapshot: Array<TerminalCell>? = null
    var cachedSnapshotVersion: Long = -1

    fun isEmpty(): Boolean = nonDefaultCells == 0

    operator fun get(index: Int): TerminalCell = cells[index]

    operator fun set(
        index: Int,
        value: TerminalCell,
    ) {
        if (setCell(index, value)) {
            version++
        }
    }

    /**
     * Resets a contiguous range of cells to [TerminalCell.EMPTY].
     *
     * @return true if any cell values changed.
     */
    internal fun clearCells(
        startIndex: Int,
        endIndex: Int,
    ): Boolean {
        if (nonDefaultCells == 0) return false

        var changed = false
        for (i in startIndex until endIndex) {
            val cell = cells[i]
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
     * Updates a single cell without incrementing the row version.
     *
     * @return true if the cell was modified.
     */
    internal fun setCell(
        index: Int,
        value: TerminalCell,
    ): Boolean {
        val oldValue = cells[index]
        if (oldValue === value) return false

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

    internal fun incrementVersion() {
        version++
    }

    /**
     * Recalculates occupied non-default cell count following bulk operations.
     */
    fun recalculateNonDefaultCells() {
        var count = 0
        for (i in cells.indices) {
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
