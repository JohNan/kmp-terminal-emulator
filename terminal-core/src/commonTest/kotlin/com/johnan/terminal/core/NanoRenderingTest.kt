package com.johnan.terminal.core
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for nano editor rendering correctness
 *
 * Nano is a correctness benchmark for terminal emulation because it:
 * - Uses explicit cursor positioning (CSI H sequences)
 * - Uses carriage return independently from linefeed
 * - Uses erase-in-line sequences extensively
 * - Expects proper auto-wrap behavior (DECAWM)
 *
 * These tests verify that nano-style editing patterns work correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NanoRenderingTest {
    private lateinit var terminalEmulator: TerminalEmulator

    @BeforeTest
    fun setup() {
        terminalEmulator = TerminalEmulator(rows = 24, cols = 80)
    }

    @Test
    fun testCarriageReturnIndependentFromLineFeed() =
        runTest {
            // Test that \r moves to column 0 without advancing row
            terminalEmulator.processOutput("Hello")
            var state = terminalEmulator.screenState.first()
            assertEquals(0, state.cursorRow)
            assertEquals(5, state.cursorCol)

            // Carriage return should move to column 0, same row
            terminalEmulator.processOutput("\r")
            state = terminalEmulator.screenState.first()
            assertEquals(0, state.cursorRow)
            assertEquals(0, state.cursorCol)

            // Writing now should overwrite the line
            terminalEmulator.processOutput("World")
            state = terminalEmulator.screenState.first()

            val row = state.getRow(0)!!
            assertEquals('W', row[0].char)
            assertEquals('o', row[1].char)
            assertEquals('r', row[2].char)
            assertEquals('l', row[3].char)
            assertEquals('d', row[4].char)
            // Original "Hello" should be overwritten
        }

    @Test
    fun testEraseInLineFromCursor() =
        runTest {
            // Write a line of text
            terminalEmulator.processOutput("Hello World")

            // Move cursor to column 6 (after "Hello ")
            terminalEmulator.processOutput("\u001B[1;7H") // Move to row 1, col 7 (1-indexed)

            // Erase from cursor to end of line (ESC[K or ESC[0K)
            terminalEmulator.processOutput("\u001B[K")

            val state = terminalEmulator.screenState.first()
            val row = state.getRow(0)!!

            // "Hello " should remain
            assertEquals('H', row[0].char)
            assertEquals('e', row[1].char)
            assertEquals('l', row[2].char)
            assertEquals('l', row[3].char)
            assertEquals('o', row[4].char)
            assertEquals(' ', row[5].char)

            // Rest should be empty (space character)
            assertEquals(' ', row[6].char)
            assertEquals(' ', row[7].char)
        }

    @Test
    fun testEraseEntireLine() =
        runTest {
            // Write a line
            terminalEmulator.processOutput("Hello World")

            // Move cursor to middle of line
            terminalEmulator.processOutput("\u001B[1;5H")

            // Erase entire line (ESC[2K)
            terminalEmulator.processOutput("\u001B[2K")

            val state = terminalEmulator.screenState.first()
            val row = state.getRow(0)!!

            // Entire line should be empty
            for (col in 0 until 11) {
                assertEquals(' ', row[col].char, "Column $col should be empty")
            }
        }

    @Test
    fun testCursorAbsolutePositioning() =
        runTest {
            // Move cursor to specific position (row 5, col 10)
            terminalEmulator.processOutput("\u001B[5;10H")

            val state = terminalEmulator.screenState.first()
            assertEquals(4, state.cursorRow) // 0-indexed
            assertEquals(9, state.cursorCol)
        }

    @Test
    fun testCursorRelativeMovement() =
        runTest {
            // Start at origin
            terminalEmulator.processOutput("X")

            // Move right 5 columns (CSI C)
            terminalEmulator.processOutput("\u001B[5C")
            var state = terminalEmulator.screenState.first()
            assertEquals(6, state.cursorCol)

            // Move down 2 rows (CSI B)
            terminalEmulator.processOutput("\u001B[2B")
            state = terminalEmulator.screenState.first()
            assertEquals(2, state.cursorRow)
            assertEquals(6, state.cursorCol)

            // Move left 3 columns (CSI D)
            terminalEmulator.processOutput("\u001B[3D")
            state = terminalEmulator.screenState.first()
            assertEquals(2, state.cursorRow)
            assertEquals(3, state.cursorCol)

            // Move up 1 row (CSI A)
            terminalEmulator.processOutput("\u001B[1A")
            state = terminalEmulator.screenState.first()
            assertEquals(1, state.cursorRow)
            assertEquals(3, state.cursorCol)
        }

    @Test
    fun testNanoStyleLineEditing() =
        runTest {
            // Simulate nano editing a line:
            // 1. Write initial text
            // 2. Move cursor to beginning with \r
            // 3. Erase line
            // 4. Write new text

            // Write initial line
            terminalEmulator.processOutput("Original text here")

            // Carriage return to beginning
            terminalEmulator.processOutput("\r")

            // Erase to end of line
            terminalEmulator.processOutput("\u001B[K")

            // Write new text
            terminalEmulator.processOutput("Modified text")

            val state = terminalEmulator.screenState.first()
            val row = state.getRow(0)!!

            // Should have new text, not intertwined with old
            assertEquals('M', row[0].char)
            assertEquals('o', row[1].char)
            assertEquals('d', row[2].char)
            assertEquals('i', row[3].char)
            assertEquals('f', row[4].char)

            // Rest should be empty (erased)
            assertEquals(' ', row[13].char)
            assertEquals(' ', row[14].char)
        }

    @Test
    fun testAutoWrapBehavior() =
        runTest {
            // Test that writing at the edge of the screen doesn't auto-wrap
            // until we actually write past the edge

            val termWidth = 80

            // Write exactly 80 characters (fill first line)
            val line = "x".repeat(termWidth)
            terminalEmulator.processOutput(line)

            var state = terminalEmulator.screenState.first()

            // After writing 80 chars, cursor should be in "pending wrap" state
            // In correct xterm behavior:
            // - Cursor is at column 79 (last column, visually)
            // - Writing another character will wrap to next line
            // - Explicit cursor movement cancels the pending wrap

            // Write one more character - this should trigger the wrap
            terminalEmulator.processOutput("y")
            state = terminalEmulator.screenState.first()

            // The 'y' should be on row 1, column 0 (wrapped to next line)
            val row1 = state.getRow(1)!!
            assertEquals('y', row1[0].char)
        }

    @Test
    fun testNoImplicitWrapOnExplicitCursorMove() =
        runTest {
            val termWidth = 80

            // Write exactly 80 characters
            terminalEmulator.processOutput("x".repeat(termWidth))

            // Now explicitly move cursor (should cancel any pending wrap)
            terminalEmulator.processOutput("\r") // Carriage return

            // Write a new character - should overwrite first character
            terminalEmulator.processOutput("Z")

            val state = terminalEmulator.screenState.first()
            val row0 = state.getRow(0)!!

            // First character should be 'Z', not 'x'
            assertEquals('Z', row0[0].char)
        }

    @Test
    fun testMultipleLineEditing() =
        runTest {
            // Simulate editing multiple lines like nano would
            // Line 1
            terminalEmulator.processOutput("\u001B[1;1H") // Position row 1, col 1
            terminalEmulator.processOutput("First line of text")

            // Line 2
            terminalEmulator.processOutput("\u001B[2;1H") // Position row 2, col 1
            terminalEmulator.processOutput("Second line of text")

            // Line 3
            terminalEmulator.processOutput("\u001B[3;1H")
            terminalEmulator.processOutput("Third line of text")

            // Now edit line 2 - move to it, erase, rewrite
            terminalEmulator.processOutput("\u001B[2;1H")
            terminalEmulator.processOutput("\u001B[K") // Erase to end
            terminalEmulator.processOutput("Modified second line")

            val state = terminalEmulator.screenState.first()

            // Check line 1 is intact
            val row0 = state.getRow(0)!!
            assertEquals('F', row0[0].char)
            assertEquals('i', row0[1].char)

            // Check line 2 has new content
            val row1 = state.getRow(1)!!
            assertEquals('M', row1[0].char)
            assertEquals('o', row1[1].char)
            assertEquals('d', row1[2].char)

            // Check line 3 is intact
            val row2 = state.getRow(2)!!
            assertEquals('T', row2[0].char)
            assertEquals('h', row2[1].char)
        }

    @Test
    fun testScrollingRegionWithCursorMovement() =
        runTest {
            // Set scrolling region (rows 5-10)
            terminalEmulator.processOutput("\u001B[5;10r")

            // Move to row 10, write text
            terminalEmulator.processOutput("\u001B[10;1H")
            terminalEmulator.processOutput("Line at row 10")

            // Line feed should scroll within region
            terminalEmulator.processOutput("\n")

            val state = terminalEmulator.screenState.first()
            // Cursor should be at row 9 (0-indexed, which is row 10 in 1-indexed)
            assertEquals(9, state.cursorRow)
        }
}
