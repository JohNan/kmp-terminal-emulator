package com.sshclient.presentation.screens.terminal
import androidx.compose.ui.geometry.Offset
import com.sshclient.composeapp.presentation.screens.terminal.TerminalCoordinateConverter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for TerminalCoordinateConverter
 */
class TerminalCoordinateConverterTest {
    private val cellWidth = 10f
    private val cellHeight = 20f
    private val scrollbackLineCount = 100
    private val visibleRows = 24
    private val totalRows = scrollbackLineCount + visibleRows
    private val cols = 80

    @Test
    fun `screenToTerminal converts top-left corner correctly`() {
        val offset = Offset(0f, 0f)
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(0, row)
        assertEquals(0, col)
    }

    @Test
    fun `screenToTerminal converts pixel coordinates to terminal coordinates`() {
        // Click at pixel (150, 400) -> should be row 20, col 15
        val offset = Offset(150f, 400f)
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(20, row)
        assertEquals(15, col)
    }

    @Test
    fun `screenToTerminal handles fractional pixels with rounding`() {
        // Click at pixel (155, 415) -> should truncate to row 20, col 15
        val offset = Offset(155f, 415f)
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(20, row)
        assertEquals(15, col)
    }

    @Test
    fun `screenToTerminal clamps row to valid bounds`() {
        // Click beyond bottom of terminal
        val offset = Offset(100f, 10000f)
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(totalRows - 1, row)
        assertEquals(10, col)
    }

    @Test
    fun `screenToTerminal clamps column to valid bounds`() {
        // Click beyond right edge of terminal
        val offset = Offset(10000f, 100f)
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(5, row)
        assertEquals(cols - 1, col)
    }

    @Test
    fun `screenToTerminal handles negative coordinates`() {
        // Click before top-left corner
        val offset = Offset(-50f, -50f)
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(0, row)
        assertEquals(0, col)
    }

    @Test
    fun `terminalToScreen converts terminal coordinates to pixels`() {
        val offset =
            TerminalCoordinateConverter.terminalToScreen(
                row = 10,
                col = 20,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
            )

        assertEquals(200f, offset.x, 0.01f)
        assertEquals(200f, offset.y, 0.01f)
    }

    @Test
    fun `terminalToScreen converts row 0 col 0 to origin`() {
        val offset =
            TerminalCoordinateConverter.terminalToScreen(
                row = 0,
                col = 0,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
            )

        assertEquals(0f, offset.x, 0.01f)
        assertEquals(0f, offset.y, 0.01f)
    }

    @Test
    fun `round-trip conversion preserves coordinates`() {
        val originalRow = 15
        val originalCol = 45

        // Convert terminal -> screen
        val screenOffset =
            TerminalCoordinateConverter.terminalToScreen(
                originalRow,
                originalCol,
                cellWidth,
                cellHeight,
            )

        // Convert screen -> terminal
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                screenOffset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(originalRow, row)
        assertEquals(originalCol, col)
    }

    @Test
    fun `screenToTerminal works with scrollback content`() {
        // Click in scrollback area (first 100 lines)
        val offset = Offset(300f, 500f) // row 25, col 30
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(25, row) // Within scrollback
        assertEquals(30, col)
    }

    @Test
    fun `screenToTerminal works with visible terminal area`() {
        // Click in visible terminal area (beyond scrollback)
        val offset = Offset(400f, 2100f) // row 105 (scrollback + 5), col 40
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
            )

        assertEquals(105, row) // Beyond scrollback
        assertEquals(40, col)
    }

    @Test
    fun `screenToTerminal handles vertical offset`() {
        val verticalOffset = 100f
        // Click at pixel (150, 500) with 100px vertical offset -> should be row (500-100)/20 = 20, col 150/10 = 15
        val offset = Offset(150f, 500f)
        val (row, col) =
            TerminalCoordinateConverter.screenToTerminal(
                offset,
                cellWidth,
                cellHeight,
                scrollbackLineCount,
                totalRows,
                cols,
                verticalOffset,
            )

        assertEquals(20, row)
        assertEquals(15, col)
    }

    @Test
    fun `terminalToScreen handles vertical offset`() {
        val verticalOffset = 100f
        val offset =
            TerminalCoordinateConverter.terminalToScreen(
                row = 20,
                col = 15,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                verticalOffset = verticalOffset,
            )

        assertEquals(150f, offset.x, 0.01f)
        assertEquals(500f, offset.y, 0.01f)
    }
}
