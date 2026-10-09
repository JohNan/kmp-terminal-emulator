package com.johnan.terminal.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.johnan.terminal.ui.RenderBatch
import com.johnan.terminal.ui.calculateBatches
import com.johnan.terminal.ui.calculateVisibleRowRange
import com.johnan.terminal.ui.resolveBatchColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalCanvasTest {
    @Test
    fun testCalculateVisibleRowRangeAtTopReturnsOnlyViewportRows() {
        val totalRows = 10_000
        val cellHeight = 40f
        val viewportHeight = 1000f
        val scrollOffset = 0f

        val range = calculateVisibleRowRange(
            scrollOffset = scrollOffset,
            viewportHeight = viewportHeight,
            cellHeight = cellHeight,
            totalRows = totalRows,
            overscanRows = 1,
        )

        assertEquals(0, range.first)
        // 1000 / 40 = 25 rows + 1 (partial) + 1 (overscan) = row index 27 exclusive (0 until 27 -> 27 rows)
        assertEquals(26, range.last)
        assertEquals(27, range.count())
        assertTrue(range.count() < 100, "Should only render visible rows plus overscan, never all $totalRows rows")
    }

    @Test
    fun testCalculateVisibleRowRangeInMiddleReturnsExactWindow() {
        val totalRows = 10_000
        val cellHeight = 40f
        val viewportHeight = 1000f
        val scrollOffset = 5000f // row 125

        val range = calculateVisibleRowRange(
            scrollOffset = scrollOffset,
            viewportHeight = viewportHeight,
            cellHeight = cellHeight,
            totalRows = totalRows,
            overscanRows = 1,
        )

        assertEquals(124, range.first) // 125 - 1 overscan
        assertEquals(151, range.last) // 125 + 25 + 1 + 1 = 152 exclusive -> last index 151
        assertEquals(28, range.count())
    }

    @Test
    fun testCalculateVisibleRowRangeAtBottomClampsToTotalRows() {
        val totalRows = 10_000
        val cellHeight = 40f
        val viewportHeight = 1000f
        val maxScroll = (totalRows * cellHeight) - viewportHeight // 399,000f

        val range = calculateVisibleRowRange(
            scrollOffset = maxScroll,
            viewportHeight = viewportHeight,
            cellHeight = cellHeight,
            totalRows = totalRows,
            overscanRows = 1,
        )

        assertEquals(9974, range.first)
        assertEquals(9999, range.last)
        assertEquals(26, range.count())
    }

    @Test
    fun testCalculateVisibleRowRangeEdgeCases() {
        // Zero rows
        assertTrue(calculateVisibleRowRange(0f, 1000f, 40f, 0).isEmpty())

        // Zero cell height
        assertTrue(calculateVisibleRowRange(0f, 1000f, 0f, 100).isEmpty())

        // Zero viewport height
        assertTrue(calculateVisibleRowRange(0f, 0f, 40f, 100).isEmpty())

        // Negative scroll offset should clamp to 0
        val negativeRange = calculateVisibleRowRange(-500f, 1000f, 40f, 100)
        assertEquals(0, negativeRange.first)
    }

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
