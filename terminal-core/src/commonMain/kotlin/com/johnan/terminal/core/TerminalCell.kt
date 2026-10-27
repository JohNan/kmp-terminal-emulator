package com.johnan.terminal.core

/**
 * Character cell within the terminal grid containing glyph data and visual styling attributes.
 */
object UnicodeWidth {
    fun wcwidth(codepoint: Int): Int = when {
        codepoint == KITTY_PLACEHOLDER_CODEPOINT -> 1
        KittyUnicodePlaceholders.diacriticToIndex(codepoint) >= 0 -> 0
        codepoint == 0 ||
            codepoint < 0x20 ||
            codepoint in 0x7F..0x9F ||
            codepoint in 0x0300..0x036F ||
            codepoint in 0x1AB0..0x1AFF ||
            codepoint in 0x1DC0..0x1DFF ||
            codepoint in 0x20D0..0x20FF ||
            codepoint in 0xFE20..0xFE2F -> 0
        codepoint in 0x1100..0x115F ||
            codepoint == 0x2329 ||
            codepoint == 0x232A ||
            codepoint in 0x231A..0x231B ||
            codepoint in 0x23E9..0x23F3 ||
            codepoint in 0x23F8..0x23FA ||
            codepoint in 0x25FD..0x25FE ||
            codepoint in 0x2614..0x2615 ||
            codepoint in 0x2648..0x2653 ||
            codepoint == 0x267F ||
            codepoint == 0x2693 ||
            codepoint == 0x26A1 ||
            codepoint in 0x26AA..0x26AB ||
            codepoint in 0x26BD..0x26BE ||
            codepoint in 0x26C4..0x26C5 ||
            codepoint == 0x26CE ||
            codepoint == 0x26D4 ||
            codepoint == 0x26EA ||
            codepoint in 0x26F2..0x26F3 ||
            codepoint == 0x26F5 ||
            codepoint == 0x26FA ||
            codepoint == 0x26FD ||
            codepoint == 0x2705 ||
            codepoint in 0x270A..0x270B ||
            codepoint == 0x2728 ||
            codepoint == 0x274C ||
            codepoint == 0x274E ||
            codepoint in 0x2753..0x2755 ||
            codepoint == 0x2757 ||
            codepoint in 0x2795..0x2797 ||
            codepoint == 0x27B0 ||
            codepoint == 0x27BF ||
            codepoint in 0x2B1B..0x2B1C ||
            codepoint == 0x2B50 ||
            codepoint == 0x2B55 ||
            codepoint in 0x2E80..0x303E ||
            codepoint in 0x3040..0xA4CF ||
            codepoint in 0xAC00..0xD7A3 ||
            codepoint in 0xF900..0xFAFF ||
            codepoint in 0xFE10..0xFE19 ||
            codepoint in 0xFE30..0xFE6F ||
            codepoint in 0xFF00..0xFF60 ||
            codepoint in 0xFFE0..0xFFE6 ||
            codepoint in 0x1F000..0x1FAFF ||
            codepoint in 0x20000..0x3FFFD -> 2
        else -> 1
    }
}

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
    val codepoint: Int = char.code,
    val width: Int = 1,
    val isWideSpacer: Boolean = false,
    val underlineColor: TerminalColor = TerminalColor.Default,
    val kittyPlaceholderInfo: Int = 0,
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
     * Helper string representation of this cell's glyph, handling surrogate pairs and spacers.
     */
    fun glyphString(): String {
        if (isWideSpacer) return ""
        if (codepoint <= 0xFFFF) return codepoint.toChar().toString()
        val high = ((codepoint - 0x10000) shr 10) + 0xD800
        val low = ((codepoint - 0x10000) and 0x3FF) + 0xDC00
        return charArrayOf(high.toChar(), low.toChar()).concatToString()
    }

    /**
     * Returns true if the cell has default attributes and blank character content.
     */
    fun isDefault(): Boolean {
        if (this === EMPTY) return true
        if (char.code == 32 && this === ASCII_CACHE[32]) return true

        return char == ' ' &&
            codepoint == 32 &&
            width == 1 &&
            !isWideSpacer &&
            foregroundColor === TerminalColor.Default &&
            backgroundColor === TerminalColor.Default &&
            underlineColor === TerminalColor.Default &&
            kittyPlaceholderInfo == 0 &&
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
         * Creates a [TerminalCell] for a 32-bit Unicode [codepoint] with visual attributes.
         */
        fun createCodepoint(
            codepoint: Int,
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
            width: Int = UnicodeWidth.wcwidth(codepoint).coerceAtLeast(1),
            isWideSpacer: Boolean = false,
            underlineColor: TerminalColor = TerminalColor.Default,
            kittyPlaceholderInfo: Int = 0,
        ): TerminalCell {
            if (codepoint in 0..127 &&
                !isWideSpacer &&
                width == 1 &&
                foregroundColor === TerminalColor.Default &&
                backgroundColor === TerminalColor.Default &&
                underlineColor === TerminalColor.Default &&
                kittyPlaceholderInfo == 0 &&
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
                return ASCII_CACHE[codepoint]
            }

            val ch = if (codepoint in 0..0xFFFF) codepoint.toChar() else ' '
            return TerminalCell(
                char = ch,
                foregroundColor = foregroundColor,
                backgroundColor = backgroundColor,
                bold = bold,
                italic = italic,
                underline = underline,
                reverse = reverse,
                dim = dim,
                blink = blink,
                strikethrough = strikethrough,
                overline = overline,
                conceal = conceal,
                codepoint = codepoint,
                width = width,
                isWideSpacer = isWideSpacer,
                underlineColor = underlineColor,
                kittyPlaceholderInfo = kittyPlaceholderInfo,
            )
        }

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
            underlineColor: TerminalColor = TerminalColor.Default,
            kittyPlaceholderInfo: Int = 0,
        ): TerminalCell = createCodepoint(
            codepoint = char.code,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            bold = bold,
            italic = italic,
            underline = underline,
            reverse = reverse,
            dim = dim,
            blink = blink,
            strikethrough = strikethrough,
            overline = overline,
            conceal = conceal,
            underlineColor = underlineColor,
            kittyPlaceholderInfo = kittyPlaceholderInfo,
        )
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

        fun toRawColorInt(terminalColor: TerminalColor): Int = when (terminalColor) {
            is Default -> -1
            is Standard -> 0x0100_0000 or (terminalColor.code and 0xFF)
            is Palette256 -> 0x0200_0000 or (terminalColor.code and 0xFF)
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
