package com.johnan.terminal.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticShellIntegrationTest {
    // -------------------------------------------------------------------------
    // OSC 8 Hyperlinks
    // -------------------------------------------------------------------------

    @Test
    fun `osc 8 sets hyperlink url on target text and null on subsequent text`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 30))
        emulator.processOutputSync("\u001B]8;;https://example.com/docs\u001B\\Click Here\u001B]8;;\u001B\\ plain")
        val row = emulator.screenState.value.rows[0]

        // "Click Here" occupies cols 0..9
        for (col in 0 until 10) {
            assertEquals("https://example.com/docs", row[col].hyperlinkUrl, "Col $col should have hyperlink")
        }
        // " plain" starts at col 10
        for (col in 10 until 30) {
            assertNull(row[col].hyperlinkUrl, "Col $col should not have hyperlink")
        }
    }

    @Test
    fun `osc 8 supports bel and st terminators and id parameters`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 40))
        // Open with id parameter and BEL terminator, close with BEL terminator
        emulator.processOutputSync("\u001B]8;id=link1;https://kotlinlang.org\u0007Kotlin\u001B]8;;\u0007 rest")
        val row = emulator.screenState.value.rows[0]

        // "Kotlin" occupies cols 0..5
        for (col in 0 until 6) {
            assertEquals("https://kotlinlang.org", row[col].hyperlinkUrl, "Col $col with id param and BEL")
        }
        // " rest" starts at col 6
        for (col in 6 until 40) {
            assertNull(row[col].hyperlinkUrl, "Col $col after BEL close should be null")
        }

        // Open with ST terminator and close with BEL terminator
        val emulator2 = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 40))
        emulator2.processOutputSync("\u001B]8;;https://github.com\u001B\\GitHub\u001B]8;;\u0007 after")
        val row2 = emulator2.screenState.value.rows[0]
        for (col in 0 until 6) {
            assertEquals("https://github.com", row2[col].hyperlinkUrl)
        }
        for (col in 6 until 40) {
            assertNull(row2[col].hyperlinkUrl)
        }
    }

    @Test
    fun `getUrlRanges and extractUrl detect explicit osc 8 links and plain text urls`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 60))
        emulator.processOutputSync(
            "\u001B]8;;https://example.com/docs\u001B\\Click Here\u001B]8;;\u001B\\" +
                " and visit https://kotlinlang.org today"
        )
        val row = emulator.screenState.value.rows[0]
        val ranges = ScreenBuffer.getUrlRanges(row)

        assertEquals(2, ranges.size)

        // First range: explicit OSC 8 link for "Click Here"
        val osc8Range = ranges[0]
        assertEquals(0, osc8Range.startCol)
        assertEquals(10, osc8Range.endCol)
        assertEquals("https://example.com/docs", osc8Range.url)
        assertEquals("https://example.com/docs", ScreenBuffer.extractUrl(row, osc8Range))

        // Second range: plain text URL for "https://kotlinlang.org"
        // "Click Here and visit " -> 10 + 11 = 21
        val plainRange = ranges[1]
        val expectedPlainUrl = "https://kotlinlang.org"
        assertEquals(21, plainRange.startCol)
        assertEquals(21 + expectedPlainUrl.length, plainRange.endCol)
        assertEquals(expectedPlainUrl, ScreenBuffer.extractUrl(row, plainRange))
    }

    @Test
    fun `sgr 0 does not clear active osc 8 hyperlink but empty osc 8 ris and resetSync clear it`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 30))

        // SGR 0 (\u001B[0m) does NOT clear active hyperlink
        emulator.processOutputSync("\u001B]8;;https://example.com\u0007Link\u001B[0mStillLink")
        val row = emulator.screenState.value.rows[0]
        for (col in 0 until 13) {
            assertEquals("https://example.com", row[col].hyperlinkUrl, "Col $col should retain link across SGR 0")
        }

        // Empty OSC 8 clears it
        emulator.processOutputSync("\u001B]8;;\u0007EndLink")
        val rowAfterClose = emulator.screenState.value.rows[0]
        for (col in 13 until 20) {
            assertNull(rowAfterClose[col].hyperlinkUrl, "Col $col should have null hyperlink after close")
        }

        // RIS (\u001Bc) clears active hyperlink
        val emulatorRis = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 30))
        emulatorRis.processOutputSync("\u001B]8;;https://example.com\u0007Link\u001BcAfterRis")
        val rowRis = emulatorRis.screenState.value.rows[0]
        for (col in 0 until 8) {
            assertNull(rowRis[col].hyperlinkUrl, "Col $col should have null hyperlink after RIS")
        }

        // resetSync() clears active hyperlink
        val emulatorReset = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 30))
        emulatorReset.processOutputSync("\u001B]8;;https://example.com\u0007Link")
        emulatorReset.resetSync()
        emulatorReset.processOutputSync("AfterReset")
        val rowReset = emulatorReset.screenState.value.rows[0]
        for (col in 0 until 10) {
            assertNull(rowReset[col].hyperlinkUrl, "Col $col should have null hyperlink after resetSync")
        }
    }

    @Test
    fun `soft wrap reflow preserves hyperlinkUrl across wrapped rows`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))
        // 26 chars link: "01234567890123456789ABCDEF"
        emulator.processOutputSync("\u001B]8;;https://example.com\u000701234567890123456789ABCDEF\u001B]8;;\u0007")

        // Reflow by resizing to cols = 10
        emulator.resizeSync(5, 10)
        val state = emulator.screenState.value

        // Row 0 has 10 characters
        for (col in 0 until 10) {
            assertEquals("https://example.com", state.rows[0][col].hyperlinkUrl, "Row 0 col $col")
        }
        // Row 1 has 10 characters
        for (col in 0 until 10) {
            assertEquals("https://example.com", state.rows[1][col].hyperlinkUrl, "Row 1 col $col")
        }
        // Row 2 has 6 characters
        for (col in 0 until 6) {
            assertEquals("https://example.com", state.rows[2][col].hyperlinkUrl, "Row 2 col $col")
        }
        for (col in 6 until 10) {
            assertNull(state.rows[2][col].hyperlinkUrl, "Row 2 col $col empty space")
        }
    }

    // -------------------------------------------------------------------------
    // OSC 7 & OSC 9;9 Current Working Directory
    // -------------------------------------------------------------------------

    @Test
    fun `osc 7 updates currentWorkingDirectory with percent decoding and utf8`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 40))

        emulator.processOutputSync("\u001B]7;file://my-host/Users/dev/my%20project/src\u0007")
        assertEquals("/Users/dev/my project/src", emulator.currentWorkingDirectory.value)
        assertEquals("/Users/dev/my project/src", emulator.screenState.value.currentWorkingDirectory)

        // UTF-8 percent-decoding: %C3%A9 -> é
        emulator.processOutputSync("\u001B]7;file://localhost/home/dev/caf%C3%A9/code\u0007")
        assertEquals("/home/dev/café/code", emulator.currentWorkingDirectory.value)
        assertEquals("/home/dev/café/code", emulator.screenState.value.currentWorkingDirectory)
    }

    @Test
    fun `osc 7 with st terminator updates currentWorkingDirectory`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 40))

        emulator.processOutputSync("\u001B]7;file:///var/log\u001B\\")
        assertEquals("/var/log", emulator.currentWorkingDirectory.value)
        assertEquals("/var/log", emulator.screenState.value.currentWorkingDirectory)
    }

    @Test
    fun `osc 9 9 updates currentWorkingDirectory without triggering attention events`() = runTest {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 40))
        val events = mutableListOf<AttentionEvent>()
        val job = launch(Dispatchers.Unconfined) {
            emulator.attentionEvents.collect { events.add(it) }
        }

        // Windows style quoted path
        emulator.processOutputSync("\u001B]9;9;\"C:\\Users\\dev\"\u0007")
        assertEquals("C:\\Users\\dev", emulator.currentWorkingDirectory.value)
        assertEquals("C:\\Users\\dev", emulator.screenState.value.currentWorkingDirectory)
        assertTrue(events.isEmpty(), "OSC 9;9 must not trigger AttentionEvents")

        // Unix path
        emulator.processOutputSync("\u001B]9;9;/home/ubuntu\u0007")
        assertEquals("/home/ubuntu", emulator.currentWorkingDirectory.value)
        assertEquals("/home/ubuntu", emulator.screenState.value.currentWorkingDirectory)
        assertTrue(events.isEmpty(), "OSC 9;9 must not trigger AttentionEvents")

        job.cancel()
    }

    @Test
    fun `standard osc 9 triggers attention event without modifying currentWorkingDirectory`() = runTest {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 40))
        emulator.processOutputSync("\u001B]9;9;/home/ubuntu\u0007")
        assertEquals("/home/ubuntu", emulator.currentWorkingDirectory.value)

        val events = mutableListOf<AttentionEvent>()
        val job = launch(Dispatchers.Unconfined) {
            emulator.attentionEvents.collect { events.add(it) }
        }

        emulator.processOutputSync("\u001B]9;Build complete\u0007")
        assertEquals(1, events.size)
        assertTrue(events[0] is AttentionEvent.Notification)
        assertEquals("Build complete", (events[0] as AttentionEvent.Notification).message)

        // currentWorkingDirectory should remain unchanged
        assertEquals("/home/ubuntu", emulator.currentWorkingDirectory.value)
        assertEquals("/home/ubuntu", emulator.screenState.value.currentWorkingDirectory)

        job.cancel()
    }

    // -------------------------------------------------------------------------
    // OSC 133 Semantic Prompt Marks
    // -------------------------------------------------------------------------

    @Test
    fun `osc 133 prompt mark sequence updates isCommandRunning and lastExitCode`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 40))

        assertFalse(emulator.isCommandRunning.value)
        assertFalse(emulator.screenState.value.isCommandRunning)
        assertNull(emulator.lastExitCode.value)
        assertNull(emulator.screenState.value.lastExitCode)

        // Prompt start (A)
        emulator.processOutputSync("\u001B]133;A\u0007prompt$ ")
        assertFalse(emulator.isCommandRunning.value)
        assertFalse(emulator.screenState.value.isCommandRunning)

        // Command start (B)
        emulator.processOutputSync("\u001B]133;B\u0007make test\r\n")
        assertFalse(emulator.isCommandRunning.value)
        assertFalse(emulator.screenState.value.isCommandRunning)

        // Command executed / Output start (C)
        emulator.processOutputSync("\u001B]133;C\u0007")
        assertTrue(emulator.isCommandRunning.value)
        assertTrue(emulator.screenState.value.isCommandRunning)

        // Output lines
        emulator.processOutputSync("Running tests...\r\nPASS\r\n")
        assertTrue(emulator.isCommandRunning.value)

        // Command finished with exit code 0 (D;0)
        emulator.processOutputSync("\u001B]133;D;0\u0007")
        assertFalse(emulator.isCommandRunning.value)
        assertFalse(emulator.screenState.value.isCommandRunning)
        assertEquals(0, emulator.lastExitCode.value)
        assertEquals(0, emulator.screenState.value.lastExitCode)

        // Next command: finishes with exit code 127 (D;127) using ST terminator
        emulator.processOutputSync("\u001B]133;A\u001B\\$ \u001B]133;B\u001B\\badcmd\r\n\u001B]133;C\u001B\\")
        assertTrue(emulator.isCommandRunning.value)
        emulator.processOutputSync("command not found\r\n\u001B]133;D;127\u001B\\")
        assertFalse(emulator.isCommandRunning.value)
        assertEquals(127, emulator.lastExitCode.value)
        assertEquals(127, emulator.screenState.value.lastExitCode)
    }

    @Test
    fun `findPreviousPromptLine and findNextPromptLine navigate across prompt marks`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 10, initialCols = 40))

        // First prompt mark at row 0
        emulator.processOutputSync(
            "\u001B]133;A\u0007$ \u001B]133;B\u0007cmd1\r\n\u001B]133;C\u0007out1\r\n\u001B]133;D;0\u0007"
        )
        // Second prompt mark at row 2
        emulator.processOutputSync(
            "\u001B]133;A\u0007$ \u001B]133;B\u0007cmd2\r\n\u001B]133;C\u0007out2\r\n\u001B]133;D;0\u0007"
        )
        // Third prompt mark at row 4
        emulator.processOutputSync(
            "\u001B]133;A\u0007$ \u001B]133;B\u0007cmd3\r\n\u001B]133;C\u0007out3\r\n\u001B]133;D;0\u0007"
        )

        val promptMarks = emulator.getPromptMarks().filter { it.type == PromptMarkType.PROMPT_START }
        assertEquals(3, promptMarks.size)
        val p1 = promptMarks[0].absLine
        val p2 = promptMarks[1].absLine
        val p3 = promptMarks[2].absLine

        assertTrue(p1 < p2 && p2 < p3)

        // Previous navigation
        assertEquals(p2, emulator.findPreviousPromptLine(p3))
        assertEquals(p1, emulator.findPreviousPromptLine(p2))
        assertNull(emulator.findPreviousPromptLine(p1))

        // Next navigation
        assertEquals(p2, emulator.findNextPromptLine(p1))
        assertEquals(p3, emulator.findNextPromptLine(p2))
        assertNull(emulator.findNextPromptLine(p3))
    }

    @Test
    fun `extractLastCommandOutput returns only command output between C and D`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 6, initialCols = 40))

        emulator.processOutputSync(
            "\u001B]133;A\u0007user@box:~$ " +
                "\u001B]133;B\u0007cat greeting.txt\r\n" +
                "\u001B]133;C\u0007Hello World!\r\nLine 2\r\n" +
                "\u001B]133;D;0\u0007"
        )

        val output = emulator.extractLastCommandOutput()
        assertNotNull(output)
        assertEquals("Hello World!\nLine 2", output)
        assertFalse(output.contains("user@box"), "Must not include prompt")
        assertFalse(output.contains("cat greeting.txt"), "Must not include command line")
    }

    @Test
    fun `prompt marks shift absLine accurately when scrollback is full and old lines evicted`() {
        val emulator = TerminalEmulator(
            TerminalConfig(initialRows = 3, initialCols = 20, maxScrollback = 3)
        )

        // Record prompt at initial line
        emulator.processOutputSync(
            "\u001B]133;A\u0007$ \u001B]133;B\u0007first\r\n\u001B]133;C\u0007out\r\n\u001B]133;D;0\u0007"
        )
        val initialPrompt = emulator.getPromptMarks().first { it.type == PromptMarkType.PROMPT_START }
        assertEquals(0, initialPrompt.absLine)

        // Scroll lines off the screen into scrollback:
        // Adding 4 newlines will push lines into scrollback (scrollback max is 3)
        // Line 0 will be evicted once scrollback overflows 3
        emulator.processOutputSync("\r\n1\r\n2\r\n3\r\n4\r\n5\r\n6")

        val currentMarks = emulator.getPromptMarks()
        for (m in currentMarks) {
            assertTrue(m.absLine >= 0, "Mark absLine must remain non-negative after evictions")
        }
    }
}
