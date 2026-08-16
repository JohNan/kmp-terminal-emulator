package com.johnan.terminal.core
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenBufferCharManipulationTest {
    private lateinit var screenBuffer: ScreenBuffer

    @BeforeTest
    fun setUp() {
        screenBuffer = ScreenBuffer(10, 20)
    }

    @Test
    fun `deleteCharacters should shift characters left and fill with empty`() {
        // Setup: Write "0123456789"
        screenBuffer.setCursorPosition(0, 0)
        screenBuffer.writeText("0123456789")

        // Move cursor to index 2 ("2")
        screenBuffer.setCursorPosition(0, 2)

        // Delete 3 characters (should delete "234")
        screenBuffer.deleteCharacters(3)

        // Expected: "0156789   "
        val row = screenBuffer.getRow(0)
        assertEquals('0', row[0].char)
        assertEquals('1', row[1].char)
        assertEquals('5', row[2].char)
        assertEquals('6', row[3].char)
        assertEquals('7', row[4].char)
        assertEquals('8', row[5].char)
        assertEquals('9', row[6].char)
        assertEquals(' ', row[7].char)
        assertEquals(' ', row[8].char)
        assertEquals(' ', row[9].char)
    }

    @Test
    fun `deleteCharacters should clamp count`() {
        screenBuffer.setCursorPosition(0, 0)
        screenBuffer.writeText("0123456789")
        screenBuffer.setCursorPosition(0, 15) // Near end

        // Try to delete more than available
        screenBuffer.deleteCharacters(100)

        // Should not crash
    }

    @Test
    fun `insertCharacters should shift characters right and fill with empty`() {
        // Setup: Write "01234"
        screenBuffer.setCursorPosition(0, 0)
        screenBuffer.writeText("01234")

        // Move cursor to index 2 ("2")
        screenBuffer.setCursorPosition(0, 2)

        // Insert 3 characters
        screenBuffer.insertCharacters(3)

        // Expected: "01   234" (assuming buffer width is enough, but characters fall off if not)
        // With width 20, "01234" is at start. "01   234" uses 8 chars.
        val row = screenBuffer.getRow(0)
        assertEquals('0', row[0].char)
        assertEquals('1', row[1].char)
        assertEquals(' ', row[2].char)
        assertEquals(' ', row[3].char)
        assertEquals(' ', row[4].char)
        assertEquals('2', row[5].char)
        assertEquals('3', row[6].char)
        assertEquals('4', row[7].char)
    }

    @Test
    fun `insertCharacters should drop characters at end of line`() {
        // Fill line with "01234567890123456789" (20 chars)
        screenBuffer.setCursorPosition(0, 0)
        val text = "01234567890123456789"
        screenBuffer.writeText(text)

        // Cursor at 0
        screenBuffer.setCursorPosition(0, 0)

        // Insert 1 char
        screenBuffer.insertCharacters(1)

        // Expected: " 0123456789012345678" (last '9' is lost)
        val row = screenBuffer.getRow(0)
        assertEquals(' ', row[0].char)
        assertEquals('0', row[1].char)
        assertEquals('8', row[19].char)
    }

    @Test
    fun `eraseCharacters should clear characters without shifting`() {
        // Setup: Write "0123456789"
        screenBuffer.setCursorPosition(0, 0)
        screenBuffer.writeText("0123456789")

        // Move cursor to index 2 ("2")
        screenBuffer.setCursorPosition(0, 2)

        // Erase 3 characters ("234")
        screenBuffer.eraseCharacters(3)

        // Expected: "01   56789"
        val row = screenBuffer.getRow(0)
        assertEquals('0', row[0].char)
        assertEquals('1', row[1].char)
        assertEquals(' ', row[2].char)
        assertEquals(' ', row[3].char)
        assertEquals(' ', row[4].char)
        assertEquals('5', row[5].char)
        assertEquals('6', row[6].char)
    }
}
