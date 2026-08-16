package com.sshclient.data.terminal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TerminalEmulatorScrollbackCacheTest {
    @Test
    fun `scrollback list is reused when no scrolling occurs`() = runTest {
        val emulator = TerminalEmulator(rows = 10, cols = 80)

        // Initial state
        val state1 = emulator.screenState.first()

        // Write something that doesn't cause scroll
        emulator.processOutput("Hello World")
        val state2 = emulator.screenState.first()

        // Scrollback list reference should be the same (empty list)
        assertSame(state1.scrollback, state2.scrollback, "Scrollback list should be reused")

        // Write enough lines to cause scroll
        val sb = StringBuilder()
        repeat(15) { sb.append("Line $it\r\n") }
        emulator.processOutput(sb.toString())

        val state3 = emulator.screenState.first()

        // Scrollback list should change
        assertNotSame(state2.scrollback, state3.scrollback, "Scrollback list should change after scrolling")

        // Write more without scrolling (cursor moves but no new lines added to scrollback)
        // Note: cursor is at bottom. Writing text without newline stays on same line or wraps.
        // We write short text to stay on line.
        emulator.processOutput("More text")
        val state4 = emulator.screenState.first()

        // Scrollback list should be reused
        assertSame(state3.scrollback, state4.scrollback, "Scrollback list should be reused when not scrolling")
    }
}
