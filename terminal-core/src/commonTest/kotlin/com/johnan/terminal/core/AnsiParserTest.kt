package com.johnan.terminal.core
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnsiParserTest {
    private lateinit var terminalEmulator: TerminalEmulator

    @BeforeTest
    fun setup() {
        terminalEmulator = TerminalEmulator(rows = 24, cols = 80)
    }

    @Test
    fun `DECOM mode set should enable origin mode flag`() =
        runTest {
            assertFalse(terminalEmulator.originModeEnabled, "Origin mode should be disabled by default")

            // Process DECOM set mode sequence: CSI ? 6 h
            terminalEmulator.processOutput("\u001B[?6h")

            assertTrue(
                terminalEmulator.originModeEnabled,
                "Origin mode should be enabled after processing DECOM set sequence"
            )
        }

    @Test
    fun `DECOM mode reset should disable origin mode flag`() =
        runTest {
            // First, enable origin mode
            terminalEmulator.processOutput("\u001B[?6h")
            assertTrue(terminalEmulator.originModeEnabled, "Precondition failed: Origin mode was not enabled")

            // Process DECOM reset mode sequence: CSI ? 6 l
            terminalEmulator.processOutput("\u001B[?6l")

            assertFalse(
                terminalEmulator.originModeEnabled,
                "Origin mode should be disabled after processing DECOM reset sequence"
            )
        }

    @Test
    fun `Bracketed paste mode set should enable bracketed paste mode flag`() =
        runTest {
            assertFalse(
                terminalEmulator.bracketedPasteModeEnabled,
                "Bracketed paste mode should be disabled by default"
            )

            // Process Bracketed Paste set mode sequence: CSI ? 2004 h
            terminalEmulator.processOutput("\u001B[?2004h")

            assertTrue(
                terminalEmulator.bracketedPasteModeEnabled,
                "Bracketed paste mode should be enabled after processing sequence"
            )
        }

    @Test
    fun `Bracketed paste mode reset should disable bracketed paste mode flag`() =
        runTest {
            // First, enable bracketed paste mode
            terminalEmulator.processOutput("\u001B[?2004h")
            assertTrue(
                terminalEmulator.bracketedPasteModeEnabled,
                "Precondition failed: Bracketed paste mode was not enabled"
            )

            // Process Bracketed Paste reset mode sequence: CSI ? 2004 l
            terminalEmulator.processOutput("\u001B[?2004l")

            assertFalse(
                terminalEmulator.bracketedPasteModeEnabled,
                "Bracketed paste mode should be disabled after processing reset sequence"
            )
        }

    @Test
    fun `formatPaste should wrap in bracketed paste sequence when enabled and raw when disabled`() =
        runTest {
            val chunk = "first line\nsecond line\nthird line"
            // Initially disabled
            assertEquals(chunk, terminalEmulator.formatPaste(chunk))

            // Enable bracketed paste mode
            terminalEmulator.processOutput("\u001B[?2004h")
            assertEquals("\u001B[200~$chunk\u001B[201~", terminalEmulator.formatPaste(chunk))

            // Disable bracketed paste mode
            terminalEmulator.processOutput("\u001B[?2004l")
            assertEquals(chunk, terminalEmulator.formatPaste(chunk))
        }

    @Test
    fun `Line drawing character set mapping`() =
        runTest {
            // Write normal 'a'
            terminalEmulator.processOutput("a")
            assertEquals('a', terminalEmulator.getScreenBuffer().getCell(0, 0).char)

            // Clear buffer
            terminalEmulator.clear()

            // Set G0 to DEC Special Graphics and ensure we are in G0
            terminalEmulator.processOutput("\u001B(0\u000F")

            // Write 'a' which should map to checkerboard '▒'
            terminalEmulator.processOutput("a")
            assertEquals('▒', terminalEmulator.getScreenBuffer().getCell(0, 0).char)

            // Clear buffer
            terminalEmulator.clear()

            // Revert G0 to ASCII
            terminalEmulator.processOutput("\u001B(B")

            // Write 'a' which should map back to 'a'
            terminalEmulator.processOutput("a")
            assertEquals('a', terminalEmulator.getScreenBuffer().getCell(0, 0).char)
        }

    @Test
    fun `OSC sequence sets window title`() =
        runTest {
            // Process OSC 0
            terminalEmulator.processOutput("\u001B]0;My Title\u0007")
            assertEquals("My Title", terminalEmulator.windowTitle.value)
        }

    @Test
    fun `CSI 22 t and 23 t save and restore window title`() =
        runTest {
            terminalEmulator.processOutput("\u001B]0;Original Title\u0007")
            assertEquals("Original Title", terminalEmulator.windowTitle.value)

            // Save title
            terminalEmulator.processOutput("\u001B[22t")

            terminalEmulator.processOutput("\u001B]0;New Title\u0007")
            assertEquals("New Title", terminalEmulator.windowTitle.value)

            // Restore title
            terminalEmulator.processOutput("\u001B[23t")
            assertEquals("Original Title", terminalEmulator.windowTitle.value)
        }

    @Test
    fun `Primary Device Attributes report`() =
        runTest {
            var responseReceived: String? = null
            terminalEmulator = TerminalEmulator(rows = 24, cols = 80, onTerminalResponse = {
                responseReceived = it
            })

            // Process CSI c (Primary Device Attributes)
            terminalEmulator.processOutput("\u001B[c")

            assertEquals("\u001B[?1;0c", responseReceived)

            responseReceived = null

            // Process CSI 0 c (Primary Device Attributes)
            terminalEmulator.processOutput("\u001B[0c")

            assertEquals("\u001B[?1;0c", responseReceived)
        }

    @Test
    fun `Cursor Position Report`() =
        runTest {
            var responseReceived: String? = null
            terminalEmulator = TerminalEmulator(rows = 24, cols = 80, onTerminalResponse = {
                responseReceived = it
            })

            // Move cursor to row 10, col 20 (0-indexed: 9, 19)
            // CSI row ; col H
            terminalEmulator.processOutput("\u001B[10;20H")

            // Request Cursor Position Report
            terminalEmulator.processOutput("\u001B[6n")

            // Expected 1-based indices in the response
            assertEquals("\u001B[10;20R", responseReceived)
        }

    @Test
    fun `SGR strikethrough overline conceal`() =
        runTest {
            // Write text with attributes
            terminalEmulator.processOutput("\u001B[9mS\u001B[29m") // strikethrough
            terminalEmulator.processOutput("\u001B[53mO\u001B[55m") // overline
            terminalEmulator.processOutput("\u001B[8mC\u001B[28m") // conceal

            val buffer = terminalEmulator.getScreenBuffer()
            val cellS = buffer.getCell(0, 0)
            assertTrue(cellS.strikethrough, "Should have strikethrough")
            assertFalse(cellS.overline, "Should not have overline")
            assertFalse(cellS.conceal, "Should not be concealed")

            val cellO = buffer.getCell(0, 1)
            assertFalse(cellO.strikethrough, "Should not have strikethrough")
            assertTrue(cellO.overline, "Should have overline")
            assertFalse(cellO.conceal, "Should not be concealed")

            val cellC = buffer.getCell(0, 2)
            assertFalse(cellC.strikethrough, "Should not have strikethrough")
            assertFalse(cellC.overline, "Should not have overline")
            assertTrue(cellC.conceal, "Should be concealed")
        }

    @Test
    fun `Terminal bell event triggers`() =
        runTest {
            var bellTriggered = false
            val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
                terminalEmulator.bellEvents.collect {
                    bellTriggered = true
                }
            }

            terminalEmulator.processOutput("\u0007")

            // Allow coroutine to process
            kotlinx.coroutines.delay(50)

            assertTrue(bellTriggered, "Bell event should be triggered")
            job.cancel()
        }

    @Test
    fun `Cursor blinking mode toggling`() =
        runTest {
            assertFalse(terminalEmulator.cursorBlinking)

            terminalEmulator.processOutput("\u001B[?12h")
            assertTrue(terminalEmulator.cursorBlinking)

            terminalEmulator.processOutput("\u001B[?12l")
            assertFalse(terminalEmulator.cursorBlinking)
        }

    @Test
    fun `Reverse video mode toggling`() =
        runTest {
            assertFalse(terminalEmulator.invertScreenColors)

            terminalEmulator.processOutput("\u001B[?5h")
            assertTrue(terminalEmulator.invertScreenColors)

            terminalEmulator.processOutput("\u001B[?5l")
            assertFalse(terminalEmulator.invertScreenColors)
        }

    @Test
    fun `Mouse tracking modes toggling`() = runTest {
        assertEquals(MouseTrackingMode.None, terminalEmulator.mouseTrackingMode)

        terminalEmulator.processOutput("\u001B[?1000h")
        assertEquals(MouseTrackingMode.Click, terminalEmulator.mouseTrackingMode)

        terminalEmulator.processOutput("\u001B[?1000l")
        assertEquals(MouseTrackingMode.None, terminalEmulator.mouseTrackingMode)

        terminalEmulator.processOutput("\u001B[?1002h")
        assertEquals(MouseTrackingMode.CellMotion, terminalEmulator.mouseTrackingMode)

        terminalEmulator.processOutput("\u001B[?1002l")
        assertEquals(MouseTrackingMode.None, terminalEmulator.mouseTrackingMode)

        terminalEmulator.processOutput("\u001B[?1003h")
        assertEquals(MouseTrackingMode.AllMotion, terminalEmulator.mouseTrackingMode)

        terminalEmulator.processOutput("\u001B[?1003l")
        assertEquals(MouseTrackingMode.None, terminalEmulator.mouseTrackingMode)
    }

    @Test
    fun `SGR mouse mode toggling`() = runTest {
        assertFalse(terminalEmulator.sgrMouseModeEnabled)

        terminalEmulator.processOutput("\u001B[?1006h")
        assertTrue(terminalEmulator.sgrMouseModeEnabled)

        terminalEmulator.processOutput("\u001B[?1006l")
        assertFalse(terminalEmulator.sgrMouseModeEnabled)
    }

    @Test
    fun `Scroll Up CSI sequence`() = runTest {
        terminalEmulator.processOutput("Row0\r\nRow1\r\nRow2")
        assertEquals('R', terminalEmulator.getScreenBuffer().getCell(0, 0).char)
        assertEquals('R', terminalEmulator.getScreenBuffer().getCell(1, 0).char)
        assertEquals('R', terminalEmulator.getScreenBuffer().getCell(2, 0).char)

        terminalEmulator.processOutput("\u001B[1S")

        assertEquals('R', terminalEmulator.getScreenBuffer().getCell(0, 0).char)
        assertEquals('o', terminalEmulator.getScreenBuffer().getCell(0, 1).char)
        assertEquals('R', terminalEmulator.getScreenBuffer().getCell(1, 0).char)
        assertEquals(' ', terminalEmulator.getScreenBuffer().getCell(2, 0).char)
    }

    @Test
    fun `Scroll Down CSI sequence`() = runTest {
        terminalEmulator.processOutput("Row0\r\nRow1")

        terminalEmulator.processOutput("\u001B[1T")

        assertEquals(' ', terminalEmulator.getScreenBuffer().getCell(0, 0).char)
        assertEquals('R', terminalEmulator.getScreenBuffer().getCell(1, 0).char)
        assertEquals('R', terminalEmulator.getScreenBuffer().getCell(2, 0).char)
    }

    @Test
    fun `Repeat Character CSI sequence`() = runTest {
        terminalEmulator.processOutput("A")

        terminalEmulator.processOutput("\u001B[5b")

        for (col in 0..5) {
            assertEquals('A', terminalEmulator.getScreenBuffer().getCell(0, col).char)
        }
        assertEquals(' ', terminalEmulator.getScreenBuffer().getCell(0, 6).char)
    }

    @Test
    fun `OSC 52 write sequence with ALWAYS_ALLOW policy should copy payload to clipboard`() = runTest {
        val emulator = TerminalEmulator(
            rows = 24,
            cols = 80,
            osc52Policy = Osc52Policy.ALWAYS_ALLOW,
        )

        // OSC 52 write sequence with base64 for "Hello World" ("SGVsbG8gV29ybGQ=")
        emulator.processOutput("\u001B]52;c;SGVsbG8gV29ybGQ=\u0007")

        assertEquals("Hello World", emulator.clipboardEvents.replayCache.firstOrNull())
    }

    @Test
    fun `OSC 52 write sequence with ALWAYS_DENY policy should ignore payload`() = runTest {
        val emulator = TerminalEmulator(
            rows = 24,
            cols = 80,
            osc52Policy = Osc52Policy.ALWAYS_DENY,
        )

        emulator.processOutput("\u001B]52;c;SGVsbG8gV29ybGQ=\u0007")

        assertEquals(null, emulator.clipboardEvents.replayCache.firstOrNull())
    }

    @Test
    fun `OSC 52 write sequence with ASK policy should invoke prompt callback`() = runTest {
        var promptedText: String? = null
        var confirmAction: (() -> Unit)? = null

        val emulator = TerminalEmulator(
            rows = 24,
            cols = 80,
            osc52Policy = Osc52Policy.ASK,
            onOsc52WriteRequested = { text, onConfirm ->
                promptedText = text
                confirmAction = onConfirm
            },
        )

        emulator.processOutput("\u001B]52;c;SGVsbG8gV29ybGQ=\u0007")

        assertEquals("Hello World", promptedText)
        assertEquals(null, emulator.clipboardEvents.replayCache.firstOrNull())

        // User confirms write prompt
        confirmAction?.invoke()
        assertEquals("Hello World", emulator.clipboardEvents.replayCache.firstOrNull())
    }

    @Test
    fun `OSC 52 read sequence should be ignored for security regardless of policy`() = runTest {
        val emulator = TerminalEmulator(
            rows = 24,
            cols = 80,
            osc52Policy = Osc52Policy.ALWAYS_ALLOW,
        )

        // OSC 52 read request sequence ("?")
        emulator.processOutput("\u001B]52;c;?\u0007")

        assertEquals(null, emulator.clipboardEvents.replayCache.firstOrNull())
    }

    @Test
    fun `OSC 52 write sequence exceeding size limit should be ignored`() = runTest {
        val emulator = TerminalEmulator(
            rows = 24,
            cols = 80,
            osc52Policy = Osc52Policy.ALWAYS_ALLOW,
        )

        // Payload exceeding 65536 bytes
        val hugePayload = "A".repeat(70000)
        emulator.processOutput("\u001B]52;c;$hugePayload\u0007")

        assertEquals(null, emulator.clipboardEvents.replayCache.firstOrNull())
    }
}
