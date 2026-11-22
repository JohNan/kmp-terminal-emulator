package com.johnan.terminal.core

/**
 * Terminal mouse tracking interaction event types.
 */
enum class MouseEvent {
    Press,
    Release,
    Drag,
    WheelUp,
    WheelDown,
}

/**
 * Mouse buttons supported by terminal pointer tracking protocols.
 */
enum class MouseButton {
    LEFT,
    MIDDLE,
    RIGHT,
    NONE,
}

/**
 * Pointer mouse interaction event with 0-based grid coordinates and modifier state.
 */
data class PointerMouseEvent(
    val type: MouseEvent,
    val button: MouseButton = MouseButton.LEFT,
    val row: Int,
    val col: Int,
    val shiftPressed: Boolean = false,
    val altPressed: Boolean = false,
    val ctrlPressed: Boolean = false,
) {
    /**
     * Encodes this pointer event into an ANSI SGR (`\u001B[<...`) or X10/X11 (`\u001B[M...`) sequence.
     *
     * Returns `null` when coordinates are negative (`< 0`), or when X10/X11 1-based coordinates exceed `223`.
     */
    fun encode(sgrMode: Boolean): String? {
        val ansiRow = row + 1
        val ansiCol = col + 1
        if (ansiRow < 1 || ansiCol < 1) return null

        val baseCode =
            when (type) {
                MouseEvent.WheelUp -> 64
                MouseEvent.WheelDown -> 65
                MouseEvent.Release -> if (sgrMode) buttonCode(noneCode = 0) else 3
                MouseEvent.Press -> buttonCode(noneCode = 0)
                MouseEvent.Drag -> 32 + buttonCode(noneCode = 3)
            }

        var code = baseCode
        if (shiftPressed) code = code or 4
        if (altPressed) code = code or 8
        if (ctrlPressed) code = code or 16

        return if (sgrMode) {
            val suffix = if (type == MouseEvent.Release) 'm' else 'M'
            "\u001B[<$code;$ansiCol;$ansiRow$suffix"
        } else {
            if (ansiCol > 223 || ansiRow > 223) return null
            "\u001B[M${Char(code + 32)}${Char(ansiCol + 32)}${Char(ansiRow + 32)}"
        }
    }

    private fun buttonCode(noneCode: Int): Int =
        when (button) {
            MouseButton.LEFT -> 0
            MouseButton.MIDDLE -> 1
            MouseButton.RIGHT -> 2
            MouseButton.NONE -> noneCode
        }
}
