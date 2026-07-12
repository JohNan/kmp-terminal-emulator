package com.sshclient.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Represents a color scheme for the terminal
 */
data class TerminalColorScheme(
    val id: String,
    val name: String,
    // Base colors
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    // Standard ANSI colors (0-15)
    // Normal colors (0-7)
    val black: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val blue: Color,
    val magenta: Color,
    val cyan: Color,
    val white: Color,
    // Bright colors (8-15)
    val brightBlack: Color,
    val brightRed: Color,
    val brightGreen: Color,
    val brightYellow: Color,
    val brightBlue: Color,
    val brightMagenta: Color,
    val brightCyan: Color,
    val brightWhite: Color,
) {
    /**
     * Get ANSI color by code (0-15)
     */
    fun getAnsiColor(code: Int): Color {
        return when (code) {
            0 -> black
            1 -> red
            2 -> green
            3 -> yellow
            4 -> blue
            5 -> magenta
            6 -> cyan
            7 -> white
            8 -> brightBlack
            9 -> brightRed
            10 -> brightGreen
            11 -> brightYellow
            12 -> brightBlue
            13 -> brightMagenta
            14 -> brightCyan
            15 -> brightWhite
            else -> foreground
        }
    }

    companion object {
        /**
         * Default color scheme (dark theme, similar to VS Code)
         */
        val DEFAULT =
            TerminalColorScheme(
                id = "default",
                name = "Default",
                background = Color(0xFF1E1E1E),
                foreground = Color(0xFFE0E0E0),
                cursor = Color(0xFFE0E0E0),
                // Normal colors
                black = Color(0xFF000000),
                red = Color(0xFFCD0000),
                green = Color(0xFF00CD00),
                yellow = Color(0xFFCDCD00),
                blue = Color(0xFF0000EE),
                magenta = Color(0xFFCD00CD),
                cyan = Color(0xFF00CDCD),
                white = Color(0xFFE5E5E5),
                // Bright colors
                brightBlack = Color(0xFF7F7F7F),
                brightRed = Color(0xFFFF0000),
                brightGreen = Color(0xFF00FF00),
                brightYellow = Color(0xFFFFFF00),
                brightBlue = Color(0xFF5C5CFF),
                brightMagenta = Color(0xFFFF00FF),
                brightCyan = Color(0xFF00FFFF),
                brightWhite = Color(0xFFFFFFFF),
            )

        /**
         * Solarized Dark color scheme
         */
        val SOLARIZED_DARK =
            TerminalColorScheme(
                id = "solarized_dark",
                name = "Solarized Dark",
                background = Color(0xFF002B36),
                foreground = Color(0xFF839496),
                cursor = Color(0xFF839496),
                // Normal colors
                black = Color(0xFF073642),
                red = Color(0xFFDC322F),
                green = Color(0xFF859900),
                yellow = Color(0xFFB58900),
                blue = Color(0xFF268BD2),
                magenta = Color(0xFFD33682),
                cyan = Color(0xFF2AA198),
                white = Color(0xFFEEE8D5),
                // Bright colors
                brightBlack = Color(0xFF002B36),
                brightRed = Color(0xFFCB4B16),
                brightGreen = Color(0xFF586E75),
                brightYellow = Color(0xFF657B83),
                brightBlue = Color(0xFF839496),
                brightMagenta = Color(0xFF6C71C4),
                brightCyan = Color(0xFF93A1A1),
                brightWhite = Color(0xFFFDF6E3),
            )

        /**
         * Solarized Light color scheme
         */
        val SOLARIZED_LIGHT =
            TerminalColorScheme(
                id = "solarized_light",
                name = "Solarized Light",
                background = Color(0xFFFDF6E3),
                foreground = Color(0xFF657B83),
                cursor = Color(0xFF657B83),
                // Normal colors
                black = Color(0xFF073642),
                red = Color(0xFFDC322F),
                green = Color(0xFF859900),
                yellow = Color(0xFFB58900),
                blue = Color(0xFF268BD2),
                magenta = Color(0xFFD33682),
                cyan = Color(0xFF2AA198),
                white = Color(0xFFEEE8D5),
                // Bright colors
                brightBlack = Color(0xFF002B36),
                brightRed = Color(0xFFCB4B16),
                brightGreen = Color(0xFF586E75),
                brightYellow = Color(0xFF657B83),
                brightBlue = Color(0xFF839496),
                brightMagenta = Color(0xFF6C71C4),
                brightCyan = Color(0xFF93A1A1),
                brightWhite = Color(0xFFFDF6E3),
            )

        /**
         * Monokai color scheme
         */
        val MONOKAI =
            TerminalColorScheme(
                id = "monokai",
                name = "Monokai",
                background = Color(0xFF272822),
                foreground = Color(0xFFF8F8F2),
                cursor = Color(0xFFF8F8F0),
                // Normal colors
                black = Color(0xFF272822),
                red = Color(0xFFF92672),
                green = Color(0xFFA6E22E),
                yellow = Color(0xFFE6DB74),
                blue = Color(0xFF66D9EF),
                magenta = Color(0xFFAE81FF),
                cyan = Color(0xFFA1EFE4),
                white = Color(0xFFF8F8F2),
                // Bright colors
                brightBlack = Color(0xFF75715E),
                brightRed = Color(0xFFF92672),
                brightGreen = Color(0xFFA6E22E),
                brightYellow = Color(0xFFE6DB74),
                brightBlue = Color(0xFF66D9EF),
                brightMagenta = Color(0xFFAE81FF),
                brightCyan = Color(0xFFA1EFE4),
                brightWhite = Color(0xFFF9F8F5),
            )

        /**
         * Nord color scheme
         */
        val NORD =
            TerminalColorScheme(
                id = "nord",
                name = "Nord",
                background = Color(0xFF2E3440),
                foreground = Color(0xFFD8DEE9),
                cursor = Color(0xFFD8DEE9),
                // Normal colors
                black = Color(0xFF3B4252),
                red = Color(0xFFBF616A),
                green = Color(0xFFA3BE8C),
                yellow = Color(0xFFEBCB8B),
                blue = Color(0xFF81A1C1),
                magenta = Color(0xFFB48EAD),
                cyan = Color(0xFF88C0D0),
                white = Color(0xFFE5E9F0),
                // Bright colors
                brightBlack = Color(0xFF4C566A),
                brightRed = Color(0xFFBF616A),
                brightGreen = Color(0xFFA3BE8C),
                brightYellow = Color(0xFFEBCB8B),
                brightBlue = Color(0xFF81A1C1),
                brightMagenta = Color(0xFFB48EAD),
                brightCyan = Color(0xFF8FBCBB),
                brightWhite = Color(0xFFECEFF4),
            )

        /**
         * All available preset color schemes
         */
        val PRESETS =
            listOf(
                DEFAULT,
                SOLARIZED_DARK,
                SOLARIZED_LIGHT,
                MONOKAI,
                NORD,
            )

        /**
         * Get preset color scheme by ID
         */
        fun getById(id: String): TerminalColorScheme? {
            return PRESETS.find { it.id == id }
        }
    }
}
