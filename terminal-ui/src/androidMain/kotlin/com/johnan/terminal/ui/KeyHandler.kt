package com.johnan.terminal.ui

import android.view.KeyEvent
import com.johnan.terminal.core.ArrowDirection
import com.johnan.terminal.core.KittyKeyboardProtocol
import com.johnan.terminal.core.KeyEventType as CoreKeyEventType

/**
 * Returns true when this Android [KeyEvent] represents a printable glyph composed via Right Alt (AltGr).
 */
internal fun KeyEvent.isAltGrComposed(): Boolean {
    if (isCtrlPressed || isMetaPressed) return false
    val isRightAltOnly =
        (metaState and KeyEvent.META_ALT_RIGHT_ON) != 0 &&
            (metaState and KeyEvent.META_ALT_LEFT_ON) == 0
    if (!isRightAltOnly) return false
    val composed = unicodeChar
    val withoutAlt = getUnicodeChar(metaState and KeyEvent.META_ALT_MASK.inv())
    return isAltGrComposedChar(
        isRightAltOnly = true,
        unicodeChar = composed,
        unmodifiedAltUnicodeChar = withoutAlt,
    )
}

/**
 * Handles Android native hardware [KeyEvent] actions for modifier chords, function keys, and D-pad arrows.
 */
fun handleAndroidHardwareKeyEvent(
    event: KeyEvent,
    onInput: ((String) -> Unit)?,
    onArrowKey: ((ArrowDirection, Boolean) -> Unit)?,
    kittyKeyboardFlags: Int = 0,
): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    if (event.isMetaPressed) return false
    if (event.isAltGrComposed()) return false

    val keyCode = event.keyCode
    val isCtrl = event.isCtrlPressed
    val isAlt = event.isAltPressed
    val isShift = event.isShiftPressed
    val mod = computeXtermModifierParam(isShift = isShift, isAlt = isAlt, isCtrl = isCtrl)

    val arrowDirection = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> ArrowDirection.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> ArrowDirection.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> ArrowDirection.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> ArrowDirection.RIGHT
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
            if (onInput == null) return false
            onInput.invoke(modified)
        } else {
            onArrowKey?.invoke(arrowDirection, isShift)
        }
        return true
    }

    if (onInput == null) return false

    if (kittyKeyboardFlags != 0) {
        var mods = 0
        if (isShift) mods = mods or KittyKeyboardProtocol.MODIFIER_SHIFT
        if (isAlt) mods = mods or KittyKeyboardProtocol.MODIFIER_ALT
        if (isCtrl) mods = mods or KittyKeyboardProtocol.MODIFIER_CTRL

        val (cp, shiftedCp) = when {
            keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && (isCtrl || isAlt) -> {
                val offset = keyCode - KeyEvent.KEYCODE_A
                val base = 'a'.code + offset
                val shifted = if (isShift) 'A'.code + offset else null
                base to shifted
            }
            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> 13 to null
            keyCode == KeyEvent.KEYCODE_TAB -> 9 to null
            keyCode == KeyEvent.KEYCODE_DEL -> 127 to null
            keyCode == KeyEvent.KEYCODE_ESCAPE -> 27 to null
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
                onInput.invoke(seq)
                return true
            }
        }
    }

    if (kittyKeyboardFlags == 0 && isCtrl && !isAlt) {
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            if (isShift) return false
            val charCode = keyCode - KeyEvent.KEYCODE_A + 1
            onInput.invoke(charCode.toChar().toString())
            return true
        }
        if ((keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_2) && !isShift) {
            onInput.invoke("\u0000")
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_LEFT_BRACKET -> {
                onInput.invoke("\u001B")
                return true
            }
            KeyEvent.KEYCODE_BACKSLASH -> {
                onInput.invoke("\u001C")
                return true
            }
            KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                onInput.invoke("\u001D")
                return true
            }
            KeyEvent.KEYCODE_6 -> {
                onInput.invoke("\u001E")
                return true
            }
            KeyEvent.KEYCODE_MINUS -> {
                onInput.invoke("\u001F")
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                onInput.invoke("\u0017")
                return true
            }
        }
    }

    if (kittyKeyboardFlags == 0 && isAlt && !isCtrl) {
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val base = (keyCode - KeyEvent.KEYCODE_A + 'a'.code).toChar()
            val ch = if (isShift) base.uppercaseChar() else base
            onInput.invoke("\u001B$ch")
            return true
        }
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val digit = (keyCode - KeyEvent.KEYCODE_0 + '0'.code).toChar()
            onInput.invoke("\u001B$digit")
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_PERIOD -> {
                onInput.invoke("\u001B.")
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                onInput.invoke("\u001B\u007F")
                return true
            }
        }
    }

    val specialKey = when (keyCode) {
        KeyEvent.KEYCODE_F1 -> "P" to false
        KeyEvent.KEYCODE_F2 -> "Q" to false
        KeyEvent.KEYCODE_F3 -> "R" to false
        KeyEvent.KEYCODE_F4 -> "S" to false
        KeyEvent.KEYCODE_F5 -> "15" to true
        KeyEvent.KEYCODE_F6 -> "17" to true
        KeyEvent.KEYCODE_F7 -> "18" to true
        KeyEvent.KEYCODE_F8 -> "19" to true
        KeyEvent.KEYCODE_F9 -> "20" to true
        KeyEvent.KEYCODE_F10 -> "21" to true
        KeyEvent.KEYCODE_F11 -> "23" to true
        KeyEvent.KEYCODE_F12 -> "24" to true
        KeyEvent.KEYCODE_FORWARD_DEL -> "3" to true
        KeyEvent.KEYCODE_INSERT -> "2" to true
        KeyEvent.KEYCODE_PAGE_UP -> "5" to true
        KeyEvent.KEYCODE_PAGE_DOWN -> "6" to true
        KeyEvent.KEYCODE_MOVE_HOME -> "H" to false
        KeyEvent.KEYCODE_MOVE_END -> "F" to false
        else -> null
    }
    if (specialKey != null) {
        onInput.invoke(encodeModifiedSpecialKey(specialKey.first, specialKey.second, mod))
        return true
    }

    when (keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            val seq =
                when {
                    isAlt -> "\u001B\r"
                    isShift || isCtrl -> "\n"
                    else -> "\r"
                }
            onInput.invoke(seq)
            return true
        }
        KeyEvent.KEYCODE_DEL -> {
            onInput.invoke("\u007F")
            return true
        }
        KeyEvent.KEYCODE_TAB -> {
            val seq =
                when {
                    isAlt && isShift -> "\u001B\u001B[Z"
                    isShift -> "\u001B[Z"
                    isAlt -> "\u001B\t"
                    else -> "\t"
                }
            onInput.invoke(seq)
            return true
        }
        KeyEvent.KEYCODE_ESCAPE -> {
            onInput.invoke("\u001B")
            return true
        }
    }

    return false
}
