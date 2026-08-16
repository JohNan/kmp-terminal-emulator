package com.sshclient.data.terminal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Nano-specific sequences that were causing rendering issues:
 * - VPA (Vertical Position Absolute): ESC [ <n> d
 * - CHA (Cursor Horizontal Absolute): ESC [ <n> G
 * - Keypad Modes: ESC = and ESC >
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NanoVpaChaTest {
    private lateinit var terminalEmulator: TerminalEmulator

    @BeforeTest
    fun setup() {
        terminalEmulator = TerminalEmulator(rows = 24, cols = 80)
    }

    @Test
    fun testVpa_VerticalPositionAbsolute() =
        runTest {
            // Start at 0,0
            var state = terminalEmulator.screenState.first()
            assertEquals(0, state.cursorRow)
            assertEquals(0, state.cursorCol)

            // Move to row 10 (1-indexed) -> row 9 (0-indexed)
            // ESC [ 10 d
            terminalEmulator.processOutput("\u001B[10d")

            state = terminalEmulator.screenState.first()
            assertEquals(9, state.cursorRow)
            assertEquals(0, state.cursorCol) // Column should remain unchanged

            // Move to row 5 (1-indexed) -> row 4 (0-indexed)
            // ESC [ 5 d
            terminalEmulator.processOutput("\u001B[5d")

            state = terminalEmulator.screenState.first()
            assertEquals(4, state.cursorRow)
            assertEquals(0, state.cursorCol)
        }

    @Test
    fun testVpa_WithColumnOffset() =
        runTest {
            // Move to 0, 5 first
            terminalEmulator.processOutput("\u001B[1;6H")
            var state = terminalEmulator.screenState.first()
            assertEquals(0, state.cursorRow)
            assertEquals(5, state.cursorCol)

            // VPA to row 10
            terminalEmulator.processOutput("\u001B[10d")

            state = terminalEmulator.screenState.first()
            assertEquals(9, state.cursorRow)
            assertEquals(5, state.cursorCol) // Column should still be 5
        }

    @Test
    fun testCha_CursorHorizontalAbsolute() =
        runTest {
            // Start at 0,0
            var state = terminalEmulator.screenState.first()
            assertEquals(0, state.cursorRow)
            assertEquals(0, state.cursorCol)

            // Move to col 20 (1-indexed) -> col 19 (0-indexed)
            // ESC [ 20 G
            terminalEmulator.processOutput("\u001B[20G")

            state = terminalEmulator.screenState.first()
            assertEquals(0, state.cursorRow)
            assertEquals(19, state.cursorCol)

            // Move to col 5 (1-indexed) -> col 4 (0-indexed)
            // ESC [ 5 G
            terminalEmulator.processOutput("\u001B[5G")

            state = terminalEmulator.screenState.first()
            assertEquals(0, state.cursorRow)
            assertEquals(4, state.cursorCol)
        }

    @Test
    fun testCha_WithRowOffset() =
        runTest {
            // Move to 5, 0 first
            terminalEmulator.processOutput("\u001B[6;1H")
            var state = terminalEmulator.screenState.first()
            assertEquals(5, state.cursorRow)
            assertEquals(0, state.cursorCol)

            // CHA to col 20
            terminalEmulator.processOutput("\u001B[20G")

            state = terminalEmulator.screenState.first()
            assertEquals(5, state.cursorRow)
            assertEquals(19, state.cursorCol)
        }

    @Test
    fun testKeypadModes() =
        runTest {
            // Default should be false (Normal mode)
            assertFalse(terminalEmulator.applicationKeypadModeEnabled)

            // ESC = : Application Keypad Mode
            terminalEmulator.processOutput("\u001B=")
            assertTrue(terminalEmulator.applicationKeypadModeEnabled)

            // ESC > : Normal Keypad Mode
            terminalEmulator.processOutput("\u001B>")
            assertFalse(terminalEmulator.applicationKeypadModeEnabled)
        }
}
