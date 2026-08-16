package com.johnan.terminal.core
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TerminalEmulatorCacheTest {
    @Test
    fun `row array identity changes when content changes`() = runTest {
        val emulator = TerminalEmulator(rows = 24, cols = 80)

        // Initial state
        emulator.processOutput("A")
        val state1 = emulator.screenState.value
        val row0v1 = state1.rows[0]

        // Change content of same row
        emulator.processOutput("B")
        val state2 = emulator.screenState.value
        val row0v2 = state2.rows[0]

        // Assert arrays are different instances
        assertNotSame(row0v1, row0v2, "Array identity must change when content changes")
    }

    @Test
    fun `row array identity stays same when content is unchanged`() = runTest {
        val emulator = TerminalEmulator(rows = 24, cols = 80)

        // Initial state
        emulator.processOutput("A")
        val state1 = emulator.screenState.value
        val row0v1 = state1.rows[0]
        val row1v1 = state1.rows[1] // Empty row

        // Write to a different row (forcing state update)
        // Move cursor to row 2
        emulator.processOutput("\u001B[3;1H") // CUP - Move to 3rd row (index 2)
        emulator.processOutput("C")

        val state2 = emulator.screenState.value
        val row0v2 = state2.rows[0] // Should be same as v1
        val row1v2 = state2.rows[1] // Should be same as v1

        // Assert unchanged rows reuse array instances
        assertSame(row0v1, row0v2, "Unchanged row should reuse array instance")
        assertSame(row1v1, row1v2, "Unchanged empty row should reuse array instance")
    }
}
