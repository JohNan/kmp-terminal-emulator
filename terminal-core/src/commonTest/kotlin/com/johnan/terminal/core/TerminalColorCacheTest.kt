package com.johnan.terminal.core
import kotlin.test.Test
import kotlin.test.assertSame

class TerminalColorCacheTest {
    @Test
    fun testStandardColorIdentity() {
        val c1 = TerminalColor.buildStandard(1)
        val c2 = TerminalColor.buildStandard(1)

        // Should be the same instance
        assertSame(c1, c2, "Standard colors should be same instance (cached)")
    }

    @Test
    fun testPalette256ColorIdentity() {
        val c1 = TerminalColor.buildPalette256(1)
        val c2 = TerminalColor.buildPalette256(1)

        // Should be the same instance
        assertSame(c1, c2, "Palette256 colors should be same instance (cached)")
    }
}
