package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalRowOptimizationTest {
    @Test
    fun `TerminalRow should start with 0 nonDefaultCells`() {
        val row = TerminalRow(80)
        assertTrue(row.isEmpty(), "Initially empty")
        assertEquals(0, row.nonDefaultCells)
    }

    @Test
    fun `setCell should update nonDefaultCells`() {
        val row = TerminalRow(80)

        // Set a non-default cell
        val nonDefaultCell = TerminalCell(char = 'A')
        row.setCell(0, nonDefaultCell)

        assertFalse(row.isEmpty(), "Should not be empty")
        assertEquals(1, row.nonDefaultCells)

        // Set another non-default cell
        row.setCell(1, nonDefaultCell)
        assertEquals(2, row.nonDefaultCells)

        // Overwrite with default cell
        row.setCell(0, TerminalCell.EMPTY)
        assertEquals(1, row.nonDefaultCells)

        // Overwrite with another default cell (should remain same count)
        row.setCell(0, TerminalCell.EMPTY)
        assertEquals(1, row.nonDefaultCells)

        // Clear last non-default cell
        row.setCell(1, TerminalCell.EMPTY)
        assertTrue(row.isEmpty(), "Should be empty again")
        assertEquals(0, row.nonDefaultCells)
    }

    @Test
    fun `operator set should update nonDefaultCells`() {
        val row = TerminalRow(80)
        val nonDefaultCell = TerminalCell(char = 'B')

        row[5] = nonDefaultCell
        assertEquals(1, row.nonDefaultCells)
        assertFalse(row.isEmpty())

        row[5] = TerminalCell.EMPTY
        assertEquals(0, row.nonDefaultCells)
        assertTrue(row.isEmpty())
    }

    @Test
    fun `overwriting non-default with non-default should not change count`() {
        val row = TerminalRow(80)
        val cell1 = TerminalCell(char = 'C')
        val cell2 = TerminalCell(char = 'D')

        row[10] = cell1
        assertEquals(1, row.nonDefaultCells)

        row[10] = cell2
        assertEquals(1, row.nonDefaultCells)
    }

    @Test
    fun `recalculateNonDefaultCells should correct count after bulk operations`() {
        val row = TerminalRow(80)

        // Simulate external modification (e.g. array copy)
        row.cells[0] = TerminalCell(char = 'E')
        row.cells[1] = TerminalCell(char = 'F')

        // Count is still 0 because we bypassed set/setCell
        assertEquals(0, row.nonDefaultCells)

        // Recalculate
        row.recalculateNonDefaultCells()

        assertEquals(2, row.nonDefaultCells)
        assertFalse(row.isEmpty())
    }
}
