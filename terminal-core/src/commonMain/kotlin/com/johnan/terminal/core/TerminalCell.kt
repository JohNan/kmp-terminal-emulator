package com.johnan.terminal.core

import androidx.compose.ui.graphics.Color

/**
 * Represents a single character cell in the terminal with its attributes
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
     * Get the effective foreground color considering reverse video
     */
    fun getEffectiveForegroundColor(): TerminalColor {
        return if (reverse) backgroundColor else foregroundColor
    }

    /**
     * Get the effective background color considering reverse video
     */
    fun getEffectiveBackgroundColor(): TerminalColor {
        return if (reverse) foregroundColor else backgroundColor
    }

    /**
     * Check if the cell is in its default state (empty and no attributes)
     */
    fun isDefault(): Boolean {
        // Fast path: Exact object reference match
        if (this === EMPTY) return true

        // Fast path: Cached ASCII space check
        if (char.code == 32 && this === ASCII_CACHE[32]) return true

        return char == ' ' &&
            foregroundColor === TerminalColor.Default &&
            backgroundColor === TerminalColor.Default &&
            !bold && !italic && !underline && !reverse && !dim && !blink &&
            !strikethrough && !overline && !conceal
    }

    companion object {
        val EMPTY = TerminalCell()

        // Cache for standard ASCII characters (0-127) with default attributes.
        // This optimization reduces object allocation for the most common case: plain text.
        private val ASCII_CACHE = Array(128) { i ->
            TerminalCell(char = i.toChar())
        }

        /**
         * Get cached ASCII character for default attributes (internal optimization).
         */
        internal fun getCachedAscii(code: Int) = ASCII_CACHE[code]

        /**
         * Factory method to obtain a TerminalCell instance.
         * Uses cached instances for standard ASCII characters with default attributes.
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
            // Check if we can use the cache
            if (char.code in 0..127 &&
                foregroundColor === TerminalColor.Default &&
                backgroundColor === TerminalColor.Default &&
                !bold && !italic && !underline && !reverse && !dim && !blink &&
                !strikethrough && !overline && !conceal
            ) {
                return ASCII_CACHE[char.code]
            }

            // Fallback to creating a new instance
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
 * Terminal color representation supporting both standard and 256-color modes
 */
sealed class TerminalColor {
    /** Default terminal color (uses theme) */
    data object Default : TerminalColor()

    /** Standard ANSI colors (0-15) */
    data class Standard(val code: Int) : TerminalColor()

    /** 256-color palette (16-255) */
    data class Palette256(val code: Int) : TerminalColor()

    /** True color RGB */
    data class Rgb(val r: Int, val g: Int, val b: Int) : TerminalColor()

    companion object {
        // Cache for Standard Colors (0-15) in Dark Mode
        private val STANDARD_DARK_CACHE = Array(16) { i -> computeStandardColor(i, true) }

        // Cache for Standard Colors (0-15) in Light Mode
        private val STANDARD_LIGHT_CACHE = Array(16) { i -> computeStandardColor(i, false) }

        // Cache for 256-color palette (0-255)
        // Note: First 16 use dark mode standard colors by default as per xterm-256color convention
        private val PALETTE_256_CACHE = Array(256) { i -> compute256Color(i) }

        // Cache for TerminalColor instances (Flyweight pattern)
        private val STANDARD_INSTANCES = Array(16) { i -> Standard(i) }
        private val PALETTE_256_INSTANCES = Array(256) { i -> Palette256(i) }

        /**
         * Get a cached Standard terminal color instance.
         */
        fun buildStandard(code: Int): Standard {
            return if (code in 0..15) {
                STANDARD_INSTANCES[code]
            } else {
                Standard(code)
            }
        }

        /**
         * Get a cached Palette256 terminal color instance.
         */
        fun buildPalette256(code: Int): Palette256 {
            return if (code in 0..255) {
                PALETTE_256_INSTANCES[code]
            } else {
                Palette256(code)
            }
        }

        /**
         * Convert terminal color to Compose Color
         * @param scheme Optional color scheme to use for standard colors (0-15)
         * @param isDark Whether the current theme is dark (used as fallback when scheme is null)
         */
        fun toComposeColor(
            terminalColor: TerminalColor,
            scheme: TerminalColorScheme? = null,
            isDark: Boolean = true,
            isBackground: Boolean = false,
        ): Color {
            return when (terminalColor) {
                is Default -> {
                    if (isBackground) {
                        scheme?.background ?: Color.Transparent
                    } else {
                        scheme?.foreground ?: (if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000))
                    }
                }
                is Standard -> {
                    // Use scheme colors if available, otherwise fall back to hardcoded colors
                    scheme?.getAnsiColor(terminalColor.code) ?: getStandardColor(terminalColor.code, isDark)
                }
                is Palette256 -> get256Color(terminalColor.code)
                is Rgb -> Color(terminalColor.r, terminalColor.g, terminalColor.b)
            }
        }

        /**
         * Get standard ANSI color from cache
         */
        private fun getStandardColor(
            code: Int,
            isDark: Boolean,
        ): Color {
            return if (code in 0..15) {
                if (isDark) STANDARD_DARK_CACHE[code] else STANDARD_LIGHT_CACHE[code]
            } else {
                if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000)
            }
        }

        /**
         * Compute standard ANSI colors (0-15)
         * Used for cache initialization
         */
        private fun computeStandardColor(
            code: Int,
            isDark: Boolean,
        ): Color {
            return when (code) {
                // Normal colors (0-7)
                0 -> Color(0xFF000000) // Black
                1 -> Color(0xFFCD0000) // Red
                2 -> Color(0xFF00CD00) // Green
                3 -> Color(0xFFCDCD00) // Yellow
                4 -> Color(0xFF0000EE) // Blue
                5 -> Color(0xFFCD00CD) // Magenta
                6 -> Color(0xFF00CDCD) // Cyan
                7 -> Color(0xFFE5E5E5) // White
                // Bright colors (8-15)
                8 -> Color(0xFF7F7F7F) // Bright Black (Gray)
                9 -> Color(0xFFFF0000) // Bright Red
                10 -> Color(0xFF00FF00) // Bright Green
                11 -> Color(0xFFFFFF00) // Bright Yellow
                12 -> Color(0xFF5C5CFF) // Bright Blue
                13 -> Color(0xFFFF00FF) // Bright Magenta
                14 -> Color(0xFF00FFFF) // Bright Cyan
                15 -> Color(0xFFFFFFFF) // Bright White
                else -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000)
            }
        }

        /**
         * Get 256-color palette color from cache
         */
        private fun get256Color(code: Int): Color {
            return if (code in 0..255) {
                PALETTE_256_CACHE[code]
            } else {
                Color(0xFFE0E0E0)
            }
        }

        /**
         * Compute 256-color palette color
         * Used for cache initialization
         */
        private fun compute256Color(code: Int): Color {
            return when {
                // Standard colors (0-15)
                code < 16 -> computeStandardColor(code, true)
                // 216-color cube (16-231): 6x6x6 RGB cube
                code < 232 -> {
                    val index = code - 16
                    val r = (index / 36) * 51
                    val g = ((index % 36) / 6) * 51
                    val b = (index % 6) * 51
                    Color(r, g, b)
                }
                // Grayscale (232-255): 24 shades of gray
                code < 256 -> {
                    val gray = 8 + (code - 232) * 10
                    Color(gray, gray, gray)
                }
                else -> Color(0xFFE0E0E0)
            }
        }
    }
}
