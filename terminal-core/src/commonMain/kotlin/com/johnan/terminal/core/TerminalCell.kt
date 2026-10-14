package com.johnan.terminal.core

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
        const val TRANSPARENT_RGB: Int = -1

        private val STANDARD_DARK_CACHE = IntArray(16) { i -> computeStandardRgb(i, true) }
        private val STANDARD_LIGHT_CACHE = IntArray(16) { i -> computeStandardRgb(i, false) }
        private val PALETTE_256_CACHE = IntArray(256) { i -> compute256Rgb(i) }

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
         * Converts this [TerminalColor] to a 24-bit RGB integer (0xRRGGBB).
         * Returns [TRANSPARENT_RGB] (-1) when [terminalColor] is [Default] as a background and [palette] is null.
         */
        fun toRgbInt(
            terminalColor: TerminalColor,
            palette: TerminalPalette? = null,
            isDark: Boolean = true,
            isBackground: Boolean = false,
        ): Int = when (terminalColor) {
            is Default -> {
                if (isBackground) {
                    palette?.background ?: TRANSPARENT_RGB
                } else {
                    palette?.foreground ?: (if (isDark) 0xE0E0E0 else 0x000000)
                }
            }
            is Standard -> {
                palette?.getAnsiRgb(terminalColor.code) ?: getStandardRgb(terminalColor.code, isDark)
            }
            is Palette256 -> get256Rgb(terminalColor.code)
            is Rgb -> ((terminalColor.r and 0xFF) shl 16) or ((terminalColor.g and 0xFF) shl 8) or
                (terminalColor.b and 0xFF)
        }

        fun red(rgb: Int): Int = (rgb shr 16) and 0xFF

        fun green(rgb: Int): Int = (rgb shr 8) and 0xFF

        fun blue(rgb: Int): Int = rgb and 0xFF

        private fun getStandardRgb(code: Int, isDark: Boolean): Int = if (code in 0..15) {
            if (isDark) STANDARD_DARK_CACHE[code] else STANDARD_LIGHT_CACHE[code]
        } else {
            if (isDark) 0xE0E0E0 else 0x000000
        }

        private fun computeStandardRgb(code: Int, isDark: Boolean): Int = when (code) {
            0 -> 0x000000
            1 -> 0xCD0000
            2 -> 0x00CD00
            3 -> 0xCDCD00
            4 -> 0x0000EE
            5 -> 0xCD00CD
            6 -> 0x00CDCD
            7 -> 0xE5E5E5
            8 -> 0x7F7F7F
            9 -> 0xFF0000
            10 -> 0x00FF00
            11 -> 0xFFFF00
            12 -> 0x5C5CFF
            13 -> 0xFF00FF
            14 -> 0x00FFFF
            15 -> 0xFFFFFF
            else -> if (isDark) 0xE0E0E0 else 0x000000
        }

        private fun get256Rgb(code: Int): Int = if (code in 0..255) PALETTE_256_CACHE[code] else 0xE0E0E0

        private fun compute256Rgb(code: Int): Int = when {
            code < 16 -> computeStandardRgb(code, true)
            code < 232 -> {
                val index = code - 16
                val r = (index / 36) * 51
                val g = ((index % 36) / 6) * 51
                val b = (index % 6) * 51
                (r shl 16) or (g shl 8) or b
            }
            code < 256 -> {
                val gray = 8 + (code - 232) * 10
                (gray shl 16) or (gray shl 8) or gray
            }
            else -> 0xE0E0E0
        }
    }
}
