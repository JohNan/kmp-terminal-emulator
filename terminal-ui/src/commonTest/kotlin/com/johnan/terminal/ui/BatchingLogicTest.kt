package com.johnan.terminal.ui

import com.johnan.terminal.core.TerminalCell
import com.johnan.terminal.core.TerminalColor
import com.johnan.terminal.core.UnderlineStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchingLogicTest {
    @Test
    fun splitsBatchesWhenUnderlineStyleChanges() {
        val row = arrayOf(
            TerminalCell(
                char = 'A',
                underline = true,
                underlineStyle = UnderlineStyle.SINGLE,
            ),
            TerminalCell(
                char = 'B',
                underline = true,
                underlineStyle = UnderlineStyle.DOUBLE,
            ),
            TerminalCell(
                char = 'C',
                underline = true,
                underlineStyle = UnderlineStyle.CURLY,
            ),
            TerminalCell(
                char = 'D',
                underline = true,
                underlineStyle = UnderlineStyle.DOTTED,
            ),
            TerminalCell(
                char = 'E',
                underline = true,
                underlineStyle = UnderlineStyle.DASHED,
            ),
        )

        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(5, batches.size)

        assertEquals("A", batches[0].text)
        assertTrue(batches[0].underline)
        assertEquals(UnderlineStyle.SINGLE, batches[0].underlineStyle)

        assertEquals("B", batches[1].text)
        assertTrue(batches[1].underline)
        assertEquals(UnderlineStyle.DOUBLE, batches[1].underlineStyle)

        assertEquals("C", batches[2].text)
        assertTrue(batches[2].underline)
        assertEquals(UnderlineStyle.CURLY, batches[2].underlineStyle)

        assertEquals("D", batches[3].text)
        assertTrue(batches[3].underline)
        assertEquals(UnderlineStyle.DOTTED, batches[3].underlineStyle)

        assertEquals("E", batches[4].text)
        assertTrue(batches[4].underline)
        assertEquals(UnderlineStyle.DASHED, batches[4].underlineStyle)
    }

    @Test
    fun splitsBatchesWhenUnderlineColorChanges() {
        val defaultUl = TerminalColor.Default
        val redUl = TerminalColor.Standard(1)
        val rgbUl = TerminalColor.Rgb(255, 128, 0)

        val row = arrayOf(
            TerminalCell(
                char = 'X',
                underline = true,
                underlineStyle = UnderlineStyle.SINGLE,
                underlineColor = defaultUl,
            ),
            TerminalCell(
                char = 'Y',
                underline = true,
                underlineStyle = UnderlineStyle.SINGLE,
                underlineColor = redUl,
            ),
            TerminalCell(
                char = 'Z',
                underline = true,
                underlineStyle = UnderlineStyle.SINGLE,
                underlineColor = rgbUl,
            ),
        )

        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(3, batches.size)

        assertEquals("X", batches[0].text)
        assertEquals(defaultUl, batches[0].underlineColor)
        assertEquals(UnderlineStyle.SINGLE, batches[0].underlineStyle)

        assertEquals("Y", batches[1].text)
        assertEquals(redUl, batches[1].underlineColor)
        assertEquals(UnderlineStyle.SINGLE, batches[1].underlineStyle)

        assertEquals("Z", batches[2].text)
        assertEquals(rgbUl, batches[2].underlineColor)
        assertEquals(UnderlineStyle.SINGLE, batches[2].underlineStyle)
    }

    @Test
    fun groupsCellsWithSameUnderlineStyleAndColor() {
        val color = TerminalColor.Standard(4)
        val row = arrayOf(
            TerminalCell(
                char = 'H',
                underline = true,
                underlineStyle = UnderlineStyle.CURLY,
                underlineColor = color,
            ),
            TerminalCell(
                char = 'E',
                underline = true,
                underlineStyle = UnderlineStyle.CURLY,
                underlineColor = color,
            ),
            TerminalCell(
                char = 'Y',
                underline = true,
                underlineStyle = UnderlineStyle.CURLY,
                underlineColor = color,
            ),
        )

        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(1, batches.size)
        assertEquals("HEY", batches[0].text)
        assertEquals(3, batches[0].length)
        assertTrue(batches[0].underline)
        assertEquals(UnderlineStyle.CURLY, batches[0].underlineStyle)
        assertEquals(color, batches[0].underlineColor)
    }

    @Test
    fun preservesUnderlineAttributesInPathBEmptyBatches() {
        val color1 = TerminalColor.Standard(2)
        val color2 = TerminalColor.Standard(3)

        val row = arrayOf(
            TerminalCell(
                char = ' ',
                underline = true,
                underlineStyle = UnderlineStyle.DOTTED,
                underlineColor = color1,
            ),
            TerminalCell(
                char = ' ',
                underline = true,
                underlineStyle = UnderlineStyle.DASHED,
                underlineColor = color2,
            ),
        )

        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(2, batches.size)

        assertEquals("", batches[0].text)
        assertEquals(1, batches[0].length)
        assertTrue(batches[0].underline)
        assertEquals(UnderlineStyle.DOTTED, batches[0].underlineStyle)
        assertEquals(color1, batches[0].underlineColor)

        assertEquals("", batches[1].text)
        assertEquals(1, batches[1].length)
        assertTrue(batches[1].underline)
        assertEquals(UnderlineStyle.DASHED, batches[1].underlineStyle)
        assertEquals(color2, batches[1].underlineColor)
    }

    @Test
    fun preservesUnderlineAttributesInPathBBuildBatches() {
        val color = TerminalColor.Rgb(50, 100, 150)

        // Space followed by content with same underline style
        val row = arrayOf(
            TerminalCell(
                char = ' ',
                underline = true,
                underlineStyle = UnderlineStyle.DOUBLE,
                underlineColor = color,
            ),
            TerminalCell(
                char = 'K',
                underline = true,
                underlineStyle = UnderlineStyle.DOUBLE,
                underlineColor = color,
            ),
            TerminalCell(
                char = 'M',
                underline = true,
                underlineStyle = UnderlineStyle.SINGLE,
                underlineColor = color,
            ),
        )

        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(2, batches.size)

        assertEquals(" K", batches[0].text)
        assertEquals(2, batches[0].length)
        assertEquals(UnderlineStyle.DOUBLE, batches[0].underlineStyle)
        assertEquals(color, batches[0].underlineColor)

        assertEquals("M", batches[1].text)
        assertEquals(1, batches[1].length)
        assertEquals(UnderlineStyle.SINGLE, batches[1].underlineStyle)
        assertEquals(color, batches[1].underlineColor)
    }

    @Test
    fun defaultRenderBatchUnderlineValues() {
        val batchNoUnderline = RenderBatch(
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
            conceal = false,
        )
        assertEquals(UnderlineStyle.NONE, batchNoUnderline.underlineStyle)
        assertEquals(TerminalColor.Default, batchNoUnderline.underlineColor)

        val batchUnderline = RenderBatch(
            startCol = 0,
            length = 1,
            text = "B",
            fgColor = TerminalColor.Default,
            bgColor = TerminalColor.Default,
            bold = false,
            underline = true,
            reverse = false,
            strikethrough = false,
            overline = false,
            conceal = false,
        )
        assertEquals(UnderlineStyle.SINGLE, batchUnderline.underlineStyle)
        assertEquals(TerminalColor.Default, batchUnderline.underlineColor)
    }
}
