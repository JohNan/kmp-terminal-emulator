package com.sshclient.data.terminal
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalColorTest {
    @Test
    fun `test Standard colors in dark mode`() {
        // Test a few standard colors
        val black = TerminalColor.toComposeColor(TerminalColor.Standard(0), isDark = true)
        assertEquals(Color(0xFF000000), black)

        val red = TerminalColor.toComposeColor(TerminalColor.Standard(1), isDark = true)
        assertEquals(Color(0xFFCD0000), red)

        val white = TerminalColor.toComposeColor(TerminalColor.Standard(7), isDark = true)
        assertEquals(Color(0xFFE5E5E5), white)
    }

    @Test
    fun `test Standard colors in light mode`() {
        // Standard colors seem to be same regardless of isDark passed to getStandardColor in current implementation?
        // Let's check getStandardColor source again.
        // It has `else -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000)`
        // But the cases 0-15 match explicitly. So isDark is only for `else`.

        val black = TerminalColor.toComposeColor(TerminalColor.Standard(0), isDark = false)
        assertEquals(Color(0xFF000000), black)
    }

    @Test
    fun `test Standard fallback color`() {
        // Standard color with code > 15 (should not happen for Standard(code) usually but let's see)
        // code is Int.
        val unknown = TerminalColor.toComposeColor(TerminalColor.Standard(999), isDark = true)
        assertEquals(Color(0xFFE0E0E0), unknown)

        val unknownLight = TerminalColor.toComposeColor(TerminalColor.Standard(999), isDark = false)
        assertEquals(Color(0xFF000000), unknownLight)
    }

    @Test
    fun `test Palette256 colors`() {
        // Test first 16 (mapped to standard)
        val p0 = TerminalColor.toComposeColor(TerminalColor.Palette256(0))
        assertEquals(Color(0xFF000000), p0)

        // Test 216-color cube (16-231)
        // code 16 -> index 0 -> r=0, g=0, b=0 -> Black
        val p16 = TerminalColor.toComposeColor(TerminalColor.Palette256(16))
        assertEquals(Color(0, 0, 0), p16)

        // code 231 -> index 215
        // r = (215/36)*51 = 5*51 = 255
        // g = ((215%36)/6)*51 = (35/6)*51 = 5*51 = 255
        // b = (215%6)*51 = 5*51 = 255
        // White
        val p231 = TerminalColor.toComposeColor(TerminalColor.Palette256(231))
        assertEquals(Color(255, 255, 255), p231)

        // Random one: code 196 (Red)
        // index = 180
        // r = (180/36)*51 = 5*51 = 255
        // g = ((180%36)/6)*51 = 0*51 = 0
        // b = (180%6)*51 = 0*51 = 0
        val p196 = TerminalColor.toComposeColor(TerminalColor.Palette256(196))
        assertEquals(Color(255, 0, 0), p196)

        // Test Grayscale (232-255)
        // code 232 -> gray = 8 + 0*10 = 8
        val p232 = TerminalColor.toComposeColor(TerminalColor.Palette256(232))
        assertEquals(Color(8, 8, 8), p232)

        // code 255 -> gray = 8 + 23*10 = 238
        val p255 = TerminalColor.toComposeColor(TerminalColor.Palette256(255))
        assertEquals(Color(238, 238, 238), p255)
    }
}
