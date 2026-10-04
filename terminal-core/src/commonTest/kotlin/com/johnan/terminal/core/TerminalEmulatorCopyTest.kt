package com.johnan.terminal.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalEmulatorCopyTest {
    private val rich =
        "\u001B[31;44mred\u001B[0m \u001B[1;3;4mstyle\u001B[0m\r\n" +
            "0123456789012345678901234567890123456789ABCDEF\r\n" + // wraps on a 20 col screen
            "\u001B[38;2;10;20;30mrgb\u001B[0m\u001B[38;5;200mpal\u001B[0m\r\n" +
            "\u001B(0lqqk\u001B(B\r\n" +
            "\u001B]0;first\u0007\u001B[22;0t\u001B]0;second\u0007" +
            "\u001B[2;4r\u001B[?6h\u001B[?1h\u001B=\u001B[?2004h\u001B[?25l\u001B[?1049hALT"

    private fun newEmulator(
        rows: Int = 6,
        cols: Int = 20,
        config: TerminalConfig = TerminalConfig(initialRows = rows, initialCols = cols, maxScrollback = 50),
    ) = TerminalEmulator(config)

    private data class Snapshot(
        val rows: List<List<TerminalCell>>,
        val scrollback: List<List<TerminalCell>>,
        val cursor: Triple<Int, Int, Boolean>,
        val size: Pair<Int, Int>,
        val contentHeight: Int,
        val flags: List<Any?>,
        val title: String?,
        val bellCount: Long,
        val clipboard: Pair<Long, String?>,
    )

    private fun snapshot(e: TerminalEmulator): Snapshot {
        val s = e.screenState.value
        return Snapshot(
            rows = s.rows.map { it.toList() },
            scrollback = s.scrollback.map { it.toList() },
            cursor = Triple(s.cursorRow, s.cursorCol, s.cursorVisible),
            size = s.terminalRows to s.cols,
            contentHeight = s.contentHeight,
            flags =
                listOf(
                    e.applicationCursorKeysEnabled,
                    e.applicationKeypadModeEnabled,
                    e.originModeEnabled,
                    e.bracketedPasteModeEnabled,
                    e.cursorBlinking,
                    e.invertScreenColors,
                    e.mouseTrackingMode,
                    e.sgrMouseModeEnabled,
                    s.mouseTrackingMode,
                    s.sgrMouseModeEnabled,
                    s.bracketedPasteModeEnabled,
                    e.getScreenBuffer().isUsingAlternateScreen(),
                ),
            title = e.windowTitle.value,
            bellCount = e.bellCount,
            clipboard = e.clipboardWriteCount to e.lastClipboardWrite,
        )
    }

    private suspend fun fresh(
        text: String,
        rows: Int = 6,
        cols: Int = 20,
    ): TerminalEmulator = newEmulator(rows, cols).also { it.processOutput(text) }

    @Test
    fun `copy has same screen state as original for a non-trivial screen`() =
        runTest {
            val original = fresh(rich + "\u001B[?1049l")
            original.processOutput("\u001B[?1049h" + "ALT2")
            val copy = original.copy()

            assertNotSame(original, copy)
            assertEquals(snapshot(original), snapshot(copy))
            assertTrue(copy.originModeEnabled)
            assertTrue(copy.applicationCursorKeysEnabled)
            assertTrue(copy.applicationKeypadModeEnabled)
            assertTrue(copy.bracketedPasteModeEnabled)
            assertFalse(copy.screenState.value.cursorVisible)
            assertEquals("second", copy.windowTitle.value)
            assertTrue(copy.getScreenBuffer().isUsingAlternateScreen())
            assertEquals(original.config, copy.config)
        }

    @Test
    fun `copy of an emulator with scrollback and wrapped lines matches`() =
        runTest {
            val text = (1..15).joinToString("") { "line $it ${"x".repeat(25)}\r\n" }
            val original = fresh(text)
            assertTrue(original.screenState.value.scrollback.isNotEmpty())
            assertEquals(snapshot(original), snapshot(original.copy()))
        }

    @Test
    fun `copy retains pushed window title stack`() =
        runTest {
            val original = fresh("\u001B]0;one\u0007\u001B[22;0t\u001B]0;two\u0007")
            val copy = original.copy()
            copy.processOutput("\u001B[23;0t")
            assertEquals("one", copy.windowTitle.value)
            assertEquals("two", original.windowTitle.value)
            original.processOutput("\u001B[23;0t")
            assertEquals("one", original.windowTitle.value)
        }

    @Test
    fun `original and copy evolve independently`() =
        runTest {
            val original = fresh(rich)
            val copy = original.copy()

            val onlyOriginal = "\r\nO1\u001B[?1049l\u001B[5;1Hprimary-orig\u001B[31m!"
            val onlyCopy = "\u001B[2J\u001B[Hcopy\u001B[1mbold\u001B[?1049l\r\n\r\n\r\n\r\n\r\nscroll\r\nscroll"
            original.processOutput(onlyOriginal)
            copy.processOutput(onlyCopy)

            assertEquals(snapshot(fresh(rich + onlyOriginal)), snapshot(original))
            assertEquals(snapshot(fresh(rich + onlyCopy)), snapshot(copy))
        }

    @Test
    fun `fork mid CSI sequence yields red X in both`() =
        runTest {
            val original = fresh("ab\u001B[3")
            val copy = original.copy()
            original.processOutput("1mX")
            copy.processOutput("1mX")

            val expected = fresh("ab\u001B[31mX")
            assertEquals(snapshot(expected), snapshot(original))
            assertEquals(snapshot(expected), snapshot(copy))
            val cell = copy.screenState.value.rows[0][2]
            assertEquals('X', cell.char)
            assertEquals(TerminalColor.Standard(1), cell.foregroundColor)
        }

    @Test
    fun `fork mid UTF-8 character completes in both`() =
        runTest {
            val bytes = "aéb".encodeToByteArray()
            val original = newEmulator()
            original.processOutput(bytes.copyOfRange(0, 2)) // 'a' + first byte of é
            val copy = original.copy()
            val rest = bytes.copyOfRange(2, bytes.size)
            original.processOutput(rest)
            copy.processOutput(rest)

            val expected = fresh("aéb")
            assertEquals(snapshot(expected), snapshot(original))
            assertEquals(snapshot(expected), snapshot(copy))
            assertEquals('é', copy.screenState.value.rows[0][1].char)
        }

    @Test
    fun `leftover bytes are not shared between original and copy`() =
        runTest {
            val bytes = "é".encodeToByteArray()
            val original = newEmulator()
            original.processOutput(bytes.copyOfRange(0, 1))
            val copy = original.copy()
            original.processOutput("A") // corrupts the pending sequence in the original only
            copy.processOutput(bytes.copyOfRange(1, 2))
            assertEquals('é', copy.screenState.value.rows[0][0].char)
        }

    @Test
    fun `leaving alternate screen in the copy restores primary and leaves original untouched`() =
        runTest {
            val original = fresh("primary\u001B[?1049hALTERNATE")
            val copy = original.copy()
            copy.processOutput("\u001B[?1049l")

            assertFalse(copy.getScreenBuffer().isUsingAlternateScreen())
            assertTrue(original.getScreenBuffer().isUsingAlternateScreen())
            assertEquals("primary", copy.screenState.value.rows[0].take(7).joinToString("") { it.char.toString() })
            assertEquals(
                "ALTERNATE",
                original.screenState.value.rows[0]
                    .take(9)
                    .joinToString("") { it.char.toString() },
            )
            assertEquals(snapshot(fresh("primary\u001B[?1049hALTERNATE\u001B[?1049l")), snapshot(copy))
        }

    @Test
    fun `saved cursor survives fork`() =
        runTest {
            val original = fresh("\u001B[3;5H\u001B7\u001B[1;1H")
            val copy = original.copy()
            original.processOutput("\u001B8X")
            copy.processOutput("\u001B8X")

            assertEquals(snapshot(fresh("\u001B[3;5H\u001B7\u001B[1;1H\u001B8X")), snapshot(copy))
            assertEquals(snapshot(copy), snapshot(original))
            assertEquals('X', copy.screenState.value.rows[2][4].char)
        }

    @Test
    fun `saved cursor set in copy does not leak to original`() =
        runTest {
            val original = fresh("\u001B[2;2H\u001B7")
            val copy = original.copy()
            copy.processOutput("\u001B[4;4H\u001B7")
            original.processOutput("\u001B8Y")
            assertEquals('Y', original.screenState.value.rows[1][1].char)
        }

    @Test
    fun `bellCount increments and survives copy`() =
        runTest {
            val original = newEmulator()
            assertEquals(0L, original.bellCount)
            original.processOutput("\u0007a\u0007")
            assertEquals(2L, original.bellCount)

            val copy = original.copy()
            assertEquals(2L, copy.bellCount)
            copy.processOutput("\u0007")
            assertEquals(3L, copy.bellCount)
            assertEquals(2L, original.bellCount)
        }

    @Test
    fun `clipboard write state updates under every policy and survives copy`() =
        runTest {
            for (policy in Osc52Policy.entries) {
                val emulator = TerminalEmulator(TerminalConfig(osc52Policy = policy))
                assertEquals(0L, emulator.clipboardWriteCount)
                assertNull(emulator.lastClipboardWrite)

                emulator.processOutput("\u001B]52;c;aGk=\u0007") // "hi"
                assertEquals(1L, emulator.clipboardWriteCount, "$policy")
                assertEquals("hi", emulator.lastClipboardWrite, "$policy")

                val copy = emulator.copy()
                assertEquals(1L, copy.clipboardWriteCount, "$policy")
                assertEquals("hi", copy.lastClipboardWrite, "$policy")

                copy.processOutput("\u001B]52;c;eW8=\u0007") // "yo"
                assertEquals(2L, copy.clipboardWriteCount, "$policy")
                assertEquals("yo", copy.lastClipboardWrite, "$policy")
                assertEquals(1L, emulator.clipboardWriteCount, "$policy")
                assertEquals("hi", emulator.lastClipboardWrite, "$policy")
            }
        }

    @Test
    fun `copy callbacks are the ones passed to copy`() =
        runTest {
            val originalRequests = mutableListOf<String>()
            val copyRequests = mutableListOf<String>()
            val responses = mutableListOf<String>()
            val original =
                TerminalEmulator(
                    config = TerminalConfig(osc52Policy = Osc52Policy.ASK),
                    onOsc52WriteRequested = { text, _ -> originalRequests.add(text) },
                )
            val copy =
                original.copy(
                    onOsc52WriteRequested = { text, _ -> copyRequests.add(text) },
                    onTerminalResponse = { responses.add(it) },
                )
            copy.processOutput("\u001B]52;c;aGk=\u0007\u001B[6n")
            assertEquals(listOf("hi"), copyRequests)
            assertTrue(originalRequests.isEmpty())
            assertTrue(responses.isNotEmpty())
        }

    @Test
    fun `resizing the copy does not affect the original`() =
        runTest {
            val text = "abcdefghijklmnopqrstuvwxyz0123456789\r\nsecond"
            val original = fresh(text)
            val before = snapshot(original)
            val copy = original.copy()

            assertTrue(copy.resize(4, 10))
            assertEquals(before, snapshot(original))
            assertEquals(4, copy.screenState.value.terminalRows)
            assertEquals(10, copy.screenState.value.cols)

            val expected = fresh(text)
            expected.resize(4, 10)
            assertEquals(snapshot(expected), snapshot(copy))

            original.resize(8, 30)
            assertEquals(4, copy.screenState.value.terminalRows)
        }

    @Test
    fun `maxScrollback zero does not crash when scrolling past the bottom`() =
        runTest {
            val emulator = TerminalEmulator(TerminalConfig(initialRows = 3, initialCols = 10, maxScrollback = 0))
            emulator.processOutput((1..10).joinToString("\r\n") { "row$it" })
            val state = emulator.screenState.value
            assertTrue(state.scrollback.isEmpty())
            assertEquals("row10", state.rows[2].take(5).joinToString("") { it.char.toString() })
            assertEquals(0L, emulator.getScreenBuffer().scrollbackVersion)
            assertTrue(emulator.copy().screenState.value.scrollback.isEmpty())
        }

    @Test
    fun `maxScrollback zero does not crash when reflow overflows`() =
        runTest {
            val emulator = TerminalEmulator(TerminalConfig(initialRows = 3, initialCols = 20, maxScrollback = 0))
            emulator.processOutput("a".repeat(18) + "\r\n" + "b".repeat(18) + "\r\n" + "c".repeat(18))
            assertTrue(emulator.resize(3, 5))
            assertTrue(emulator.screenState.value.scrollback.isEmpty())
            assertEquals(0L, emulator.getScreenBuffer().scrollbackVersion)
        }

    @Test
    fun `scrollback rows are not mutated by later writes or resize`() =
        runTest {
            val emulator = newEmulator(rows = 3, cols = 10)
            emulator.processOutput("aaaa\r\nbbbb\r\ncccc\r\ndddd")
            val frozen = emulator.screenState.value.scrollback.map { it.toList() }
            val copy = emulator.copy()
            emulator.processOutput("\r\neeee\r\nffff")
            copy.processOutput("\u001B[2J\u001B[H" + "zzzz\r\n".repeat(5))
            copy.resize(3, 4)
            assertEquals(frozen, emulator.screenState.value.scrollback.take(frozen.size).map { it.toList() })
        }

    @Test
    fun `copy at every byte split equals a fresh emulator fed the whole stream`() =
        runTest {
            val streams =
                listOf(
                    rich,
                    "plain text\r\nmore\u0007\r\n\u001B[1;31mbold red\u001B[0m é ü 日本語 😀 end",
                    (1..9).joinToString("") { "row $it \u001B[3${it % 8}m${"w".repeat(it * 3)}\u001B[0m\r\n" },
                    "\u001B[?1049hvim\u001B[2;3H\u001B7\u001B[H\u001B[Kx\u001B8y\u001B[?1049l\u001B[3Aback",
                    "\u001B[2;5r\u001B[?6h\u001B[1;1Htop\n\n\n\n\n\u001B[1Lins\u001B[1M\u001B[2J\u001B[?6l\u001B[r",
                    "\u001B(0lqwqk\u000Ex\u000Fx\u001B(B\u001B)0\u000Elq\u000Fabc\u001B[3b\u001B]52;c;aGk=\u0007\u001B[5n",
                    "tab\tstop\r\u001B[2P\u001B[3@ins\u001B[4X\u001B[2K\u001B[1J\u001B[0Kend\u001B[?7l" + "z".repeat(
                        30
                    ),
                )
            for ((streamIndex, stream) in streams.withIndex()) {
                val bytes = stream.encodeToByteArray()
                val expected = snapshot(newEmulator().also { it.processOutput(bytes) })
                for (k in 0..bytes.size) {
                    val e1 = newEmulator()
                    e1.processOutput(bytes.copyOfRange(0, k))
                    val e2 = e1.copy()
                    e2.processOutput(bytes.copyOfRange(k, bytes.size))
                    assertEquals(expected, snapshot(e2), "stream $streamIndex split at $k")

                    // The original keeps working independently of the copy.
                    e1.processOutput(bytes.copyOfRange(k, bytes.size))
                    assertEquals(expected, snapshot(e1), "stream $streamIndex original after split at $k")
                }
            }
        }

    @Test
    fun `isRowWrapped reports soft wraps and is preserved by copy`() =
        runTest {
            val original = fresh("a".repeat(45) + "\r\nshort")
            val buffer = original.getScreenBuffer()
            assertTrue(buffer.isRowWrapped(0))
            assertTrue(buffer.isRowWrapped(1))
            assertFalse(buffer.isRowWrapped(2))
            assertFalse(buffer.isRowWrapped(3))
            assertFalse(buffer.isRowWrapped(-1))
            assertFalse(buffer.isRowWrapped(99))

            val copyBuffer = original.copy().getScreenBuffer()
            for (row in 0 until 6) {
                assertEquals(buffer.isRowWrapped(row), copyBuffer.isRowWrapped(row), "row $row")
            }
        }

    @Test
    fun `copy of a fresh emulator differs from a modified original only after divergence`() =
        runTest {
            val original = newEmulator()
            val copy = original.copy()
            assertEquals(snapshot(original), snapshot(copy))
            copy.processOutput("x")
            assertNotEquals(snapshot(original), snapshot(copy))
        }
}
