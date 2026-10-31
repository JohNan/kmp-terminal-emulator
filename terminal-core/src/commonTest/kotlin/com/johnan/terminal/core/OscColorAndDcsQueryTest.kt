package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OscColorAndDcsQueryTest {
    @Test
    fun testOscColorSpecParsingAndFormatting() {
        assertEquals(0xFF00AA, TerminalPalette.parseOscColorSpec("#f0a"))
        assertEquals(0x112233, TerminalPalette.parseOscColorSpec("#112233"))
        assertEquals(0x112233, TerminalPalette.parseOscColorSpec("#111122223333"))
        assertEquals(0xFF00AA, TerminalPalette.parseOscColorSpec("rgb:f/0/a"))
        assertEquals(0x112233, TerminalPalette.parseOscColorSpec("rgb:11/22/33"))
        assertEquals(0x112233, TerminalPalette.parseOscColorSpec("rgb:1111/2222/3333"))

        assertNull(TerminalPalette.parseOscColorSpec("invalid"))
        assertNull(TerminalPalette.parseOscColorSpec("rgb:1/2"))
        assertNull(TerminalPalette.parseOscColorSpec("#12"))
        assertNull(TerminalPalette.parseOscColorSpec(""))
        assertNull(TerminalPalette.parseOscColorSpec("#gggggg"))

        assertEquals("rgb:1e1e/2b2b/3c3c", TerminalPalette.formatOscRgbResponse(0x1E2B3C))
    }

    @Test
    fun testOsc10And11And12QueriesAndOverrides() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 5, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        emulator.processOutputSync("\u001B]10;?\u001B\\")
        assertEquals("\u001B]10;rgb:e0e0/e0e0/e0e0\u001B\\", responses.removeLast())
        emulator.processOutputSync("\u001B]11;?\u0007")
        assertEquals("\u001B]11;rgb:1e1e/1e1e/1e1e\u001B\\", responses.removeLast())
        emulator.processOutputSync("\u001B]12;?\u001B\\")
        assertEquals("\u001B]12;rgb:e0e0/e0e0/e0e0\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001B]10;#123456\u0007")
        emulator.processOutputSync("\u001B]11;rgb:aa/bb/cc\u001B\\")
        emulator.processOutputSync("\u001B]12;#abcdef\u0007")

        assertEquals(0x123456, emulator.dynamicForegroundRgb)
        assertEquals(0xAABBCC, emulator.dynamicBackgroundRgb)
        assertEquals(0xABCDEF, emulator.dynamicCursorRgb)
        assertEquals(0x123456, emulator.screenState.value.dynamicForegroundRgb)
        assertEquals(0xAABBCC, emulator.screenState.value.dynamicBackgroundRgb)
        assertEquals(0xABCDEF, emulator.screenState.value.dynamicCursorRgb)
        assertEquals(0x123456, emulator.getEffectiveForegroundRgb())
        assertEquals(0xAABBCC, emulator.getEffectiveBackgroundRgb())
        assertEquals(0xABCDEF, emulator.getEffectiveCursorRgb())

        emulator.processOutputSync("\u001B]10;?\u0007")
        assertEquals("\u001B]10;rgb:1212/3434/5656\u001B\\", responses.removeLast())
        emulator.processOutputSync("\u001B]11;?\u001B\\")
        assertEquals("\u001B]11;rgb:aaaa/bbbb/cccc\u001B\\", responses.removeLast())
        emulator.processOutputSync("\u001B]12;?\u0007")
        assertEquals("\u001B]12;rgb:abab/cdcd/efef\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001B]10;#111111;#222222;#333333\u0007")
        assertEquals(0x111111, emulator.dynamicForegroundRgb)
        assertEquals(0x222222, emulator.dynamicBackgroundRgb)
        assertEquals(0x333333, emulator.dynamicCursorRgb)

        emulator.processOutputSync("\u001B]110\u0007")
        assertNull(emulator.dynamicForegroundRgb)
        assertNull(emulator.screenState.value.dynamicForegroundRgb)
        assertEquals(TerminalPalette.DEFAULT.foreground, emulator.getEffectiveForegroundRgb())

        emulator.processOutputSync("\u001B]111;\u0007")
        assertNull(emulator.dynamicBackgroundRgb)
        assertNull(emulator.screenState.value.dynamicBackgroundRgb)
        assertEquals(TerminalPalette.DEFAULT.background, emulator.getEffectiveBackgroundRgb())

        emulator.processOutputSync("\u001B]112\u001B\\")
        assertNull(emulator.dynamicCursorRgb)
        assertNull(emulator.screenState.value.dynamicCursorRgb)
        assertEquals(TerminalPalette.DEFAULT.cursor, emulator.getEffectiveCursorRgb())
    }

    @Test
    fun testOsc4And104PaletteOverrides() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 5, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        emulator.processOutputSync("\u001B]4;1;?\u0007")
        assertEquals("\u001B]4;1;rgb:cdcd/0000/0000\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001B]4;1;#ff5500;2;rgb:00/ff/88\u0007")
        assertEquals(0xFF5500, emulator.getDynamicPaletteColor(1))
        assertEquals(0x00FF88, emulator.getDynamicPaletteColor(2))
        assertEquals(0xFF5500, emulator.getEffectivePaletteRgb(1))
        assertEquals(0x00FF88, emulator.getEffectivePaletteRgb(2))

        emulator.processOutputSync("\u001B]4;1;?\u001B\\")
        assertEquals("\u001B]4;1;rgb:ffff/5555/0000\u001B\\", responses.removeLast())
        emulator.processOutputSync("\u001B]4;2;?\u001B\\")
        assertEquals("\u001B]4;2;rgb:0000/ffff/8888\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001B]104;1\u0007")
        assertNull(emulator.getDynamicPaletteColor(1))
        assertEquals(0x00FF88, emulator.getDynamicPaletteColor(2))
        assertEquals(TerminalPalette.DEFAULT.red, emulator.getEffectivePaletteRgb(1))

        emulator.processOutputSync("\u001B]104\u0007")
        assertNull(emulator.getDynamicPaletteColor(2))
        assertEquals(TerminalPalette.DEFAULT.green, emulator.getEffectivePaletteRgb(2))
    }

    @Test
    fun testXtgettcapQueries() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 5, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        emulator.processOutputSync("\u001BP+q544E;524742\u001B\\")
        assertEquals(
            listOf(
                "\u001BP1+r544E=787465726D2D323536636F6C6F72\u001B\\",
                "\u001BP1+r524742\u001B\\",
            ),
            responses,
        )
        responses.clear()

        emulator.processOutputSync("\u001BP+q5A5A5A5A\u001B\\")
        assertEquals(listOf("\u001BP0+r\u001B\\"), responses)
    }

    @Test
    fun testDecrqssQueries() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 24, initialCols = 80),
            onTerminalResponse = { responses.add(it) },
        )

        emulator.processOutputSync("\u001BP\$qm\u001B\\")
        assertEquals("\u001BP1\$r0m\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001B[5;20r")
        emulator.processOutputSync("\u001BP\$qr\u001B\\")
        assertEquals("\u001BP1\$r5;20r\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001B[6 q")
        emulator.processOutputSync("\u001BP\$q q\u001B\\")
        assertEquals("\u001BP1\$r6 q\u001B\\", responses.removeLast())

        emulator.processOutputSync("\u001BP\$qx\u001B\\")
        assertEquals("\u001BP0\$r\u001B\\", responses.removeLast())
    }

    @Test
    fun testResetClearsDynamicColors() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 5, initialCols = 20))
        emulator.processOutputSync(
            "\u001B]10;#112233\u0007\u001B]11;#445566\u0007\u001B]12;#778899\u0007\u001B]4;1;#ff0000\u0007",
        )
        assertEquals(0x112233, emulator.dynamicForegroundRgb)
        assertEquals(0x445566, emulator.dynamicBackgroundRgb)
        assertEquals(0x778899, emulator.dynamicCursorRgb)
        assertEquals(0xFF0000, emulator.getDynamicPaletteColor(1))

        emulator.processOutputSync("\u001Bc")
        assertNull(emulator.dynamicForegroundRgb)
        assertNull(emulator.dynamicBackgroundRgb)
        assertNull(emulator.dynamicCursorRgb)
        assertNull(emulator.getDynamicPaletteColor(1))
        assertNull(emulator.screenState.value.dynamicForegroundRgb)
        assertNull(emulator.screenState.value.dynamicBackgroundRgb)
        assertNull(emulator.screenState.value.dynamicCursorRgb)

        emulator.processOutputSync(
            "\u001B]10;#112233\u0007\u001B]11;#445566\u0007\u001B]12;#778899\u0007\u001B]4;1;#ff0000\u0007",
        )
        assertEquals(0x112233, emulator.dynamicForegroundRgb)
        assertEquals(0x445566, emulator.dynamicBackgroundRgb)
        assertEquals(0x778899, emulator.dynamicCursorRgb)
        assertEquals(0xFF0000, emulator.getDynamicPaletteColor(1))

        emulator.resetSync()
        assertNull(emulator.dynamicForegroundRgb)
        assertNull(emulator.dynamicBackgroundRgb)
        assertNull(emulator.dynamicCursorRgb)
        assertNull(emulator.getDynamicPaletteColor(1))
        assertNull(emulator.screenState.value.dynamicForegroundRgb)
        assertNull(emulator.screenState.value.dynamicBackgroundRgb)
        assertNull(emulator.screenState.value.dynamicCursorRgb)
    }
}
