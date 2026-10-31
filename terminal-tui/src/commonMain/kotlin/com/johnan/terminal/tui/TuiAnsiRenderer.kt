package com.johnan.terminal.tui

import com.johnan.terminal.core.UnderlineStyle

/**
 * Low-allocation differential ANSI renderer converting [TuiSurface] states
 * into minimal ANSI escape sequences with TrueColor and synchronized updates.
 */
class TuiAnsiRenderer {
    private var previousSurface: TuiSurface? = null
    private val sb = StringBuilder(16384)

    /**
     * Resets internal differential state, forcing full redraw on next [render] call.
     */
    fun reset() {
        previousSurface = null
    }

    /**
     * Renders [surface] into an ANSI escape string.
     * Diffs cells against previous frame unless [forceFullRedraw] is true.
     */
    fun render(surface: TuiSurface, forceFullRedraw: Boolean = false): String {
        sb.setLength(0)
        sb.append("\u001b[?2026h")

        val dimensionsChanged = previousSurface == null ||
            previousSurface?.rows != surface.rows ||
            previousSurface?.cols != surface.cols
        val isFull = forceFullRedraw || dimensionsChanged

        if (dimensionsChanged) {
            previousSurface = TuiSurface(surface.rows, surface.cols)
        }
        if (isFull) {
            sb.append("\u001b[2J\u001b[H")
        }

        val prev = previousSurface!!
        var currentFg = -1
        var currentBg = -1
        var currentStyle = -1
        var currentUlRgb = -2
        var currentHyperlink: String? = null
        var lastCursorRow = -1
        var lastCursorCol = -1

        for (r in 0 until surface.rows) {
            for (c in 0 until surface.cols) {
                val char = surface.getChar(r, c)
                val cp = surface.getCodepoint(r, c)
                val fg = surface.getFgRgb(r, c)
                val bg = surface.getBgRgb(r, c)
                val style = surface.getStyle(r, c)
                val ulRgb = surface.getUnderlineRgb(r, c)
                val link = surface.getHyperlink(r, c)

                val changed = isFull ||
                    char != prev.getChar(r, c) ||
                    cp != prev.getCodepoint(r, c) ||
                    fg != prev.getFgRgb(r, c) ||
                    bg != prev.getBgRgb(r, c) ||
                    style != prev.getStyle(r, c) ||
                    ulRgb != prev.getUnderlineRgb(r, c) ||
                    link != prev.getHyperlink(r, c)

                if (changed) {
                    if (cp == 0) continue
                    if (r != lastCursorRow || c != lastCursorCol) {
                        if (currentHyperlink != null) {
                            sb.append("\u001b]8;;\u001b\\")
                            currentHyperlink = null
                        }
                        sb.append("\u001b[").append(r + 1).append(';').append(c + 1).append('H')
                        lastCursorRow = r
                        lastCursorCol = c
                    }

                    if (link != currentHyperlink) {
                        sb.append("\u001b]8;;").append(link ?: "").append("\u001b\\")
                        currentHyperlink = link
                    }

                    if (fg != currentFg || bg != currentBg || style != currentStyle || ulRgb != currentUlRgb) {
                        appendSgr(sb, fg, bg, style, ulRgb)
                        currentFg = fg
                        currentBg = bg
                        currentStyle = style
                        currentUlRgb = ulRgb
                    }

                    if (cp <= 0xFFFF) {
                        sb.append(cp.toChar())
                    } else {
                        val high = ((cp - 0x10000) shr 10) + 0xD800
                        val low = ((cp - 0x10000) and 0x3FF) + 0xDC00
                        sb.append(high.toChar()).append(low.toChar())
                    }
                    val advance = if (c + 1 < surface.cols && surface.getCodepoint(r, c + 1) == 0) 2 else 1
                    lastCursorCol += advance
                }
            }
        }

        if (currentHyperlink != null) {
            sb.append("\u001b]8;;\u001b\\")
        }

        if (currentStyle != -1 || currentFg != -1 || currentBg != -1 || currentUlRgb != -2) {
            sb.append("\u001b[0m")
        }

        // Position cursor (1-indexed)
        sb.append("\u001b[").append(surface.cursorRow + 1).append(';').append(surface.cursorCol + 1).append('H')

        // Cursor visibility
        if (surface.cursorVisible) {
            sb.append("\u001b[?25h")
        } else {
            sb.append("\u001b[?25l")
        }

        sb.append("\u001b[?2026l")

        prev.copyFrom(surface)
        return sb.toString()
    }

    private fun appendSgr(sb: StringBuilder, fg: Int, bg: Int, style: Int, ulRgb: Int = -1) {
        sb.append("\u001b[0")
        if ((style and TuiCellStyle.BOLD) != 0) sb.append(";1")
        if ((style and TuiCellStyle.DIM) != 0) sb.append(";2")
        if ((style and TuiCellStyle.ITALIC) != 0) sb.append(";3")

        val ulStyle = TuiCellStyle.getUnderlineStyle(style)
        if (ulStyle != UnderlineStyle.NONE) {
            if (ulStyle == UnderlineStyle.SINGLE) {
                sb.append(";4")
            } else {
                sb.append(";4:").append(ulStyle.ordinal)
            }
        } else if ((style and TuiCellStyle.UNDERLINE) != 0) {
            sb.append(";4")
        }

        if ((style and TuiCellStyle.BLINK) != 0) sb.append(";5")
        if ((style and TuiCellStyle.REVERSE) != 0) sb.append(";7")
        if ((style and TuiCellStyle.STRIKETHROUGH) != 0) sb.append(";9")
        if ((style and TuiCellStyle.OVERLINE) != 0) sb.append(";53")

        val fgR = (fg shr 16) and 0xFF
        val fgG = (fg shr 8) and 0xFF
        val fgB = fg and 0xFF
        sb.append(";38;2;").append(fgR).append(';').append(fgG).append(';').append(fgB)

        val bgR = (bg shr 16) and 0xFF
        val bgG = (bg shr 8) and 0xFF
        val bgB = bg and 0xFF
        sb.append(";48;2;").append(bgR).append(';').append(bgG).append(';').append(bgB)

        if (ulRgb >= 0) {
            val ulR = (ulRgb shr 16) and 0xFF
            val ulG = (ulRgb shr 8) and 0xFF
            val ulB = ulRgb and 0xFF
            sb.append(";58;2;").append(ulR).append(';').append(ulG).append(';').append(ulB)
        }

        sb.append('m')
    }
}
