package com.sshclient.data.terminal
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ScreenBufferTest {
    private lateinit var screenBuffer: ScreenBuffer

    @BeforeTest
    fun setup() {
        screenBuffer = ScreenBuffer(initialRows = 24, initialCols = 80)
    }

    @Test
    fun `setCursorPosition should work correctly in absolute mode`() {
        screenBuffer.setCursorPosition(row = 10, col = 20)
        assertEquals(10, screenBuffer.cursorRow, "Cursor row should be set absolutely")
        assertEquals(20, screenBuffer.cursorCol, "Cursor col should be set absolutely")
    }

    @Test
    fun `setCursorPosition should respect origin mode and scrolling region`() {
        // Set a scrolling region from row 5 to 15 (0-indexed)
        screenBuffer.setScrollingRegion(top = 5, bottom = 15)

        // Set cursor position with origin mode enabled. The row should be relative to scrollTop.
        // A relative row of 2 should result in an absolute row of 5 + 2 = 7.
        screenBuffer.setCursorPosition(row = 2, col = 10, originMode = true)

        assertEquals(7, screenBuffer.cursorRow, "Cursor row should be relative to scrollTop in origin mode")
        assertEquals(10, screenBuffer.cursorCol, "Cursor col should be set as specified")
    }

    @Test
    fun `setCursorPosition should clamp row to scrolling region in origin mode`() {
        // Set a scrolling region from row 5 to 15
        screenBuffer.setScrollingRegion(top = 5, bottom = 15)

        // Attempt to set cursor position outside the scrolling region with origin mode enabled.
        // A relative row of 20 should be clamped to the scrollBottom (15).
        screenBuffer.setCursorPosition(row = 20, col = 10, originMode = true)

        assertEquals(15, screenBuffer.cursorRow, "Cursor row should be clamped to scrollBottom in origin mode")
    }

    @Test
    fun `setCursorPosition should not be affected by scrolling region without origin mode`() {
        // Set a scrolling region
        screenBuffer.setScrollingRegion(top = 5, bottom = 15)

        // Set cursor position without origin mode. It should be absolute to the screen.
        screenBuffer.setCursorPosition(row = 2, col = 10, originMode = false)

        assertEquals(2, screenBuffer.cursorRow, "Cursor row should be absolute when origin mode is off")
    }

    @Test
    fun `resize should preserve content`() {
        // Setup initial content
        screenBuffer.resize(24, 80)
        screenBuffer.setCursorPosition(0, 0)
        screenBuffer.writeChar('A')
        screenBuffer.setCursorPosition(0, 79)
        screenBuffer.writeChar('B')
        screenBuffer.setCursorPosition(23, 0)
        screenBuffer.writeChar('C')

        // Resize larger (vertical)
        screenBuffer.resize(40, 80)
        assertEquals('A', screenBuffer.getCell(0, 0).char)
        assertEquals('B', screenBuffer.getCell(0, 79).char)
        assertEquals('C', screenBuffer.getCell(23, 0).char)
        assertEquals(40, screenBuffer.rows)

        // Resize smaller (vertical)
        screenBuffer.resize(20, 80)
        assertEquals('A', screenBuffer.getCell(0, 0).char)
        assertEquals('B', screenBuffer.getCell(0, 79).char)
        // C is cut off
        assertEquals(20, screenBuffer.rows)

        // Resize larger (horizontal)
        screenBuffer.resize(20, 100)
        assertEquals('A', screenBuffer.getCell(0, 0).char)
        assertEquals('B', screenBuffer.getCell(0, 79).char)
        assertEquals(100, screenBuffer.cols)

        // Resize smaller (horizontal)
        screenBuffer.resize(20, 40)
        assertEquals('A', screenBuffer.getCell(0, 0).char)
        // B is cut off
        assertEquals(40, screenBuffer.cols)
    }

    @Test
    fun `getUrlRanges should detect URLs correctly`() {
        val row = Array(80) { TerminalCell.EMPTY }
        val url = "https://example.com"
        val text = "Visit $url now"

        // Populate row
        text.forEachIndexed { index, char ->
            row[index] = TerminalCell.create(char)
        }

        val ranges = ScreenBuffer.getUrlRanges(row)
        assertEquals(1, ranges.size)
        assertEquals(url, ScreenBuffer.extractUrl(row, ranges[0]))
        assertEquals(6, ranges[0].startCol)
        assertEquals(6 + url.length, ranges[0].endCol)
    }

    @Test
    fun `getUrlRanges should cache results`() {
        val row = Array(80) { TerminalCell.EMPTY }
        val url = "https://example.com"
        val text = "Visit $url now"

        // Populate row
        text.forEachIndexed { index, char ->
            row[index] = TerminalCell.create(char)
        }

        val ranges1 = ScreenBuffer.getUrlRanges(row)
        val ranges2 = ScreenBuffer.getUrlRanges(row)

        // Should be same instance due to caching
        assertSame(ranges1, ranges2)
    }

    @Test
    fun `writeText should wrap correctly`() {
        screenBuffer.setCursorPosition(0, 0)
        // Write text that is exactly 2 lines long (80 * 2 = 160 chars)
        val text = "A".repeat(160)
        screenBuffer.writeText(text)

        // After 160 chars, we fill row 0 and row 1.
        // Cursor should be at the end of row 1 with pending wrap.
        // We can verify this by writing one more char, which should end up on row 2.
        screenBuffer.writeChar('X')

        assertEquals(2, screenBuffer.cursorRow)
        assertEquals(1, screenBuffer.cursorCol)
        assertEquals('X', screenBuffer.getCell(2, 0).char)
    }

    @Test
    fun `writeText should respect autoWrapMode`() {
        screenBuffer.setAutoWrapMode(false)
        screenBuffer.setCursorPosition(0, 0)
        val text = "A".repeat(85) // 85 chars, width 80
        screenBuffer.writeText(text)

        // Should fill line 0, and stay at end. Rows should not increment.
        assertEquals(0, screenBuffer.cursorRow)
        assertEquals(79, screenBuffer.cursorCol)
    }

    @Test
    fun `writeText should handle pending wrap from previous state`() {
        screenBuffer.setCursorPosition(0, 79)
        screenBuffer.writeChar('A') // Triggers pending wrap

        // Now call writeText
        screenBuffer.writeText("BC")

        // 'B' should wrap to next line (1, 0)
        // 'C' should be at (1, 1)
        assertEquals(1, screenBuffer.cursorRow)
        assertEquals(2, screenBuffer.cursorCol)
        assertEquals('B', screenBuffer.getCell(1, 0).char)
        assertEquals('C', screenBuffer.getCell(1, 1).char)
    }
}
