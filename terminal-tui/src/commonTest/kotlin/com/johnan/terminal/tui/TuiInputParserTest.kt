package com.johnan.terminal.tui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TuiInputParserTest {
    private val parser = TuiInputParser()

    @Test
    fun testParseSimpleCharacters() {
        val bytes = "hello".encodeToByteArray()
        val events = parser.parse(bytes)

        assertEquals(5, events.size)
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'h'), events[0])
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'e'), events[1])
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'l'), events[2])
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'l'), events[3])
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'o'), events[4])
    }

    @Test
    fun testParseUtf8Characters() {
        val bytes = "€".encodeToByteArray()
        val events = parser.parse(bytes)

        assertEquals(1, events.size)
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = '€'), events[0])
    }

    @Test
    fun testParseControlKeys() {
        val enterCr = parser.parse(byteArrayOf(13))
        assertEquals(1, enterCr.size)
        assertEquals(TuiEvent.Key(TuiKey.ENTER), enterCr[0])

        val enterLf = parser.parse(byteArrayOf(10))
        assertEquals(1, enterLf.size)
        assertEquals(TuiEvent.Key(TuiKey.ENTER), enterLf[0])

        val enterCrLf = parser.parse(byteArrayOf(13, 10))
        assertEquals(1, enterCrLf.size)
        assertEquals(TuiEvent.Key(TuiKey.ENTER), enterCrLf[0])

        val tab = parser.parse(byteArrayOf(9))
        assertEquals(1, tab.size)
        assertEquals(TuiEvent.Key(TuiKey.TAB), tab[0])

        val backspaceDel = parser.parse(byteArrayOf(127))
        assertEquals(1, backspaceDel.size)
        assertEquals(TuiEvent.Key(TuiKey.BACKSPACE), backspaceDel[0])

        val backspaceBs = parser.parse(byteArrayOf(8))
        assertEquals(1, backspaceBs.size)
        assertEquals(TuiEvent.Key(TuiKey.BACKSPACE), backspaceBs[0])

        val escape = parser.parse(byteArrayOf(0x1B))
        assertEquals(1, escape.size)
        assertEquals(TuiEvent.Key(TuiKey.ESCAPE), escape[0])

        // Ctrl+C (code 3)
        val ctrlC = parser.parse(byteArrayOf(3))
        assertEquals(1, ctrlC.size)
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'c', ctrl = true), ctrlC[0])

        // Alt+X (\u001b followed by 'x')
        val altX = parser.parse(byteArrayOf(0x1B, 'x'.code.toByte()))
        assertEquals(1, altX.size)
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'x', alt = true), altX[0])
    }

    @Test
    fun testParseArrowAndNavigationKeys() {
        assertEquals(TuiEvent.Key(TuiKey.UP), parser.parse("\u001b[A".encodeToByteArray())[0])
        assertEquals(TuiEvent.Key(TuiKey.DOWN), parser.parse("\u001b[B".encodeToByteArray())[0])
        assertEquals(TuiEvent.Key(TuiKey.RIGHT), parser.parse("\u001b[C".encodeToByteArray())[0])
        assertEquals(TuiEvent.Key(TuiKey.LEFT), parser.parse("\u001b[D".encodeToByteArray())[0])

        assertEquals(TuiEvent.Key(TuiKey.HOME), parser.parse("\u001b[H".encodeToByteArray())[0])
        assertEquals(TuiEvent.Key(TuiKey.END), parser.parse("\u001b[F".encodeToByteArray())[0])
        assertEquals(TuiEvent.Key(TuiKey.DELETE), parser.parse("\u001b[3~".encodeToByteArray())[0])
        assertEquals(TuiEvent.Key(TuiKey.PAGE_UP), parser.parse("\u001b[5~".encodeToByteArray())[0])
        assertEquals(TuiEvent.Key(TuiKey.PAGE_DOWN), parser.parse("\u001b[6~".encodeToByteArray())[0])

        // SS3 arrow keys
        assertEquals(TuiEvent.Key(TuiKey.UP), parser.parse("\u001bOA".encodeToByteArray())[0])

        // Backtab (Shift+Tab)
        assertEquals(TuiEvent.Key(TuiKey.TAB, shift = true), parser.parse("\u001b[Z".encodeToByteArray())[0])

        // Shift+Up (modifier = 2 -> shift)
        val shiftUp = parser.parse("\u001b[1;2A".encodeToByteArray())[0]
        assertEquals(TuiEvent.Key(TuiKey.UP, shift = true), shiftUp)

        // Ctrl+Right (modifier = 5 -> ctrl)
        val ctrlRight = parser.parse("\u001b[1;5C".encodeToByteArray())[0]
        assertEquals(TuiEvent.Key(TuiKey.RIGHT, ctrl = true), ctrlRight)
    }

    @Test
    fun testParseSgrMouse() {
        // Press at 1-indexed col 15, row 25 -> 0-indexed col 14, row 24
        val press = parser.parse("\u001b[<0;15;25M".encodeToByteArray())
        assertEquals(1, press.size)
        val mousePress = assertIs<TuiEvent.Mouse>(press[0])
        assertEquals(0, mousePress.button)
        assertEquals(14, mousePress.col)
        assertEquals(24, mousePress.row)
        assertFalse(mousePress.released)
        assertFalse(mousePress.wheelUp)
        assertFalse(mousePress.wheelDown)

        // Release
        val release = parser.parse("\u001b[<0;15;25m".encodeToByteArray())
        val mouseRelease = assertIs<TuiEvent.Mouse>(release[0])
        assertTrue(mouseRelease.released)

        // Wheel Up (btn 64)
        val wheelUp = parser.parse("\u001b[<64;5;5M".encodeToByteArray())
        val mouseWheelUp = assertIs<TuiEvent.Mouse>(wheelUp[0])
        assertTrue(mouseWheelUp.wheelUp)

        // Wheel Down (btn 65)
        val wheelDown = parser.parse("\u001b[<65;5;5M".encodeToByteArray())
        val mouseWheelDown = assertIs<TuiEvent.Mouse>(wheelDown[0])
        assertTrue(mouseWheelDown.wheelDown)
    }

    @Test
    fun testParseBracketedPaste() {
        val pasteSeq = "\u001b[200~Pasted Content Line 1\nLine 2\u001b[201~".encodeToByteArray()
        val events = parser.parse(pasteSeq)

        assertEquals(1, events.size)
        val paste = assertIs<TuiEvent.Paste>(events[0])
        assertEquals("Pasted Content Line 1\nLine 2", paste.text)
    }

    @Test
    fun testToAnsiSequence() {
        assertEquals("\r", parser.toAnsiSequence(TuiEvent.Key(TuiKey.ENTER)))
        assertEquals("\u007f", parser.toAnsiSequence(TuiEvent.Key(TuiKey.BACKSPACE)))
        assertEquals("\t", parser.toAnsiSequence(TuiEvent.Key(TuiKey.TAB)))
        assertEquals("\u001b[Z", parser.toAnsiSequence(TuiEvent.Key(TuiKey.TAB, shift = true)))
        assertEquals("\u001b", parser.toAnsiSequence(TuiEvent.Key(TuiKey.ESCAPE)))

        assertEquals("\u001b[A", parser.toAnsiSequence(TuiEvent.Key(TuiKey.UP)))
        assertEquals("\u001bOA", parser.toAnsiSequence(TuiEvent.Key(TuiKey.UP), applicationCursorKeys = true))
        assertEquals("\u001b[1;2A", parser.toAnsiSequence(TuiEvent.Key(TuiKey.UP, shift = true)))

        assertEquals("\u0003", parser.toAnsiSequence(TuiEvent.Key(TuiKey.CHAR, char = 'c', ctrl = true)))
        assertEquals("\u001ba", parser.toAnsiSequence(TuiEvent.Key(TuiKey.CHAR, char = 'a', alt = true)))
        assertEquals("x", parser.toAnsiSequence(TuiEvent.Key(TuiKey.CHAR, char = 'x')))
    }

    @Test
    fun testParseKittyKeyboardSequences() {
        val escapeEvent = parser.parse("\u001b[27u".encodeToByteArray())
        assertEquals(1, escapeEvent.size)
        assertEquals(TuiEvent.Key(TuiKey.ESCAPE), escapeEvent[0])

        val shiftEnterEvent = parser.parse("\u001b[13;2u".encodeToByteArray())
        assertEquals(1, shiftEnterEvent.size)
        assertEquals(TuiEvent.Key(TuiKey.ENTER, shift = true), shiftEnterEvent[0])

        val ctrlAEvent = parser.parse("\u001b[97;5u".encodeToByteArray())
        assertEquals(1, ctrlAEvent.size)
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'a', ctrl = true), ctrlAEvent[0])

        val shiftAEvent = parser.parse("\u001b[97:65;2u".encodeToByteArray())
        assertEquals(1, shiftAEvent.size)
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'A', shift = true), shiftAEvent[0])

        val releaseAEvent = parser.parse("\u001b[97;1:3u".encodeToByteArray())
        assertEquals(1, releaseAEvent.size)
        assertEquals(TuiEvent.Key(TuiKey.CHAR, char = 'a', eventType = 3), releaseAEvent[0])

        val repeatUpEvent = parser.parse("\u001b[1;2:2A".encodeToByteArray())
        assertEquals(1, repeatUpEvent.size)
        assertEquals(TuiEvent.Key(TuiKey.UP, shift = true, eventType = 2), repeatUpEvent[0])
    }

    @Test
    fun testToAnsiSequenceWithKittyKeyboard() {
        assertEquals(
            "\u001b[27u",
            parser.toAnsiSequence(TuiEvent.Key(TuiKey.ESCAPE), kittyKeyboardFlags = 1),
        )
        assertEquals(
            "\u001b[99;5u",
            parser.toAnsiSequence(
                TuiEvent.Key(TuiKey.CHAR, char = 'c', ctrl = true),
                kittyKeyboardFlags = 1,
            ),
        )
        assertEquals(
            "\u001b[97;1:3u",
            parser.toAnsiSequence(
                TuiEvent.Key(TuiKey.CHAR, char = 'a', eventType = 3),
                kittyKeyboardFlags = 3,
            ),
        )
        assertEquals(
            "\u001b[13u",
            parser.toAnsiSequence(TuiEvent.Key(TuiKey.ENTER), kittyKeyboardFlags = 8),
        )
    }
}
