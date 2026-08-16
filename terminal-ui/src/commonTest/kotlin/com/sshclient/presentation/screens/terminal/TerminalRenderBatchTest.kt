package com.sshclient.presentation.screens.terminal

import com.sshclient.composeapp.presentation.screens.terminal.RenderBatch
import com.sshclient.composeapp.presentation.screens.terminal.calculateBatches
import com.sshclient.data.terminal.TerminalCell
import com.sshclient.data.terminal.TerminalColor
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalRenderBatchTest {
    @Test
    fun testCompletelyInvisibleEmptyBatchIsSkipped() {
        val row = Array(5) { TerminalCell(char = ' ') }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(0, batches.size)
    }

    @Test
    fun testVisibleEmptyBatchIsAdded() {
        val redBg = TerminalColor.Standard(1)
        val row = Array(5) { TerminalCell(char = ' ', backgroundColor = redBg) }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(1, batches.size)
        assertEquals("", batches[0].text)
        assertEquals(5, batches[0].length)
        assertEquals(0, batches[0].startCol)
        assertEquals(redBg, batches[0].bgColor)
    }

    @Test
    fun testSingleCharContent() {
        val row = Array(1) { TerminalCell(char = 'A') }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(1, batches.size)
        assertEquals("A", batches[0].text)
        assertEquals(1, batches[0].length)
    }

    @Test
    fun testMultipleCharsContent() {
        val row = Array(3) {
            when (it) {
                0 -> TerminalCell(char = 'A')
                1 -> TerminalCell(char = 'B')
                else -> TerminalCell(char = 'C')
            }
        }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(1, batches.size)
        assertEquals("ABC", batches[0].text)
        assertEquals(3, batches[0].length)
    }

    @Test
    fun testLeadingSpacesInContent() {
        val row = Array(3) {
            when (it) {
                2 -> TerminalCell(char = 'A')
                else -> TerminalCell(char = ' ')
            }
        }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(1, batches.size)
        assertEquals("  A", batches[0].text)
        assertEquals(3, batches[0].length)
    }

    @Test
    fun testTrailingSpacesInContent() {
        val row = Array(3) {
            when (it) {
                0 -> TerminalCell(char = 'A')
                else -> TerminalCell(char = ' ')
            }
        }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(1, batches.size)
        assertEquals("A  ", batches[0].text)
        assertEquals(3, batches[0].length)
    }

    @Test
    fun testMixedContentAndSpaces() {
        val row = Array(5) {
            when (it) {
                1 -> TerminalCell(char = 'A')
                3 -> TerminalCell(char = 'B')
                else -> TerminalCell(char = ' ')
            }
        }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(1, batches.size)
        assertEquals(" A B ", batches[0].text)
        assertEquals(5, batches[0].length)
    }

    @Test
    fun testMultipleBatchesDueToColorChange() {
        val red = TerminalColor.Standard(1)
        val blue = TerminalColor.Standard(4)

        val row = Array(2) {
            when (it) {
                0 -> TerminalCell(char = 'A', foregroundColor = red)
                else -> TerminalCell(char = 'B', foregroundColor = blue)
            }
        }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(2, batches.size)

        assertEquals("A", batches[0].text)
        assertEquals(red, batches[0].fgColor)

        assertEquals("B", batches[1].text)
        assertEquals(blue, batches[1].fgColor)
    }

    @Test
    fun testInvisibleWhitespaceBatchAmongContentBatchesIsSkipped() {
        val red = TerminalColor.Standard(1)
        val blue = TerminalColor.Standard(4)

        val row = Array(3) {
            when (it) {
                0 -> TerminalCell(char = 'A', foregroundColor = red)
                1 -> TerminalCell(char = ' ', foregroundColor = blue)
                else -> TerminalCell(char = 'B', foregroundColor = red)
            }
        }
        val sb = StringBuilder()
        val batches = ArrayList<RenderBatch>()
        calculateBatches(row, sb, batches)

        assertEquals(2, batches.size)

        assertEquals("A", batches[0].text)
        assertEquals(red, batches[0].fgColor)
        assertEquals(0, batches[0].startCol)

        assertEquals("B", batches[1].text)
        assertEquals(red, batches[1].fgColor)
        assertEquals(2, batches[1].startCol)
    }
}
