package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalConfigTest {
    @Test
    fun testDefaultValues() {
        val config = TerminalConfig.DEFAULT
        assertEquals(24, config.initialRows)
        assertEquals(80, config.initialCols)
        assertEquals(1000, config.maxScrollback)
        assertEquals(Osc52Policy.ASK, config.osc52Policy)
        assertEquals(8, config.tabStopInterval)
        assertTrue(config.autoWrapDefault)
        assertFalse(config.cursorBlinkingDefault)
        assertTrue(config.mouseTrackingEnabled)
        assertTrue(config.bracketedPasteEnabled)
        assertEquals(BellBehavior.VISUAL, config.bellBehavior)
    }

    @Test
    fun testDslBuilder() {
        val config = terminalConfig {
            initialRows = 40
            initialCols = 120
            maxScrollback = 5000
            osc52Policy = Osc52Policy.ALWAYS_ALLOW
            tabStopInterval = 4
            autoWrapDefault = false
            cursorBlinkingDefault = true
            mouseTrackingEnabled = false
            bracketedPasteEnabled = false
            bellBehavior = BellBehavior.CALLBACK
        }

        assertEquals(40, config.initialRows)
        assertEquals(120, config.initialCols)
        assertEquals(5000, config.maxScrollback)
        assertEquals(Osc52Policy.ALWAYS_ALLOW, config.osc52Policy)
        assertEquals(4, config.tabStopInterval)
        assertFalse(config.autoWrapDefault)
        assertTrue(config.cursorBlinkingDefault)
        assertFalse(config.mouseTrackingEnabled)
        assertFalse(config.bracketedPasteEnabled)
        assertEquals(BellBehavior.CALLBACK, config.bellBehavior)
    }

    @Test
    fun testValidation() {
        assertFailsWith<IllegalArgumentException> {
            TerminalConfig(initialRows = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            TerminalConfig(initialCols = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            TerminalConfig(maxScrollback = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            TerminalConfig(tabStopInterval = 0)
        }
    }

    @Test
    fun testTerminalEmulatorIntegrationWithConfig() {
        val config = terminalConfig {
            initialRows = 30
            initialCols = 100
            maxScrollback = 200
            osc52Policy = Osc52Policy.ALWAYS_DENY
        }
        val emulator = TerminalEmulator(config = config)

        assertEquals(config, emulator.config)
        assertEquals(Osc52Policy.ALWAYS_DENY, emulator.osc52Policy)
        assertEquals(30, emulator.screenState.value.rows.size)
        assertEquals(100, emulator.screenState.value.cols)
    }
}
