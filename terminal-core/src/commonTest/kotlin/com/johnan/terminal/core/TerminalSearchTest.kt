package com.johnan.terminal.core
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalSearchTest {
    @Test
    fun testSearch() {
        val buffer = ScreenBuffer(initialRows = 24, initialCols = 80)

        // Helper to write string
        fun writeString(text: String) {
            for (char in text) {
                if (char == '\n') {
                    buffer.lineFeed()
                    buffer.carriageReturn()
                } else {
                    buffer.writeChar(char)
                }
            }
        }

        // 1. Setup: Populate buffer
        // Line 0: "Error: Connection failed..."
        writeString("Error: Connection failed...")
        buffer.lineFeed()
        buffer.carriageReturn()

        // Line 1: "Retrying..."
        writeString("Retrying...")
        buffer.lineFeed()
        buffer.carriageReturn()

        // Line 2: "Error: Retry"
        writeString("Error: Retry")

        // 2. Action: Search "Error"
        val matches = buffer.search("Error")

        // 3. Assertion
        assertEquals(2, matches.size)

        // First match: "Error" at (0, 0)
        assertEquals(TerminalMatch(0, 0, 0, 5), matches[0])

        // Second match: "Error" at (2, 0)
        assertEquals(TerminalMatch(2, 0, 2, 5), matches[1])
    }

    @Test
    fun testSearchIgnoreCase() {
        val buffer = ScreenBuffer(initialRows = 24, initialCols = 80)

        fun writeString(text: String) {
            for (char in text) {
                if (char == '\n') {
                    buffer.lineFeed()
                    buffer.carriageReturn()
                } else {
                    buffer.writeChar(char)
                }
            }
        }

        writeString("error: test")

        val matches = buffer.search("ERROR", ignoreCase = true)
        assertEquals(1, matches.size)
        assertEquals(TerminalMatch(0, 0, 0, 5), matches[0])
    }

    @Test
    fun testSearchWithScrollback() {
        // Create small buffer to force scrollback
        val buffer = ScreenBuffer(initialRows = 2, initialCols = 20)

        fun writeString(text: String) {
            for (char in text) {
                if (char == '\n') {
                    buffer.lineFeed()
                    buffer.carriageReturn()
                } else {
                    buffer.writeChar(char)
                }
            }
        }

        // Line 0 (will move to scrollback)
        writeString("Found match 1")
        buffer.lineFeed()
        buffer.carriageReturn()

        // Line 1 (will move to row 0)
        writeString("Processing...")
        buffer.lineFeed()
        buffer.carriageReturn()

        // Line 2 (at row 1)
        writeString("Found match 2")

        // Now:
        // Scrollback[0]: "Found match 1"
        // Row 0: "Processing..."
        // Row 1: "Found match 2"

        val matches = buffer.search("Found")

        assertEquals(2, matches.size)

        // Match 1 in scrollback (index 0)
        assertEquals(TerminalMatch(0, 0, 0, 5), matches[0])

        // Match 2 in visible buffer (index 2 = 1 scrollback + 1 visible)
        // Wait, rows are:
        // 0: scrollback item 0
        // 1: buffer row 0
        // 2: buffer row 1

        assertEquals(TerminalMatch(2, 0, 2, 5), matches[1])
    }

    @Test
    fun testMultiMatchInSameLine() {
        val buffer = ScreenBuffer(initialRows = 5, initialCols = 80)

        fun writeString(text: String) {
            for (char in text) {
                buffer.writeChar(char)
            }
        }

        writeString("foo bar foo")

        val matches = buffer.search("foo")
        assertEquals(2, matches.size)
        assertEquals(TerminalMatch(0, 0, 0, 3), matches[0])
        assertEquals(TerminalMatch(0, 8, 0, 11), matches[1])
    }
}
