package com.johnan.terminal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.KeyBarUiItem
import com.johnan.terminal.core.TerminalColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalUiConfigTest {
    @Test
    fun testDefaultUiConfigValues() {
        val config = TerminalUiConfig.DEFAULT

        // Typography
        assertEquals(14.sp, config.typography.fontSize)
        assertEquals(FontFamily.Monospace, config.typography.fontFamily)
        assertEquals(1.0f, config.typography.lineHeightMultiplier)
        assertEquals(0.sp, config.typography.letterSpacing)

        // ColorScheme
        assertEquals(TerminalColorScheme.DEFAULT, config.colorScheme)

        // Cursor
        assertEquals(TerminalCursorStyle.BLOCK, config.cursor.style)
        assertTrue(config.cursor.blink)
        assertEquals(500L, config.cursor.blinkRateMs)
        assertNull(config.cursor.overrideColor)

        // Gestures
        assertTrue(config.gestures.enableTouchToFocus)
        assertTrue(config.gestures.enableSelection)
        assertTrue(config.gestures.enableUrlClick)
        assertEquals(1.0f, config.gestures.wheelScrollMultiplier)
        assertEquals(400L, config.gestures.longPressTimeoutMs)
        assertFalse(config.gestures.touchScrollSendsWheelOnly)

        // KeyBar
        assertTrue(config.keyBar.isVisible)
        assertEquals(48.dp, config.keyBar.height)
        assertTrue(config.keyBar.items.isEmpty())

        // Render optimization
        assertEquals(200, config.render.cacheRowBatchCapacity)
    }

    @Test
    fun testUiConfigDslBuilder() {
        val testItem = KeyBarUiItem(id = "esc", label = "ESC", content = "\u001B")
        val config = terminalUiConfig {
            typography {
                fontSize = 18.sp
                lineHeightMultiplier = 1.2f
                letterSpacing = 1.sp
            }
            cursor {
                style = TerminalCursorStyle.BEAM
                blink = false
                blinkRateMs = 250L
                overrideColor = Color.Red
            }
            gestures {
                enableTouchToFocus = false
                enableSelection = false
                enableUrlClick = false
                wheelScrollMultiplier = 2.5f
                longPressTimeoutMs = 600L
                touchScrollSendsWheelOnly = true
            }
            keyBar {
                isVisible = false
                height = 56.dp
                items = listOf(testItem)
            }
            render {
                cacheRowBatchCapacity = 500
            }
            colorScheme = TerminalColorScheme.SOLARIZED_DARK
        }

        assertEquals(18.sp, config.typography.fontSize)
        assertEquals(1.2f, config.typography.lineHeightMultiplier)
        assertEquals(1.sp, config.typography.letterSpacing)

        assertEquals(TerminalCursorStyle.BEAM, config.cursor.style)
        assertFalse(config.cursor.blink)
        assertEquals(250L, config.cursor.blinkRateMs)
        assertEquals(Color.Red, config.cursor.overrideColor)

        assertFalse(config.gestures.enableTouchToFocus)
        assertFalse(config.gestures.enableSelection)
        assertFalse(config.gestures.enableUrlClick)
        assertEquals(2.5f, config.gestures.wheelScrollMultiplier)
        assertEquals(600L, config.gestures.longPressTimeoutMs)
        assertTrue(config.gestures.touchScrollSendsWheelOnly)


        assertFalse(config.keyBar.isVisible)
        assertEquals(56.dp, config.keyBar.height)
        assertEquals(1, config.keyBar.items.size)
        assertEquals("esc", config.keyBar.items.first().id)

        assertEquals(500, config.render.cacheRowBatchCapacity)
        assertEquals(TerminalColorScheme.SOLARIZED_DARK, config.colorScheme)
    }

    @Test
    fun testPartialDslConfig() {
        val config = terminalUiConfig {
            cursor {
                style = TerminalCursorStyle.UNDERLINE
            }
            colorScheme = TerminalColorScheme.MONOKAI
        }

        assertEquals(TerminalCursorStyle.UNDERLINE, config.cursor.style)
        assertTrue(config.cursor.blink)
        assertEquals(TerminalColorScheme.MONOKAI, config.colorScheme)
        assertEquals(14.sp, config.typography.fontSize)
    }
}
