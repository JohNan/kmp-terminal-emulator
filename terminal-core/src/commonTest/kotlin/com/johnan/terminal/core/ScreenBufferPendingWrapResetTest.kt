package com.johnan.terminal.core

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenBufferPendingWrapResetTest {
    private lateinit var screenBuffer: ScreenBuffer

    @BeforeTest
    fun setUp() {
        screenBuffer = ScreenBuffer(initialRows = 4, initialCols = 5)
    }

    @Test
    fun `clearLine should clear pending wrap before next character`() {
        preparePendingWrapState()
        screenBuffer.clearLine()
        assertTrue(screenBuffer.getCell(TEST_ROW, 0).isDefault())
        assertPendingWrapWasCleared()
    }

    @Test
    fun `eraseCharacters should clear pending wrap before next character`() {
        preparePendingWrapState()
        screenBuffer.eraseCharacters(1)
        assertTrue(screenBuffer.getCell(TEST_ROW, LAST_COL).isDefault())
        assertPendingWrapWasCleared()
    }

    @Test
    fun `insertLines should clear pending wrap before next character`() {
        preparePendingWrapState()
        screenBuffer.setCursorPosition(TEST_ROW + 1, 0)
        screenBuffer.writeChar('N')
        screenBuffer.setCursorPosition(TEST_ROW, LAST_COL)
        screenBuffer.insertLines(1)
        assertTrue(screenBuffer.getCell(TEST_ROW, 0).isDefault())
        assertEquals('B', screenBuffer.getCell(TEST_ROW + 1, 0).char)
        assertPendingWrapWasCleared(assertUntouchedNextRow = false)
    }

    @Test
    fun `deleteLines should clear pending wrap before next character`() {
        preparePendingWrapState()
        screenBuffer.setCursorPosition(TEST_ROW + 1, 0)
        screenBuffer.writeChar('N')
        screenBuffer.setCursorPosition(TEST_ROW, LAST_COL)
        screenBuffer.deleteLines(1)
        assertEquals('N', screenBuffer.getCell(TEST_ROW, 0).char)
        assertPendingWrapWasCleared(assertUntouchedNextRow = false)
    }

    @Test
    fun `deleteCharacters should clear pending wrap before next character`() {
        preparePendingWrapState()
        screenBuffer.deleteCharacters(1)
        assertTrue(screenBuffer.getCell(TEST_ROW, LAST_COL).isDefault())
        assertPendingWrapWasCleared()
    }

    @Test
    fun `insertCharacters should clear pending wrap before next character`() {
        preparePendingWrapState()
        screenBuffer.insertCharacters(1)
        assertTrue(screenBuffer.getCell(TEST_ROW, LAST_COL).isDefault())
        assertPendingWrapWasCleared()
    }

    private fun preparePendingWrapState() {
        // Put buffer into pending-wrap state: write at final column with DECAWM enabled.
        screenBuffer.setCursorPosition(TEST_ROW, 0)
        screenBuffer.writeChar('B')
        screenBuffer.setCursorPosition(TEST_ROW, LAST_COL)
        screenBuffer.writeChar('A')
        assertEquals(TEST_ROW, screenBuffer.cursorRow)
        assertEquals(LAST_COL, screenBuffer.cursorCol)
        assertEquals('A', screenBuffer.getCell(TEST_ROW, LAST_COL).char)
    }

    private fun assertPendingWrapWasCleared(assertUntouchedNextRow: Boolean = true) {
        // Next char must not wrap to the next row.
        screenBuffer.writeChar('X')
        assertEquals(TEST_ROW, screenBuffer.cursorRow, "cursor row should remain stable after pending-wrap reset")
        assertEquals(
            'X',
            screenBuffer.getCell(TEST_ROW, LAST_COL).char,
            "character should be written at current row/last column"
        )
        if (assertUntouchedNextRow) {
            assertTrue(screenBuffer.getCell(TEST_ROW + 1, 0).isDefault(), "next row col 0 should remain untouched")
            assertTrue(screenBuffer.getCell(TEST_ROW + 1, 1).isDefault(), "next row col 1 should remain untouched")
        }
    }

    companion object {
        private const val TEST_ROW = 1
        private const val LAST_COL = 4
    }
}
