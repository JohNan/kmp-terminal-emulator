package com.johnan.terminal.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.ArrowDirection
import com.johnan.terminal.core.KittyKeyboardProtocol
import com.johnan.terminal.core.KeyEventType as CoreKeyEventType

const val MAX_INPUT_BUFFER_SIZE = 100
const val INPUT_BUFFER_TRIM_SIZE = 50
val HIDDEN_TEXT_SIZE = 1.sp

const val CARRIAGE_RETURN = "\r"
const val DELETE_CHAR = "\u007f"
const val TAB_CHAR = "\t"
const val ESCAPE_CHAR = "\u001b"

/**
 * Transforms character according to active Ctrl / Alt modifier keys into ANSI/ASCII control codes.
 */
fun applyModifierToChar(
    char: Char,
    modifierState: ModifierKeyState,
    kittyKeyboardFlags: Int = 0,
): String = when {
    kittyKeyboardFlags != 0 -> {
        var mods = 0
        if (modifierState.ctrlPressed) mods = mods or KittyKeyboardProtocol.MODIFIER_CTRL
        if (modifierState.altPressed) mods = mods or KittyKeyboardProtocol.MODIFIER_ALT
        val cp = when (char) {
            '\r', '\n' -> 13
            '\t' -> 9
            '\u007f', '\b' -> 127
            else -> char.lowercaseChar().code
        }
        val shifted = if (char.isUpperCase()) char.code else null
        KittyKeyboardProtocol.encodeKeyEvent(
            codepoint = cp,
            shiftedCodepoint = shifted,
            modifiers = mods,
            eventType = CoreKeyEventType.PRESS,
            flags = kittyKeyboardFlags,
        )
    }
    modifierState.ctrlPressed -> {
        when (char.lowercaseChar()) {
            'a' -> "\u0001"
            'b' -> "\u0002"
            'c' -> "\u0003"
            'd' -> "\u0004"
            'e' -> "\u0005"
            'f' -> "\u0006"
            'g' -> "\u0007"
            'h' -> "\u0008"
            'i' -> "\u0009"
            'j' -> "\u000A"
            'k' -> "\u000B"
            'l' -> "\u000C"
            'm' -> "\u000D"
            'n' -> "\u000E"
            'o' -> "\u000F"
            'p' -> "\u0010"
            'q' -> "\u0011"
            'r' -> "\u0012"
            's' -> "\u0013"
            't' -> "\u0014"
            'u' -> "\u0015"
            'v' -> "\u0016"
            'w' -> "\u0017"
            'x' -> "\u0018"
            'y' -> "\u0019"
            'z' -> "\u001A"
            '[' -> "\u001B"
            '\\' -> "\u001C"
            ']' -> "\u001D"
            '^' -> "\u001E"
            '_' -> "\u001F"
            '\r', '\n' -> "\u000A"
            else -> char.toString()
        }
    }
    modifierState.altPressed -> {
        when (char) {
            '\r', '\n' -> "$ESCAPE_CHAR\r"
            else -> "$ESCAPE_CHAR$char"
        }
    }
    else -> char.toString()
}

/**
 * Computes the xterm modifier parameter (`1 + shift(1) + alt(2) + ctrl(4)`).
 */
fun computeXtermModifierParam(
    isShift: Boolean,
    isAlt: Boolean,
    isCtrl: Boolean,
): Int =
    1 +
        (if (isShift) 1 else 0) +
        (if (isAlt) 2 else 0) +
        (if (isCtrl) 4 else 0)

private fun isIsoControlCodePoint(codePoint: Int): Boolean =
    codePoint in 0x00..0x1F || codePoint in 0x7F..0x9F

/**
 * Returns true when Right Alt acts as AltGr to compose a non-control character distinct from its unmodified glyph.
 */
internal fun isAltGrComposedChar(
    isRightAltOnly: Boolean,
    unicodeChar: Int,
    unmodifiedAltUnicodeChar: Int,
): Boolean =
    isRightAltOnly &&
        unicodeChar > 0 &&
        !isIsoControlCodePoint(unicodeChar) &&
        unicodeChar != unmodifiedAltUnicodeChar

/**
 * Determines whether the fallback unicode character from a hardware KeyDown event should be emitted to PTY input.
 */
internal fun shouldEmitFallbackUnicodeChar(
    isCtrl: Boolean,
    isMeta: Boolean,
    isAlt: Boolean,
    isAltGrComposed: Boolean,
    unicodeChar: Int,
): Boolean =
    !isCtrl &&
        !isMeta &&
        (!isAlt || isAltGrComposed) &&
        unicodeChar > 0 &&
        !isIsoControlCodePoint(unicodeChar)

/**
 * Encodes an arrow key as an xterm CSI sequence when Ctrl or Alt is pressed,
 * or returns `null` for plain/Shift-only arrow keys so callers preserve DECCKM handling.
 */
fun encodeModifiedArrowKey(
    direction: ArrowDirection,
    isShift: Boolean,
    isAlt: Boolean,
    isCtrl: Boolean,
): String? {
    if (!isCtrl && !isAlt) return null
    val mod = computeXtermModifierParam(isShift = isShift, isAlt = isAlt, isCtrl = isCtrl)
    val letter = when (direction) {
        ArrowDirection.UP -> 'A'
        ArrowDirection.DOWN -> 'B'
        ArrowDirection.RIGHT -> 'C'
        ArrowDirection.LEFT -> 'D'
    }
    return "\u001B[1;$mod$letter"
}

/**
 * Encodes a special navigation or function key with an xterm modifier parameter.
 */
fun encodeModifiedSpecialKey(
    baseEscapeCode: String,
    isTildeSequence: Boolean,
    modifierParam: Int,
): String = when {
    modifierParam > 1 && isTildeSequence -> "\u001B[$baseEscapeCode;$modifierParam~"
    modifierParam > 1 -> "\u001B[1;$modifierParam$baseEscapeCode"
    isTildeSequence -> "\u001B[$baseEscapeCode~"
    baseEscapeCode == "P" || baseEscapeCode == "Q" || baseEscapeCode == "R" || baseEscapeCode == "S" ->
        "\u001BO$baseEscapeCode"
    else -> "\u001B[$baseEscapeCode"
}

private fun composeLetterIndex(key: Key): Int = when (key) {
    Key.A -> 0
    Key.B -> 1
    Key.C -> 2
    Key.D -> 3
    Key.E -> 4
    Key.F -> 5
    Key.G -> 6
    Key.H -> 7
    Key.I -> 8
    Key.J -> 9
    Key.K -> 10
    Key.L -> 11
    Key.M -> 12
    Key.N -> 13
    Key.O -> 14
    Key.P -> 15
    Key.Q -> 16
    Key.R -> 17
    Key.S -> 18
    Key.T -> 19
    Key.U -> 20
    Key.V -> 21
    Key.W -> 22
    Key.X -> 23
    Key.Y -> 24
    Key.Z -> 25
    else -> -1
}

private fun composeDigitChar(key: Key): Char? = when (key) {
    Key.Zero -> '0'
    Key.One -> '1'
    Key.Two -> '2'
    Key.Three -> '3'
    Key.Four -> '4'
    Key.Five -> '5'
    Key.Six -> '6'
    Key.Seven -> '7'
    Key.Eight -> '8'
    Key.Nine -> '9'
    else -> null
}

/**
 * Processes hardware key combinations (Ctrl/Alt modifiers, Function keys, navigation) into VT escape sequences.
 */
fun handleHardwareKeyEvent(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    kittyKeyboardFlags: Int = 0,
): Boolean =
    processHardwareKey(
        key = keyEvent.key,
        isShift = keyEvent.isShiftPressed,
        isAlt = keyEvent.isAltPressed,
        isCtrl = keyEvent.isCtrlPressed,
        onInput = onInput,
        onArrowKey = onArrowKey,
        kittyKeyboardFlags = kittyKeyboardFlags,
        isMeta = keyEvent.isMetaPressed,
    )

internal fun processHardwareKey(
    key: Key,
    isShift: Boolean,
    isAlt: Boolean,
    isCtrl: Boolean,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    kittyKeyboardFlags: Int = 0,
    isMeta: Boolean = false,
): Boolean {
    if (isMeta) return false
    val mod = computeXtermModifierParam(isShift = isShift, isAlt = isAlt, isCtrl = isCtrl)
    val letterOffset = composeLetterIndex(key)

    if (kittyKeyboardFlags != 0) {
        var mods = 0
        if (isShift) mods = mods or KittyKeyboardProtocol.MODIFIER_SHIFT
        if (isAlt) mods = mods or KittyKeyboardProtocol.MODIFIER_ALT
        if (isCtrl) mods = mods or KittyKeyboardProtocol.MODIFIER_CTRL

        val (cp, shiftedCp) = when {
            letterOffset >= 0 && (isCtrl || isAlt) -> {
                val base = 'a'.code + letterOffset
                val shifted = if (isShift) 'A'.code + letterOffset else null
                base to shifted
            }
            key == Key.Enter || key == Key.NumPadEnter -> 13 to null
            key == Key.Tab -> 9 to null
            key == Key.Backspace -> 127 to null
            key == Key.Escape -> 27 to null
            else -> null to null
        }

        if (cp != null) {
            val seq = KittyKeyboardProtocol.encodeKeyEvent(
                codepoint = cp,
                shiftedCodepoint = shiftedCp,
                modifiers = mods,
                eventType = CoreKeyEventType.PRESS,
                flags = kittyKeyboardFlags,
            )
            if (seq.isNotEmpty()) {
                onInput(seq)
                return true
            }
        }
    }

    if (kittyKeyboardFlags == 0 && isCtrl && !isAlt) {
        if (letterOffset >= 0) {
            if (isShift) return false
            onInput((1 + letterOffset).toChar().toString())
            return true
        }
        if ((key == Key.Spacebar || key == Key.Two) && !isShift) {
            onInput("\u0000")
            return true
        }

        when (key) {
            Key.LeftBracket -> {
                onInput("\u001B")
                return true
            }
            Key.Backslash -> {
                onInput("\u001C")
                return true
            }
            Key.RightBracket -> {
                onInput("\u001D")
                return true
            }
            Key.Six -> {
                onInput("\u001E")
                return true
            }
            Key.Minus -> {
                onInput("\u001F")
                return true
            }
            Key.Backspace -> {
                onInput("\u0017")
                return true
            }
            else -> {}
        }
    }

    if (kittyKeyboardFlags == 0 && isAlt && !isCtrl) {
        if (letterOffset >= 0) {
            val base = ('a'.code + letterOffset).toChar()
            val ch = if (isShift) base.uppercaseChar() else base
            onInput("\u001B$ch")
            return true
        }
        val digit = composeDigitChar(key)
        if (digit != null) {
            onInput("\u001B$digit")
            return true
        }

        when (key) {
            Key.Period -> {
                onInput("\u001B.")
                return true
            }
            Key.Backspace -> {
                onInput("\u001B\u007f")
                return true
            }
            else -> {}
        }
    }

    val arrowDirection = when (key) {
        Key.DirectionUp -> ArrowDirection.UP
        Key.DirectionDown -> ArrowDirection.DOWN
        Key.DirectionLeft -> ArrowDirection.LEFT
        Key.DirectionRight -> ArrowDirection.RIGHT
        else -> null
    }
    if (arrowDirection != null) {
        val modified = encodeModifiedArrowKey(
            direction = arrowDirection,
            isShift = isShift,
            isAlt = isAlt,
            isCtrl = isCtrl,
        )
        if (modified != null) {
            onInput(modified)
        } else {
            onArrowKey(arrowDirection, isShift)
        }
        return true
    }

    val specialKey = when (key) {
        Key.F1 -> "P" to false
        Key.F2 -> "Q" to false
        Key.F3 -> "R" to false
        Key.F4 -> "S" to false
        Key.F5 -> "15" to true
        Key.F6 -> "17" to true
        Key.F7 -> "18" to true
        Key.F8 -> "19" to true
        Key.F9 -> "20" to true
        Key.F10 -> "21" to true
        Key.F11 -> "23" to true
        Key.F12 -> "24" to true
        Key.Delete -> "3" to true
        Key.Insert -> "2" to true
        Key.PageUp -> "5" to true
        Key.PageDown -> "6" to true
        Key.MoveHome -> "H" to false
        Key.MoveEnd -> "F" to false
        else -> null
    }
    if (specialKey != null) {
        onInput(encodeModifiedSpecialKey(specialKey.first, specialKey.second, mod))
        return true
    }

    when (key) {
        Key.Enter, Key.NumPadEnter -> {
            val seq =
                when {
                    isAlt -> "\u001B\r"
                    isShift || isCtrl -> "\n"
                    else -> CARRIAGE_RETURN
                }
            onInput(seq)
            return true
        }
        Key.Backspace -> {
            onInput(DELETE_CHAR)
            return true
        }
        Key.Tab -> {
            val seq =
                when {
                    isAlt && isShift -> "\u001B\u001B[Z"
                    isShift -> "\u001B[Z"
                    isAlt -> "\u001B\t"
                    else -> TAB_CHAR
                }
            onInput(seq)
            return true
        }
        Key.Escape -> {
            onInput(ESCAPE_CHAR)
            return true
        }
        else -> return false
    }
}
