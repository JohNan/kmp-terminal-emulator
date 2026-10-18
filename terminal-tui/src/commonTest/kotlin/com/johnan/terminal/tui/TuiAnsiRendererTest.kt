package com.johnan.terminal.tui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TuiAnsiRendererTest {
    @Test
    fun testFullRenderOutputStructure() {
        val renderer = TuiAnsiRenderer()
        val surface = TuiSurface(rows = 3, cols = 5)
        surface.drawText(0, 0, "HI", 0xFF0000, 0x0000FF, TuiCellStyle.BOLD)
        surface.cursorRow = 1
        surface.cursorCol = 2
        surface.cursorVisible = true

        val output = renderer.render(surface)

        // Must start with synchronized update start and end with synchronized update end
        assertTrue(output.startsWith("\u001b[?2026h"))
        assertTrue(output.endsWith("\u001b[?2026l"))

        // Initial render must clear screen and home cursor
        assertTrue(output.contains("\u001b[2J\u001b[H"))

        // Must emit bold and TrueColor SGR
        assertTrue(output.contains(";1;38;2;255;0;0;48;2;0;0;255m"))

        // Must position cursor and show it
        assertTrue(output.contains("\u001b[2;3H"))
        assertTrue(output.contains("\u001b[?25h"))
    }

    @Test
    fun testDifferentialRendering() {
        val renderer = TuiAnsiRenderer()
        val surface = TuiSurface(rows = 4, cols = 4)
        surface.clear(0xFFFFFF, 0x000000)

        // Initial frame
        renderer.render(surface)

        // Frame 2: Modify single cell at row 2, col 2
        surface.setCell(2, 2, 'Z', 0x112233, 0x445566, TuiCellStyle.UNDERLINE)
        val diffOutput = renderer.render(surface)

        // Diff output must NOT clear screen
        assertFalse(diffOutput.contains("\u001b[2J"))

        // Must jump directly to row 3, col 3 (1-indexed)
        assertTrue(diffOutput.contains("\u001b[3;3H"))
        assertTrue(diffOutput.contains("Z"))

        // Frame 3: Identical frame without changes
        val noChangeOutput = renderer.render(surface)
        assertFalse(noChangeOutput.contains("\u001b[2J"))
        assertFalse(noChangeOutput.contains("Z"))
    }

    @Test
    fun testForceFullRedrawAndReset() {
        val renderer = TuiAnsiRenderer()
        val surface = TuiSurface(rows = 2, cols = 2)

        renderer.render(surface)

        // Force full redraw
        val fullOutput = renderer.render(surface, forceFullRedraw = true)
        assertTrue(fullOutput.contains("\u001b[2J\u001b[H"))

        // Reset
        renderer.reset()
        val afterResetOutput = renderer.render(surface)
        assertTrue(afterResetOutput.contains("\u001b[2J\u001b[H"))
    }

    @Test
    fun testCursorHidden() {
        val renderer = TuiAnsiRenderer()
        val surface = TuiSurface(rows = 2, cols = 2)
        surface.cursorVisible = false

        val output = renderer.render(surface)
        assertTrue(output.contains("\u001b[?25l"))
    }

    @Test
    fun testSupplementaryCodepointRendering() {
        val renderer = TuiAnsiRenderer()
        val surface = TuiSurface(rows = 1, cols = 4)
        surface.setCell(0, 0, ' ', 0xFFFFFF, 0x000000, codepoint = 0xF06A9)
        surface.setCell(0, 1, 'X', 0xFFFFFF, 0x000000)

        val output = renderer.render(surface)
        assertTrue(output.contains("\uDB81\uDEA9X"))
    }
}
