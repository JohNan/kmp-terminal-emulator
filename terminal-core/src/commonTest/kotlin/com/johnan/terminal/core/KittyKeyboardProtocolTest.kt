package com.johnan.terminal.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KittyKeyboardProtocolTest {
    @Test
    fun testPushPopAndQueryFlags() =
        runTest {
            var lastResponse: String? = null
            val emulator = TerminalEmulator(
                config = TerminalConfig(initialRows = 24, initialCols = 80),
                onTerminalResponse = { lastResponse = it },
            )

            assertEquals(0, emulator.kittyKeyboardFlags)
            assertEquals(0, emulator.screenState.value.kittyKeyboardFlags)

            // Push flag 1
            emulator.processOutput("\u001B[>1u")
            assertEquals(1, emulator.kittyKeyboardFlags)
            assertEquals(1, emulator.screenState.value.kittyKeyboardFlags)

            // Query current flags
            emulator.processOutput("\u001B[?u")
            assertEquals("\u001B[?1u", lastResponse)

            // Push flag 3
            emulator.processOutput("\u001B[>3u")
            assertEquals(3, emulator.kittyKeyboardFlags)
            assertEquals(3, emulator.screenState.value.kittyKeyboardFlags)

            // Query current flags
            emulator.processOutput("\u001B[?u")
            assertEquals("\u001B[?3u", lastResponse)

            // Pop 1 entry -> restores previous flag 1
            emulator.processOutput("\u001B[<1u")
            assertEquals(1, emulator.kittyKeyboardFlags)
            assertEquals(1, emulator.screenState.value.kittyKeyboardFlags)

            // Pop 5 entries (underflow) -> restores to 0
            emulator.processOutput("\u001B[<5u")
            assertEquals(0, emulator.kittyKeyboardFlags)
            assertEquals(0, emulator.screenState.value.kittyKeyboardFlags)

            emulator.processOutput("\u001B[?u")
            assertEquals("\u001B[?0u", lastResponse)
        }

    @Test
    fun testStackOverflowEvictsOldest() =
        runTest {
            val emulator = TerminalEmulator(
                config = TerminalConfig(initialRows = 24, initialCols = 80),
            )

            // Push 18 distinct values (1..18)
            for (i in 1..18) {
                emulator.processOutput("\u001B[>${i}u")
                assertEquals(i, emulator.kittyKeyboardFlags)
            }

            // Max depth is 16: values 0 and 1 were evicted.
            // Pop 16 entries: should produce 17 down to 2 in LIFO order
            for (expected in 17 downTo 2) {
                emulator.processOutput("\u001B[<1u")
                assertEquals(expected, emulator.kittyKeyboardFlags)
            }

            // 17th pop underflows and resets flags to 0
            emulator.processOutput("\u001B[<1u")
            assertEquals(0, emulator.kittyKeyboardFlags)
        }

    @Test
    fun testSetFlagsModes() =
        runTest {
            val emulator = TerminalEmulator(
                config = TerminalConfig(initialRows = 24, initialCols = 80),
            )

            // Mode 1: replace -> 5
            emulator.processOutput("\u001B[=5;1u")
            assertEquals(5, emulator.kittyKeyboardFlags)
            assertEquals(5, emulator.screenState.value.kittyKeyboardFlags)

            // Mode 2: bitwise OR -> 5 or 2 = 7
            emulator.processOutput("\u001B[=2;2u")
            assertEquals(7, emulator.kittyKeyboardFlags)
            assertEquals(7, emulator.screenState.value.kittyKeyboardFlags)

            // Mode 3: bitwise AND NOT -> 7 and not 4 = 3
            emulator.processOutput("\u001B[=4;3u")
            assertEquals(3, emulator.kittyKeyboardFlags)
            assertEquals(3, emulator.screenState.value.kittyKeyboardFlags)
        }

    @Test
    fun testDisambiguateFromScorc() =
        runTest {
            val emulator = TerminalEmulator(
                config = TerminalConfig(initialRows = 24, initialCols = 80),
            )

            // Move cursor to row 2, col 3 (1-indexed: row 3, col 4)
            emulator.processOutput("\u001B[3;4H")
            assertEquals(2, emulator.screenState.value.cursorRow)
            assertEquals(3, emulator.screenState.value.cursorCol)

            // Save cursor position via SCOSC (\u001B[s)
            emulator.processOutput("\u001B[s")

            // Move cursor to row 10, col 10
            emulator.processOutput("\u001B[10;10H")
            assertEquals(9, emulator.screenState.value.cursorRow)
            assertEquals(9, emulator.screenState.value.cursorCol)

            // Push kitty keyboard flags (\u001B[>1u)
            emulator.processOutput("\u001B[>1u")
            assertEquals(1, emulator.kittyKeyboardFlags)

            // Restore cursor via SCORC (\u001B[u without parameters)
            emulator.processOutput("\u001B[u")

            // Cursor restored to row 2, col 3 while kittyKeyboardFlags remains 1
            assertEquals(2, emulator.screenState.value.cursorRow)
            assertEquals(3, emulator.screenState.value.cursorCol)
            assertEquals(1, emulator.kittyKeyboardFlags)
        }

    @Test
    fun testPrimaryAndAlternateScreenStackIsolation() =
        runTest {
            val emulator = TerminalEmulator(
                config = TerminalConfig(initialRows = 24, initialCols = 80),
            )

            // Push flags 3 on primary screen
            emulator.processOutput("\u001B[>3u")
            assertEquals(3, emulator.kittyKeyboardFlags)
            assertEquals(3, emulator.screenState.value.kittyKeyboardFlags)

            // Switch to alternate screen (\u001B[?1049h) -> flags should be 0
            emulator.processOutput("\u001B[?1049h")
            assertEquals(0, emulator.kittyKeyboardFlags)
            assertEquals(0, emulator.screenState.value.kittyKeyboardFlags)

            // Push flags 15 on alternate screen
            emulator.processOutput("\u001B[>15u")
            assertEquals(15, emulator.kittyKeyboardFlags)
            assertEquals(15, emulator.screenState.value.kittyKeyboardFlags)

            // Exit alternate screen (\u001B[?1049l) -> restores primary screen flags (3)
            emulator.processOutput("\u001B[?1049l")
            assertEquals(3, emulator.kittyKeyboardFlags)
            assertEquals(3, emulator.screenState.value.kittyKeyboardFlags)

            // Re-entering alternate screen resets alternate screen flags to 0
            emulator.processOutput("\u001B[?1049h")
            assertEquals(0, emulator.kittyKeyboardFlags)
            assertEquals(0, emulator.screenState.value.kittyKeyboardFlags)
        }

    @Test
    fun testKeyEncodingWithProgressiveFlags() {
        val flag1 = KittyKeyboardProtocol.DISAMBIGUATE_ESCAPE_CODES
        val flag2 = KittyKeyboardProtocol.REPORT_EVENT_TYPES
        val flag4 = KittyKeyboardProtocol.REPORT_ALTERNATE_KEYS
        val flag8 = KittyKeyboardProtocol.REPORT_ALL_KEYS_AS_ESCAPE_CODES
        val flag16 = KittyKeyboardProtocol.REPORT_ASSOCIATED_TEXT

        // Flag 0 (legacy)
        assertEquals(
            "\u001B",
            KittyKeyboardProtocol.encodeKeyEvent(codepoint = 27, flags = 0),
        )
        assertEquals(
            "\u0003",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 'c'.code,
                modifiers = KittyKeyboardProtocol.MODIFIER_CTRL,
                flags = 0,
            ),
        )
        assertEquals(
            "",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 'a'.code,
                eventType = KeyEventType.RELEASE,
                flags = 0,
            ),
        )

        // Flag 1 (DISAMBIGUATE_ESCAPE_CODES)
        assertEquals(
            "\u001B[27u",
            KittyKeyboardProtocol.encodeKeyEvent(codepoint = 27, flags = flag1),
        )
        assertEquals(
            "\u001B[97;5u",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 'a'.code,
                modifiers = KittyKeyboardProtocol.MODIFIER_CTRL,
                flags = flag1,
            ),
        )
        assertEquals(
            "\u001B[13;2u",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 13,
                modifiers = KittyKeyboardProtocol.MODIFIER_SHIFT,
                flags = flag1,
            ),
        )
        assertEquals(
            "a",
            KittyKeyboardProtocol.encodeKeyEvent(codepoint = 'a'.code, flags = flag1),
        )
        assertEquals(
            "\r",
            KittyKeyboardProtocol.encodeKeyEvent(codepoint = 13, flags = flag1),
        )

        // Flag 2 (REPORT_EVENT_TYPES)
        assertEquals(
            "\u001B[97;5:2u",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 'a'.code,
                modifiers = KittyKeyboardProtocol.MODIFIER_CTRL,
                eventType = KeyEventType.REPEAT,
                flags = flag1 or flag2,
            ),
        )
        assertEquals(
            "\u001B[27;1:3u",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 27,
                eventType = KeyEventType.RELEASE,
                flags = flag1 or flag2,
            ),
        )

        // Flag 4 (REPORT_ALTERNATE_KEYS)
        assertEquals(
            "\u001B[97:65;2u",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 'a'.code,
                shiftedCodepoint = 'A'.code,
                modifiers = KittyKeyboardProtocol.MODIFIER_SHIFT,
                flags = flag1 or flag4 or flag8,
            ),
        )

        // Flag 8 (REPORT_ALL_KEYS_AS_ESCAPE_CODES)
        assertEquals(
            "\u001B[97u",
            KittyKeyboardProtocol.encodeKeyEvent(codepoint = 'a'.code, flags = flag8),
        )
        assertEquals(
            "\u001B[13u",
            KittyKeyboardProtocol.encodeKeyEvent(codepoint = 13, flags = flag8),
        )
        assertEquals(
            "\u001B[9u",
            KittyKeyboardProtocol.encodeKeyEvent(codepoint = 9, flags = flag8),
        )

        // Flag 16 (REPORT_ASSOCIATED_TEXT)
        assertEquals(
            "\u001B[97;1;97u",
            KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = 'a'.code,
                associatedText = "a",
                flags = flag8 or flag16,
            ),
        )

        // Emulator delegate with active flags
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 24, initialCols = 80),
        )
        assertEquals("\u001B", emulator.encodeKeyEvent(codepoint = 27))
        emulator.processOutputSync("\u001B[>1u")
        assertEquals("\u001B[27u", emulator.encodeKeyEvent(codepoint = 27))
        assertEquals(
            "\u001B[97;5u",
            emulator.encodeKeyEvent(
                codepoint = 'a'.code,
                modifiers = KittyKeyboardProtocol.MODIFIER_CTRL,
            ),
        )
    }

    @Test
    fun testResetClearsKittyKeyboardFlags() =
        runTest {
            val emulator = TerminalEmulator(
                config = TerminalConfig(initialRows = 24, initialCols = 80),
            )

            emulator.processOutput("\u001B[>5u")
            assertEquals(5, emulator.kittyKeyboardFlags)
            assertEquals(5, emulator.screenState.value.kittyKeyboardFlags)

            // RIS (\u001Bc) resets flags and stacks
            emulator.processOutput("\u001Bc")
            assertEquals(0, emulator.kittyKeyboardFlags)
            assertEquals(0, emulator.screenState.value.kittyKeyboardFlags)

            emulator.processOutput("\u001B[>7u")
            assertEquals(7, emulator.kittyKeyboardFlags)
            assertEquals(7, emulator.screenState.value.kittyKeyboardFlags)

            // resetSync() resets flags and stacks
            emulator.resetSync()
            assertEquals(0, emulator.kittyKeyboardFlags)
            assertEquals(0, emulator.screenState.value.kittyKeyboardFlags)
        }
}
