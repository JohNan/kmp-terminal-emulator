package com.johnan.terminal.core

import androidx.compose.ui.graphics.Color

/**
 * Character cell within the terminal grid containing glyph data and visual styling attributes.
 */
data class TerminalCell(
    val char: Char = ' ',
    val foregroundColor: TerminalColor = TerminalColor.Default,
    val backgroundColor: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val reverse: Boolean = false,
    val dim: Boolean = false,
    val blink: Boolean = false,
    val strikethrough: Boolean = false,
    val overline: Boolean = false,
    val conceal: Boolean = false,
) {
    /**
     * Resolves foreground color taking reverse video (inverse attribute) into account.
     */
    fun getEffectiveForegroundColor(): TerminalColor = if (reverse) backgroundColor else foregroundColor

    /**
     * Resolves background color taking reverse video (inverse attribute) into account.
     */
    fun getEffectiveBackgroundColor(): TerminalColor = if (reverse) foregroundColor else backgroundColor

    /**
     * Returns true if the cell has default attributes and blank character content.
     */
    fun isDefault(): Boolean {
        if (this === EMPTY) return true
        if (char.code == 32 && this === ASCII_CACHE[32]) return true

        return char == ' ' &&
            foregroundColor === TerminalColor.Default &&
            backgroundColor === TerminalColor.Default &&
            !bold &&
            !italic &&
            !underline &&
            !reverse &&
            !dim &&
            !blink &&
            !strikethrough &&
            !overline &&
            !conceal
    }

    companion object {
        val EMPTY = TerminalCell()

        private val ASCII_CACHE = Array(128) { i ->
            TerminalCell(char = i.toChar())
        }

        internal fun getCachedAscii(code: Int): TerminalCell = ASCII_CACHE[code]

        /**
         * Obtains a cell instance, leveraging flyweight cached ASCII instances for plain text.
         */
        fun create(
            char: Char,
            foregroundColor: TerminalColor = TerminalColor.Default,
            backgroundColor: TerminalColor = TerminalColor.Default,
            bold: Boolean = false,
            italic: Boolean = false,
            underline: Boolean = false,
            reverse: Boolean = false,
            dim: Boolean = false,
            blink: Boolean = false,
            strikethrough: Boolean = false,
            overline: Boolean = false,
            conceal: Boolean = false,
        ): TerminalCell {
            if (char.code in 0..127 &&
                foregroundColor === TerminalColor.Default &&
                backgroundColor === TerminalColor.Default &&
                !bold &&
                !italic &&
                !underline &&
                !reverse &&
                !dim &&
                !blink &&
                !strikethrough &&
                !overline &&
                !conceal
            ) {
                return ASCII_CACHE[char.code]
            }

            return TerminalCell(
                char,
                foregroundColor,
                backgroundColor,
                bold,
                italic,
                underline,
                reverse,
                dim,
                blink,
                strikethrough,
                overline,
                conceal,
            )
        }
    }
}

/**
 * Terminal color representation supporting standard ANSI, 256-color palette, and 24-bit TrueColor RGB.
 */
sealed class TerminalColor {
    data object Default : TerminalColor()

    data class Standard(
        val code: Int
    ) : TerminalColor()

    data class Palette256(
        val code: Int
    ) : TerminalColor()

    data class Rgb(
        val r: Int,
        val g: Int,
        val b: Int
    ) : TerminalColor()

    companion object {
        private val STANDARD_DARK_CACHE = Array(16) { i -> computeStandardColor(i, true) }
        private val STANDARD_LIGHT_CACHE = Array(16) { i -> computeStandardColor(i, false) }
        private val PALETTE_256_CACHE = Array(256) { i -> compute256Color(i) }

        private val STANDARD_INSTANCES = Array(16) { i -> Standard(i) }
        private val PALETTE_256_INSTANCES = Array(256) { i -> Palette256(i) }

        /**
         * Returns flyweight cached [Standard] color instance for codes 0..15.
         */
        fun buildStandard(code: Int): Standard = if (code in 0..15) STANDARD_INSTANCES[code] else Standard(code)

        /**
         * Returns flyweight cached [Palette256] color instance for codes 0..255.
         */
        fun buildPalette256(
            code: Int
        ): Palette256 = if (code in 0..255) PALETTE_256_INSTANCES[code] else Palette256(code)

        /**
         * Converts this [TerminalColor] to a Compose [Color].
         */
        fun toComposeColor(
            terminalColor: TerminalColor,
            scheme: TerminalColorScheme? = null,
            isDark: Boolean = true,
            isBackground: Boolean = false,
        ): Color = when (terminalColor) {
            is Default -> {
                if (isBackground) {
                    scheme?.background ?: Color.Transparent
                } else {
                    scheme?.foreground ?: (if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000))
                }
            }
            is Standard -> {
                scheme?.getAnsiColor(terminalColor.code) ?: getStandardColor(terminalColor.code, isDark)
            }
            is Palette256 -> get256Color(terminalColor.code)
            is Rgb -> Color(terminalColor.r, terminalColor.g, terminalColor.b)
        }

        private fun getStandardColor(code: Int, isDark: Boolean): Color = if (code in 0..15) {
            if (isDark) STANDARD_DARK_CACHE[code] else STANDARD_LIGHT_CACHE[code]
        } else {
            if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000)
        }

        private fun computeStandardColor(code: Int, isDark: Boolean): Color = when (code) {
            0 -> Color(0xFF000000)
            1 -> Color(0xFFCD0000)
            2 -> Color(0xFF00CD00)
            3 -> Color(0xFFCDCD00)
            4 -> Color(0xFF0000EE)
            5 -> Color(0xFFCD00CD)
            6 -> Color(0xFF00CDCD)
            7 -> Color(0xFFE5E5E5)
            8 -> Color(0xFF7F7F7F)
            9 -> Color(0xFFFF0000)
            10 -> Color(0xFF00FF00)
            11 -> Color(0xFFFFFF00)
            12 -> Color(0xFF5C5CFF)
            13 -> Color(0xFFFF00FF)
            14 -> Color(0xFF00FFFF)
            15 -> Color(0xFFFFFFFF)
            else -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000)
        }

        private fun get256Color(code: Int): Color = if (code in 0..255) PALETTE_256_CACHE[code] else Color(0xFFE0E0E0)

        private fun compute256Color(code: Int): Color = when {
            code < 16 -> computeStandardColor(code, true)
            code < 232 -> {
                val index = code - 16
                val r = (index / 36) * 51
                val g = ((index % 36) / 6) * 51
                val b = (index % 6) * 51
                Color(r, g, b)
            }
            code < 256 -> {
                val gray = 8 + (code - 232) * 10
                Color(gray, gray, gray)
            }
            else -> Color(0xFFE0E0E0)
        }
    }
}
