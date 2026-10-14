package com.johnan.terminal.core

/**
 * Color palette definition for terminal background, foreground, cursor, and standard 16 ANSI colors,
 * stored as packed 24-bit RGB integers (0xRRGGBB).
 */
data class TerminalPalette(
    val id: String,
    val name: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    val black: Int,
    val red: Int,
    val green: Int,
    val yellow: Int,
    val blue: Int,
    val magenta: Int,
    val cyan: Int,
    val white: Int,
    val brightBlack: Int,
    val brightRed: Int,
    val brightGreen: Int,
    val brightYellow: Int,
    val brightBlue: Int,
    val brightMagenta: Int,
    val brightCyan: Int,
    val brightWhite: Int,
) {
    /**
     * Resolves packed 24-bit RGB int for standard ANSI color code (0-15), defaulting to [foreground].
     */
    fun getAnsiRgb(code: Int): Int = when (code) {
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

    companion object {
        val DEFAULT = TerminalPalette(
            id = "default",
            name = "Default",
            background = 0x1E1E1E,
            foreground = 0xE0E0E0,
            cursor = 0xE0E0E0,
            black = 0x000000,
            red = 0xCD0000,
            green = 0x00CD00,
            yellow = 0xCDCD00,
            blue = 0x0000EE,
            magenta = 0xCD00CD,
            cyan = 0x00CDCD,
            white = 0xE5E5E5,
            brightBlack = 0x7F7F7F,
            brightRed = 0xFF0000,
            brightGreen = 0x00FF00,
            brightYellow = 0xFFFF00,
            brightBlue = 0x5C5CFF,
            brightMagenta = 0xFF00FF,
            brightCyan = 0x00FFFF,
            brightWhite = 0xFFFFFF,
        )

        val SOLARIZED_DARK = TerminalPalette(
            id = "solarized_dark",
            name = "Solarized Dark",
            background = 0x002B36,
            foreground = 0x839496,
            cursor = 0x839496,
            black = 0x073642,
            red = 0xDC322F,
            green = 0x859900,
            yellow = 0xB58900,
            blue = 0x268BD2,
            magenta = 0xD33682,
            cyan = 0x2AA198,
            white = 0xEEE8D5,
            brightBlack = 0x002B36,
            brightRed = 0xCB4B16,
            brightGreen = 0x586E75,
            brightYellow = 0x657B83,
            brightBlue = 0x839496,
            brightMagenta = 0x6C71C4,
            brightCyan = 0x93A1A1,
            brightWhite = 0xFDF6E3,
        )

        val SOLARIZED_LIGHT = TerminalPalette(
            id = "solarized_light",
            name = "Solarized Light",
            background = 0xFDF6E3,
            foreground = 0x657B83,
            cursor = 0x657B83,
            black = 0x073642,
            red = 0xDC322F,
            green = 0x859900,
            yellow = 0xB58900,
            blue = 0x268BD2,
            magenta = 0xD33682,
            cyan = 0x2AA198,
            white = 0xEEE8D5,
            brightBlack = 0x002B36,
            brightRed = 0xCB4B16,
            brightGreen = 0x586E75,
            brightYellow = 0x657B83,
            brightBlue = 0x839496,
            brightMagenta = 0x6C71C4,
            brightCyan = 0x93A1A1,
            brightWhite = 0xFDF6E3,
        )

        val MONOKAI = TerminalPalette(
            id = "monokai",
            name = "Monokai",
            background = 0x272822,
            foreground = 0xF8F8F2,
            cursor = 0xF8F8F0,
            black = 0x272822,
            red = 0xF92672,
            green = 0xA6E22E,
            yellow = 0xE6DB74,
            blue = 0x66D9EF,
            magenta = 0xAE81FF,
            cyan = 0xA1EFE4,
            white = 0xF8F8F2,
            brightBlack = 0x75715E,
            brightRed = 0xF92672,
            brightGreen = 0xA6E22E,
            brightYellow = 0xE6DB74,
            brightBlue = 0x66D9EF,
            brightMagenta = 0xAE81FF,
            brightCyan = 0xA1EFE4,
            brightWhite = 0xF9F8F5,
        )

        val NORD = TerminalPalette(
            id = "nord",
            name = "Nord",
            background = 0x2E3440,
            foreground = 0xD8DEE9,
            cursor = 0xD8DEE9,
            black = 0x3B4252,
            red = 0xBF616A,
            green = 0xA3BE8C,
            yellow = 0xEBCB8B,
            blue = 0x81A1C1,
            magenta = 0xB48EAD,
            cyan = 0x88C0D0,
            white = 0xE5E9F0,
            brightBlack = 0x4C566A,
            brightRed = 0xBF616A,
            brightGreen = 0xA3BE8C,
            brightYellow = 0xEBCB8B,
            brightBlue = 0x81A1C1,
            brightMagenta = 0xB48EAD,
            brightCyan = 0x8FBCBB,
            brightWhite = 0xECEFF4,
        )

        val PRESETS = listOf(
            DEFAULT,
            SOLARIZED_DARK,
            SOLARIZED_LIGHT,
            MONOKAI,
            NORD,
        )

        fun getById(id: String): TerminalPalette? = PRESETS.find { it.id == id }
    }
}
