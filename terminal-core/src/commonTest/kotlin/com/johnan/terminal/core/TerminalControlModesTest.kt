package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalControlModesTest {
    @Test
    fun `synchronized output batches updates until mode is reset`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))
        emulator.processOutputSync("init")
        assertEquals("init", emulator.screenState.value.rows[0].take(4).joinToString("") { it.char.toString() })

        // Enable synchronized output (DEC mode 2026)
        emulator.processOutputSync("\u001B[?2026h")
        assertTrue(emulator.synchronizedOutputEnabled)

        // Intermediate writes while synchronized output is active do NOT update screenState
        emulator.processOutputSync("\r\nupdate1")
        assertEquals("init", emulator.screenState.value.rows[0].take(4).joinToString("") { it.char.toString() })
        assertEquals("    ", emulator.screenState.value.rows[1].take(4).joinToString("") { it.char.toString() })

        emulator.processOutputSync("\r\nupdate2")
        assertEquals("    ", emulator.screenState.value.rows[2].take(4).joinToString("") { it.char.toString() })

        // Disable synchronized output flushes the accumulated screen buffer state
        emulator.processOutputSync("\u001B[?2026l")
        assertFalse(emulator.synchronizedOutputEnabled)
        assertEquals("init", emulator.screenState.value.rows[0].take(4).joinToString("") { it.char.toString() })
        assertEquals("update1", emulator.screenState.value.rows[1].take(7).joinToString("") { it.char.toString() })
        assertEquals("update2", emulator.screenState.value.rows[2].take(7).joinToString("") { it.char.toString() })
    }

    @Test
    fun `focus reporting sends escape sequences only when enabled`() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 5, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        // Initially disabled: reporting returns null and sends nothing
        assertFalse(emulator.focusReportingEnabled)
        assertNull(emulator.reportFocusChanged(true))
        assertNull(emulator.reportFocusChanged(false))
        assertTrue(responses.isEmpty())

        // Enable DEC mode 1004
        emulator.processOutputSync("\u001B[?1004h")
        assertTrue(emulator.focusReportingEnabled)

        assertEquals("\u001B[I", emulator.reportFocusChanged(true))
        assertEquals("\u001B[O", emulator.reportFocusChanged(false))
        assertEquals(listOf("\u001B[I", "\u001B[O"), responses)

        // Disable DEC mode 1004
        emulator.processOutputSync("\u001B[?1004l")
        assertFalse(emulator.focusReportingEnabled)
        assertNull(emulator.reportFocusChanged(true))
        assertEquals(2, responses.size)
    }

    @Test
    fun `device attributes and version queries respond with expected sequences`() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 5, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        // Primary Device Attributes (DA1)
        emulator.processOutputSync("\u001B[c")
        assertEquals("\u001B[?1;0c", responses.removeLast())

        emulator.processOutputSync("\u001B[0c")
        assertEquals("\u001B[?1;0c", responses.removeLast())

        // Secondary Device Attributes (DA2)
        emulator.processOutputSync("\u001B[>c")
        assertEquals("\u001B[>0;10;0c", responses.removeLast())

        emulator.processOutputSync("\u001B[>0c")
        assertEquals("\u001B[>0;10;0c", responses.removeLast())

        // XTVERSION query
        emulator.processOutputSync("\u001B[>q")
        assertEquals("\u001BP>|kmp-terminal(0.3.11)\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001B[>0q")
        assertEquals("\u001BP>|kmp-terminal(0.3.11)\u001B\\", responses.removeLast())
    }

    @Test
    fun `decrqm reports mode status and decstr performs soft reset`() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 5, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        // Query mode 2026 (reset by default -> status 2)
        emulator.processOutputSync("\u001B[?2026\$p")
        assertEquals("\u001B[?2026;2\$y", responses.removeLast())

        // Set mode 2026 -> query reports 1
        emulator.processOutputSync("\u001B[?2026h")
        emulator.processOutputSync("\u001B[?2026\$p")
        assertEquals("\u001B[?2026;1\$y", responses.removeLast())

        // Query mode 1004
        emulator.processOutputSync("\u001B[?1004\$p")
        assertEquals("\u001B[?1004;2\$y", responses.removeLast())
        emulator.processOutputSync("\u001B[?1004h")
        emulator.processOutputSync("\u001B[?1004\$p")
        assertEquals("\u001B[?1004;1\$y", responses.removeLast())

        // Query mode 2004 (Bracketed paste)
        emulator.processOutputSync("\u001B[?2004\$p")
        assertEquals("\u001B[?2004;2\$y", responses.removeLast())
        emulator.processOutputSync("\u001B[?2004h")
        emulator.processOutputSync("\u001B[?2004\$p")
        assertEquals("\u001B[?2004;1\$y", responses.removeLast())

        // Query unrecognised mode 9999 -> returns 0
        emulator.processOutputSync("\u001B[?9999\$p")
        assertEquals("\u001B[?9999;0\$y", responses.removeLast())

        // DECSTR soft terminal reset
        emulator.processOutputSync("\u001B[1;31;4m\u001B[?6h\u001B[?7l")
        assertTrue(emulator.originModeEnabled)
        assertFalse(emulator.getScreenBuffer().autoWrapMode)

        emulator.processOutputSync("\u001B[!p")
        assertFalse(emulator.originModeEnabled)
        assertTrue(emulator.getScreenBuffer().autoWrapMode)
    }

    @Test
    fun `decscusr sets cursor shape and blinking`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))

        emulator.processOutputSync("\u001B[0 q")
        assertEquals(CursorShape.BLOCK, emulator.cursorShape)
        assertTrue(emulator.cursorBlinking)

        emulator.processOutputSync("\u001B[1 q")
        assertEquals(CursorShape.BLOCK, emulator.cursorShape)
        assertTrue(emulator.cursorBlinking)

        emulator.processOutputSync("\u001B[2 q")
        assertEquals(CursorShape.BLOCK, emulator.cursorShape)
        assertFalse(emulator.cursorBlinking)

        emulator.processOutputSync("\u001B[3 q")
        assertEquals(CursorShape.UNDERLINE, emulator.cursorShape)
        assertTrue(emulator.cursorBlinking)

        emulator.processOutputSync("\u001B[4 q")
        assertEquals(CursorShape.UNDERLINE, emulator.cursorShape)
        assertFalse(emulator.cursorBlinking)

        emulator.processOutputSync("\u001B[5 q")
        assertEquals(CursorShape.BEAM, emulator.cursorShape)
        assertTrue(emulator.cursorBlinking)

        emulator.processOutputSync("\u001B[6 q")
        assertEquals(CursorShape.BEAM, emulator.cursorShape)
        assertFalse(emulator.cursorBlinking)
    }

    @Test
    fun `extended underlines and colon color formats`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 10, initialCols = 30))

        val seq = "\u001B[4:1mA\u001B[4:2mB\u001B[4:3mC\u001B[4:4mD\u001B[4:5mE\u001B[4:0mF\u001B[21mG\u001B[24mH"
        emulator.processOutputSync(seq)

        val row0 = emulator.getScreenBuffer().getRow(0)
        assertEquals(UnderlineStyle.SINGLE, row0[0].underlineStyle)
        assertTrue(row0[0].underline)
        assertEquals(UnderlineStyle.DOUBLE, row0[1].underlineStyle)
        assertTrue(row0[1].underline)
        assertEquals(UnderlineStyle.CURLY, row0[2].underlineStyle)
        assertTrue(row0[2].underline)
        assertEquals(UnderlineStyle.DOTTED, row0[3].underlineStyle)
        assertTrue(row0[3].underline)
        assertEquals(UnderlineStyle.DASHED, row0[4].underlineStyle)
        assertTrue(row0[4].underline)
        assertEquals(UnderlineStyle.NONE, row0[5].underlineStyle)
        assertFalse(row0[5].underline)
        assertEquals(UnderlineStyle.DOUBLE, row0[6].underlineStyle)
        assertTrue(row0[6].underline)
        assertEquals(UnderlineStyle.NONE, row0[7].underlineStyle)
        assertFalse(row0[7].underline)

        // Underline colors with colon notation
        emulator.processOutputSync("\u001B[2;1H\u001B[58:2::255:128:64m\u001B[4mX")
        val cellX = emulator.getScreenBuffer().getRow(1)[0]
        assertEquals(TerminalColor.Rgb(255, 128, 64), cellX.underlineColor)
        assertEquals(UnderlineStyle.SINGLE, cellX.underlineStyle)

        emulator.processOutputSync("\u001B[58:2:10:20:30mY")
        val cellY = emulator.getScreenBuffer().getRow(1)[1]
        assertEquals(TerminalColor.Rgb(10, 20, 30), cellY.underlineColor)

        emulator.processOutputSync("\u001B[58:5:196mZ")
        val cellZ = emulator.getScreenBuffer().getRow(1)[2]
        assertEquals(TerminalColor.Palette256(196), cellZ.underlineColor)

        // Foreground and background colon notation
        emulator.processOutputSync("\u001B[3;1H\u001B[38:2::1:2:3m\u001B[48:5:42mW")
        val cellW = emulator.getScreenBuffer().getRow(2)[0]
        assertEquals(TerminalColor.Rgb(1, 2, 3), cellW.foregroundColor)
        assertEquals(TerminalColor.Palette256(42), cellW.backgroundColor)
    }

    @Test
    fun `mid-subparameter CSI split and copy preservation`() {
        val original = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))
        original.processOutputSync("\u001B[4:")
        val copy = original.copy()

        original.processOutputSync("3mORIG\u001B[0m")
        copy.processOutputSync("3mCOPY\u001B[0m")

        val origCell = original.getScreenBuffer().getRow(0)[0]
        val copyCell = copy.getScreenBuffer().getRow(0)[0]
        assertEquals(UnderlineStyle.CURLY, origCell.underlineStyle)
        assertEquals(UnderlineStyle.CURLY, copyCell.underlineStyle)
        assertEquals('O', origCell.char)
        assertEquals('C', copyCell.char)

        // Split in middle of colon RGB color
        val e1 = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))
        e1.processOutputSync("\u001B[58:2::100:150:")
        val e2 = e1.copy()

        e1.processOutputSync("200m\u001B[4m1")
        e2.processOutputSync("200m\u001B[4m2")

        val c1 = e1.getScreenBuffer().getRow(0)[0]
        val c2 = e2.getScreenBuffer().getRow(0)[0]
        assertEquals(TerminalColor.Rgb(100, 150, 200), c1.underlineColor)
        assertEquals(TerminalColor.Rgb(100, 150, 200), c2.underlineColor)
    }

    @Test
    fun `resetSync and ris restore new control modes`() {
        val emulator = TerminalEmulator(TerminalConfig(initialRows = 5, initialCols = 20))
        emulator.processOutputSync("\u001B[?2026h\u001B[?1004h\u001B[3 q")
        assertTrue(emulator.synchronizedOutputEnabled)
        assertTrue(emulator.focusReportingEnabled)
        assertEquals(CursorShape.UNDERLINE, emulator.cursorShape)

        // RIS (\ec)
        emulator.processOutputSync("\u001Bc")
        assertFalse(emulator.synchronizedOutputEnabled)
        assertFalse(emulator.focusReportingEnabled)
        assertEquals(CursorShape.BLOCK, emulator.cursorShape)

        emulator.processOutputSync("\u001B[?2026h\u001B[?1004h\u001B[5 q")
        assertTrue(emulator.synchronizedOutputEnabled)
        assertTrue(emulator.focusReportingEnabled)
        assertEquals(CursorShape.BEAM, emulator.cursorShape)

        // resetSync
        emulator.resetSync()
        assertFalse(emulator.synchronizedOutputEnabled)
        assertFalse(emulator.focusReportingEnabled)
        assertEquals(CursorShape.BLOCK, emulator.cursorShape)
    }
}
