package com.johnan.terminal.tui

import com.johnan.terminal.core.TerminalCell
import com.johnan.terminal.core.TerminalColor
import com.johnan.terminal.core.TerminalPalette
import com.johnan.terminal.core.TerminalScreenState
import com.johnan.terminal.core.UnderlineStyle

/**
 * Text styling attributes for TUI cells bitwise-OR'd together.
 */
object TuiCellStyle {
    const val NONE = 0
    const val BOLD = 1 shl 0
    const val DIM = 1 shl 1
    const val ITALIC = 1 shl 2
    const val UNDERLINE = 1 shl 3
    const val BLINK = 1 shl 4
    const val REVERSE = 1 shl 5
    const val STRIKETHROUGH = 1 shl 6
    const val OVERLINE = 1 shl 7

    const val UNDERLINE_STYLE_SHIFT = 8
    const val UNDERLINE_STYLE_MASK = 0x7 shl 8

    fun withUnderlineStyle(style: Int, underlineStyle: UnderlineStyle): Int =
        (style and UNDERLINE_STYLE_MASK.inv()) or ((underlineStyle.ordinal and 0x7) shl UNDERLINE_STYLE_SHIFT)

    fun getUnderlineStyle(style: Int): UnderlineStyle {
        val ord = (style and UNDERLINE_STYLE_MASK) ushr UNDERLINE_STYLE_SHIFT
        val base = UnderlineStyle.entries.getOrElse(ord) {
            if ((style and UNDERLINE) != 0) UnderlineStyle.SINGLE else UnderlineStyle.NONE
        }
        return if (base == UnderlineStyle.NONE && (style and UNDERLINE) != 0) {
            UnderlineStyle.SINGLE
        } else {
            base
        }
    }
}

/**
 * High-performance offscreen 2D terminal grid surface supporting box drawing,
 * text rendering, and differential blitting from [TerminalScreenState].
 */
class TuiSurface(
    val rows: Int,
    val cols: Int,
) {
    private val totalCells = rows * cols
    private val chars = CharArray(totalCells)
    private val codepoints = IntArray(totalCells)
    private val fgRgbArray = IntArray(totalCells)
    private val bgRgbArray = IntArray(totalCells)
    private val styleArray = IntArray(totalCells)
    private val underlineRgbArray = IntArray(totalCells) { -1 }
    private val hyperlinkArray = arrayOfNulls<String>(totalCells)

    var cursorRow: Int = 0
    var cursorCol: Int = 0
    var cursorVisible: Boolean = true

    init {
        clear()
    }

    /**
     * Clears the entire surface with the specified default colors.
     */
    fun clear(fgRgb: Int = 0xE2E8F0, bgRgb: Int = 0x090D16) {
        chars.fill(' ')
        codepoints.fill(' '.code)
        fgRgbArray.fill(fgRgb)
        bgRgbArray.fill(bgRgb)
        styleArray.fill(TuiCellStyle.NONE)
        underlineRgbArray.fill(-1)
        hyperlinkArray.fill(null)
    }

    /**
     * Sets cell content and styling at [row] and [col], ignoring out-of-bounds coordinates.
     */
    fun setCell(
        row: Int,
        col: Int,
        char: Char,
        fgRgb: Int,
        bgRgb: Int,
        style: Int = TuiCellStyle.NONE,
        codepoint: Int = char.code,
        underlineRgb: Int = -1,
        hyperlinkUrl: String? = null,
    ) {
        if (row in 0 until rows && col in 0 until cols) {
            val index = row * cols + col
            chars[index] = char
            codepoints[index] = codepoint
            fgRgbArray[index] = fgRgb
            bgRgbArray[index] = bgRgb
            styleArray[index] = style
            underlineRgbArray[index] = underlineRgb
            hyperlinkArray[index] = hyperlinkUrl
        }
    }

    fun getChar(row: Int, col: Int): Char =
        if (row in 0 until rows && col in 0 until cols) chars[row * cols + col] else ' '

    fun getCodepoint(row: Int, col: Int): Int =
        if (row in 0 until rows && col in 0 until cols) codepoints[row * cols + col] else ' '.code

    fun getFgRgb(row: Int, col: Int): Int =
        if (row in 0 until rows && col in 0 until cols) fgRgbArray[row * cols + col] else 0

    fun getBgRgb(row: Int, col: Int): Int =
        if (row in 0 until rows && col in 0 until cols) bgRgbArray[row * cols + col] else 0

    fun getStyle(row: Int, col: Int): Int =
        if (row in 0 until rows && col in 0 until cols) styleArray[row * cols + col] else TuiCellStyle.NONE

    fun getUnderlineRgb(row: Int, col: Int): Int =
        if (row in 0 until rows && col in 0 until cols) underlineRgbArray[row * cols + col] else -1

    fun getHyperlink(row: Int, col: Int): String? =
        if (row in 0 until rows && col in 0 until cols) hyperlinkArray[row * cols + col] else null

    /**
     * Fills a rectangular region of the surface.
     */
    fun fillRect(
        row: Int,
        col: Int,
        height: Int,
        width: Int,
        char: Char = ' ',
        fgRgb: Int,
        bgRgb: Int,
        style: Int = TuiCellStyle.NONE,
    ) {
        val endRow = minOf(rows, row + height)
        val endCol = minOf(cols, col + width)
        for (r in maxOf(0, row) until endRow) {
            for (c in maxOf(0, col) until endCol) {
                setCell(r, c, char, fgRgb, bgRgb, style)
            }
        }
    }

    /**
     * Draws a line of text at [row] and [col] clipped by [maxWidth] and surface boundaries.
     */
    fun drawText(
        row: Int,
        col: Int,
        text: String,
        fgRgb: Int,
        bgRgb: Int,
        style: Int = TuiCellStyle.NONE,
        maxWidth: Int = cols - col,
    ) {
        if (row !in 0 until rows || col >= cols) return
        val limit = minOf(text.length, maxWidth, cols - col)
        for (i in 0 until limit) {
            setCell(row, col + i, text[i], fgRgb, bgRgb, style)
        }
    }

    /**
     * Draws a bordered box with rounded corners and an optional title.
     */
    fun drawBox(
        row: Int,
        col: Int,
        height: Int,
        width: Int,
        title: String? = null,
        borderFgRgb: Int,
        bgRgb: Int,
        titleFgRgb: Int = borderFgRgb,
    ) {
        if (height < 2 || width < 2) return

        // Corners
        setCell(row, col, '╭', borderFgRgb, bgRgb)
        setCell(row, col + width - 1, '╮', borderFgRgb, bgRgb)
        setCell(row + height - 1, col, '╰', borderFgRgb, bgRgb)
        setCell(row + height - 1, col + width - 1, '╯', borderFgRgb, bgRgb)

        // Top and bottom horizontal borders
        for (c in col + 1 until col + width - 1) {
            setCell(row, c, '─', borderFgRgb, bgRgb)
            setCell(row + height - 1, c, '─', borderFgRgb, bgRgb)
        }

        // Left and right vertical borders and inner fill
        for (r in row + 1 until row + height - 1) {
            setCell(r, col, '│', borderFgRgb, bgRgb)
            for (c in col + 1 until col + width - 1) {
                setCell(r, c, ' ', borderFgRgb, bgRgb)
            }
            setCell(r, col + width - 1, '│', borderFgRgb, bgRgb)
        }

        // Draw title if present
        if (!title.isNullOrEmpty() && width > 4) {
            val titleText = " $title "
            val maxTitleWidth = width - 4
            val displayTitle = if (titleText.length > maxTitleWidth) {
                titleText.take(maxTitleWidth)
            } else {
                titleText
            }
            drawText(row, col + 2, displayTitle, titleFgRgb, bgRgb)
        }
    }

    /**
     * Blits a [TerminalScreenState] snapshot into this surface at [destRow], [destCol].
     */
    fun blitScreenState(
        state: TerminalScreenState,
        destRow: Int,
        destCol: Int,
        height: Int,
        width: Int,
        palette: TerminalPalette = TerminalPalette.DEFAULT,
        scrollOffset: Int = 0,
    ) {
        val startLine = maxOf(0, state.scrollback.size - scrollOffset)
        val renderHeight = minOf(height, rows - destRow)
        val renderWidth = minOf(width, cols - destCol)

        for (r in 0 until renderHeight) {
            val lineIndex = startLine + r
            val terminalRow = when {
                lineIndex < state.scrollback.size -> state.scrollback.getOrNull(lineIndex)
                else -> state.rows.getOrNull(lineIndex - state.scrollback.size)
            }

            for (c in 0 until renderWidth) {
                val cell = terminalRow?.getOrNull(c) ?: TerminalCell.EMPTY
                val fg = TerminalColor.toRgbInt(
                    cell.getEffectiveForegroundColor(),
                    palette,
                    isDark = true,
                    isBackground = false,
                )
                val rawBg = TerminalColor.toRgbInt(
                    cell.getEffectiveBackgroundColor(),
                    palette,
                    isDark = true,
                    isBackground = true,
                )
                val bg = if (rawBg == TerminalColor.TRANSPARENT_RGB) palette.background else rawBg

                var style = TuiCellStyle.NONE
                if (cell.bold) style = style or TuiCellStyle.BOLD
                if (cell.dim) style = style or TuiCellStyle.DIM
                if (cell.italic) style = style or TuiCellStyle.ITALIC
                if (cell.underline) style = style or TuiCellStyle.UNDERLINE
                if (cell.blink) style = style or TuiCellStyle.BLINK
                if (cell.reverse) style = style or TuiCellStyle.REVERSE
                if (cell.strikethrough) style = style or TuiCellStyle.STRIKETHROUGH
                if (cell.overline) style = style or TuiCellStyle.OVERLINE
                if (cell.underline && cell.underlineStyle != UnderlineStyle.NONE) {
                    style = TuiCellStyle.withUnderlineStyle(style, cell.underlineStyle)
                }

                val ulRgb = if (cell.underlineColor === TerminalColor.Default) {
                    -1
                } else {
                    TerminalColor.toRgbInt(cell.underlineColor, palette, isDark = true, isBackground = false)
                }

                val cp = if (cell.isWideSpacer) {
                    0
                } else if (cell.codepoint <= 0) {
                    ' '.code
                } else {
                    cell.codepoint
                }
                val ch = if (cp in 1..0xFFFF) cp.toChar() else ' '
                setCell(
                    destRow + r,
                    destCol + c,
                    ch,
                    fg,
                    bg,
                    style,
                    cp,
                    underlineRgb = ulRgb,
                    hyperlinkUrl = cell.hyperlinkUrl,
                )
            }
        }

        if (scrollOffset == 0 && state.cursorVisible) {
            cursorRow = destRow + state.cursorRow
            cursorCol = destCol + state.cursorCol
            cursorVisible = true
        } else if (scrollOffset > 0) {
            cursorVisible = false
        }
    }

    /**
     * Extracts row text as a String.
     */
    fun extractRowText(row: Int): String {
        if (row !in 0 until rows) return ""
        return chars.concatToString(row * cols, row * cols + cols)
    }

    /**
     * Copies all cells and cursor state from [other] into this surface.
     */
    fun copyFrom(other: TuiSurface) {
        val count = minOf(totalCells, other.totalCells)
        other.chars.copyInto(chars, 0, 0, count)
        other.codepoints.copyInto(codepoints, 0, 0, count)
        other.fgRgbArray.copyInto(fgRgbArray, 0, 0, count)
        other.bgRgbArray.copyInto(bgRgbArray, 0, 0, count)
        other.styleArray.copyInto(styleArray, 0, 0, count)
        other.underlineRgbArray.copyInto(underlineRgbArray, 0, 0, count)
        other.hyperlinkArray.copyInto(hyperlinkArray, 0, 0, count)
        cursorRow = other.cursorRow
        cursorCol = other.cursorCol
        cursorVisible = other.cursorVisible
    }
}
