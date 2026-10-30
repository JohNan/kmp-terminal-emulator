package com.johnan.terminal.core

/**
 * SGR 4:x underline rendering styles per ITU T.416 / modern terminal extensions.
 */
enum class UnderlineStyle {
    NONE,
    SINGLE,
    DOUBLE,
    CURLY,
    DOTTED,
    DASHED,
    ;

    companion object {
        fun fromSubParam(code: Int): UnderlineStyle = when (code) {
            0 -> NONE
            1 -> SINGLE
            2 -> DOUBLE
            3 -> CURLY
            4 -> DOTTED
            5 -> DASHED
            else -> SINGLE
        }
    }
}

/**
 * DECSCUSR (`CSI Ps SP q`) cursor shape state reported by the terminal engine.
 */
enum class CursorShape {
    BLOCK,
    UNDERLINE,
    BEAM,
}
