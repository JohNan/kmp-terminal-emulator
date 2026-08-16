package com.johnan.terminal.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.johnan.terminal.ui.RenderBatch
import com.johnan.terminal.ui.calculateBatches
import com.johnan.terminal.ui.resolveBatchColors
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalCanvasTest {
    @Test
    fun testCalculateBatchesShouldUseRawColorsAndCaptureReverseFlag() {
        val row = arrayOf(
            TerminalCell(
                char = 'A',
                foregroundColor = TerminalColor.Standard(1),
                backgroundColor = TerminalColor.Standard(2),
                reverse = false
            ),
            TerminalCell(
                char = 'B',
                foregroundColor = TerminalColor.Standard(1),
                backgroundColor = TerminalColor.Standard(2),
                reverse = true
            )
        )
        val sb = StringBuilder()

        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(2, batches.size)

        // Batch 1: Normal
        assertEquals("A", batches[0].text)
        assertEquals(TerminalColor.Standard(1), batches[0].fgColor)
        assertEquals(TerminalColor.Standard(2), batches[0].bgColor)
        assertEquals(false, batches[0].reverse)

        // Batch 2: Reversed
        assertEquals("B", batches[1].text)
        assertEquals(TerminalColor.Standard(1), batches[1].fgColor)
        assertEquals(TerminalColor.Standard(2), batches[1].bgColor)
        assertEquals(true, batches[1].reverse)
    }

    @Test
    fun testResolveBatchColorsShouldInvertColorsWhenReverseIsTrue() {
        val ansiColors = Array(16) { Color.Black }
        val baseTextStyle = TextStyle(color = Color.White)
        val terminalBackgroundColor = Color.Black

        // Case 1: Normal (Default Fg/Bg)
        val batchNormal = RenderBatch(
            startCol = 0,
            length = 1,
            text = "A",
            fgColor = TerminalColor.Default,
            bgColor = TerminalColor.Default,
            bold = false,
            underline = false,
            reverse = false,
            strikethrough = false,
            overline = false,
            conceal = false
        )

        resolveBatchColors(batchNormal, ansiColors, baseTextStyle, terminalBackgroundColor, null, true)

        assertEquals(baseTextStyle.color, batchNormal.resolvedFg)
        assertEquals(Color.Transparent, batchNormal.resolvedBg)

        // Case 2: Reversed (Default Fg/Bg) -> Should invert
        val batchReversed = RenderBatch(
            startCol = 0,
            length = 1,
            text = "A",
            fgColor = TerminalColor.Default,
            bgColor = TerminalColor.Default,
            bold = false,
            underline = false,
            reverse = true,
            strikethrough = false,
            overline = false,
            conceal = false
        )

        resolveBatchColors(batchReversed, ansiColors, baseTextStyle, terminalBackgroundColor, null, true)

        assertEquals(terminalBackgroundColor, batchReversed.resolvedFg)
        assertEquals(baseTextStyle.color, batchReversed.resolvedBg)
    }

    @Test
    fun testResolveBatchColorsShouldInvertExplicitColors() {
        val ansiColors = Array(16) {
            if (it == 1) {
                Color.Red
            } else if (it == 4) {
                Color.Blue
            } else {
                Color.Black
            }
        }
        val baseTextStyle = TextStyle(color = Color.White)
        val terminalBackgroundColor = Color.Black

        val batch = RenderBatch(
            startCol = 0,
            length = 1,
            text = "A",
            fgColor = TerminalColor.Standard(1),
            bgColor = TerminalColor.Standard(4),
            bold = false,
            underline = false,
            reverse = true,
            strikethrough = false,
            overline = false,
            conceal = false
        )

        resolveBatchColors(batch, ansiColors, baseTextStyle, terminalBackgroundColor, null, true)

        assertEquals(Color.Blue, batch.resolvedFg)
        assertEquals(Color.Red, batch.resolvedBg)
    }
}
