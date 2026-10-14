package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalPaletteTest {
    @Test
    fun testPalettePresets() {
        assertEquals(5, TerminalPalette.PRESETS.size)
        assertEquals(TerminalPalette.DEFAULT, TerminalPalette.getById("default"))
        assertEquals(TerminalPalette.SOLARIZED_DARK, TerminalPalette.getById("solarized_dark"))
        assertEquals(TerminalPalette.SOLARIZED_LIGHT, TerminalPalette.getById("solarized_light"))
        assertEquals(TerminalPalette.MONOKAI, TerminalPalette.getById("monokai"))
        assertEquals(TerminalPalette.NORD, TerminalPalette.getById("nord"))
        assertNull(TerminalPalette.getById("non_existent"))

        val defaultPalette = TerminalPalette.DEFAULT
        assertEquals(0x1E1E1E, defaultPalette.background)
        assertEquals(0xE0E0E0, defaultPalette.foreground)
        assertEquals(0x000000, defaultPalette.getAnsiRgb(0))
        assertEquals(0xCD0000, defaultPalette.getAnsiRgb(1))
        assertEquals(0x00CD00, defaultPalette.getAnsiRgb(2))
        assertEquals(0xCDCD00, defaultPalette.getAnsiRgb(3))
        assertEquals(0x0000EE, defaultPalette.getAnsiRgb(4))
        assertEquals(0xCD00CD, defaultPalette.getAnsiRgb(5))
        assertEquals(0x00CDCD, defaultPalette.getAnsiRgb(6))
        assertEquals(0xE5E5E5, defaultPalette.getAnsiRgb(7))
        assertEquals(0x7F7F7F, defaultPalette.getAnsiRgb(8))
        assertEquals(0xFF0000, defaultPalette.getAnsiRgb(9))
        assertEquals(0x00FF00, defaultPalette.getAnsiRgb(10))
        assertEquals(0xFFFF00, defaultPalette.getAnsiRgb(11))
        assertEquals(0x5C5CFF, defaultPalette.getAnsiRgb(12))
        assertEquals(0xFF00FF, defaultPalette.getAnsiRgb(13))
        assertEquals(0x00FFFF, defaultPalette.getAnsiRgb(14))
        assertEquals(0xFFFFFF, defaultPalette.getAnsiRgb(15))
        assertEquals(defaultPalette.foreground, defaultPalette.getAnsiRgb(99))
    }

    @Test
    fun testTerminalColorToRgbIntDefault() {
        // Default without palette
        val fgDark = TerminalColor.toRgbInt(
            terminalColor = TerminalColor.Default,
            palette = null,
            isDark = true,
            isBackground = false
        )
        assertEquals(0xE0E0E0, fgDark)

        val fgLight = TerminalColor.toRgbInt(
            terminalColor = TerminalColor.Default,
            palette = null,
            isDark = false,
            isBackground = false
        )
        assertEquals(0x000000, fgLight)

        val bgWithoutPalette = TerminalColor.toRgbInt(
            terminalColor = TerminalColor.Default,
            palette = null,
            isDark = true,
            isBackground = true
        )
        assertEquals(TerminalColor.TRANSPARENT_RGB, bgWithoutPalette)

        // Default with palette
        val palette = TerminalPalette.SOLARIZED_DARK
        val fgWithPalette = TerminalColor.toRgbInt(
            terminalColor = TerminalColor.Default,
            palette = palette,
            isDark = true,
            isBackground = false
        )
        assertEquals(palette.foreground, fgWithPalette)

        val bgWithPalette = TerminalColor.toRgbInt(
            terminalColor = TerminalColor.Default,
            palette = palette,
            isDark = true,
            isBackground = true
        )
        assertEquals(palette.background, bgWithPalette)
    }

    @Test
    fun testTerminalColorToRgbIntStandard() {
        // Standard without palette
        val black = TerminalColor.toRgbInt(TerminalColor.Standard(0), palette = null, isDark = true)
        assertEquals(0x000000, black)

        val red = TerminalColor.toRgbInt(TerminalColor.Standard(1), palette = null, isDark = true)
        assertEquals(0xCD0000, red)

        val white = TerminalColor.toRgbInt(TerminalColor.Standard(7), palette = null, isDark = true)
        assertEquals(0xE5E5E5, white)

        val fallbackDark = TerminalColor.toRgbInt(TerminalColor.Standard(100), palette = null, isDark = true)
        assertEquals(0xE0E0E0, fallbackDark)

        val fallbackLight = TerminalColor.toRgbInt(TerminalColor.Standard(100), palette = null, isDark = false)
        assertEquals(0x000000, fallbackLight)

        // Standard with palette
        val palette = TerminalPalette.MONOKAI
        val pRed = TerminalColor.toRgbInt(TerminalColor.Standard(1), palette = palette)
        assertEquals(palette.red, pRed)
    }

    @Test
    fun testTerminalColorToRgbIntPalette256() {
        // Standard mapping for code 0
        val p0 = TerminalColor.toRgbInt(TerminalColor.Palette256(0))
        assertEquals(0x000000, p0)

        // 6x6x6 color cube: code 16 -> black (0, 0, 0)
        val p16 = TerminalColor.toRgbInt(TerminalColor.Palette256(16))
        assertEquals(0x000000, p16)

        // code 231 -> white (255, 255, 255)
        val p231 = TerminalColor.toRgbInt(TerminalColor.Palette256(231))
        assertEquals(0xFFFFFF, p231)

        // code 196 -> red (255, 0, 0)
        val p196 = TerminalColor.toRgbInt(TerminalColor.Palette256(196))
        assertEquals(0xFF0000, p196)

        // Grayscale ramp
        // code 232 -> gray 8
        val p232 = TerminalColor.toRgbInt(TerminalColor.Palette256(232))
        assertEquals(0x080808, p232)

        // code 255 -> gray 238 (0xEEEEEE)
        val p255 = TerminalColor.toRgbInt(TerminalColor.Palette256(255))
        assertEquals(0xEEEEEE, p255)

        // Out of bounds fallback
        val p999 = TerminalColor.toRgbInt(TerminalColor.Palette256(999))
        assertEquals(0xE0E0E0, p999)
    }

    @Test
    fun testTerminalColorToRgbIntRgb() {
        val color = TerminalColor.Rgb(0x12, 0x34, 0x56)
        val rgb = TerminalColor.toRgbInt(color)
        assertEquals(0x123456, rgb)

        assertEquals(0x12, TerminalColor.red(rgb))
        assertEquals(0x34, TerminalColor.green(rgb))
        assertEquals(0x56, TerminalColor.blue(rgb))
    }
}
