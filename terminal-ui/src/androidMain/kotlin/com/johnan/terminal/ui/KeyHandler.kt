package com.johnan.terminal.ui

import android.view.KeyEvent
import com.johnan.terminal.core.ArrowDirection

/**
 * Handles Android native hardware [KeyEvent] actions for modifier chords, function keys, and D-pad arrows.
 */
fun handleAndroidHardwareKeyEvent(
    event: KeyEvent,
    onInput: ((String) -> Unit)?,
    onArrowKey: ((ArrowDirection, Boolean) -> Unit)?,
): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false

    val keyCode = event.keyCode
    val isCtrl = event.isCtrlPressed
    val isAlt = event.isAltPressed
    val isShift = event.isShiftPressed

    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> {
            onArrowKey?.invoke(ArrowDirection.UP, isShift)
            return true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            onArrowKey?.invoke(ArrowDirection.DOWN, isShift)
            return true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            onArrowKey?.invoke(ArrowDirection.LEFT, isShift)
            return true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            onArrowKey?.invoke(ArrowDirection.RIGHT, isShift)
            return true
        }
    }

    if (onInput == null) return false

    if (isCtrl && !isAlt) {
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val charCode = keyCode - KeyEvent.KEYCODE_A + 1
            onInput.invoke(charCode.toChar().toString())
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

    if (isAlt && !isCtrl) {
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val char = (keyCode - KeyEvent.KEYCODE_A + 'a'.code).toChar()
            onInput.invoke("\u001B$char")
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

    when (keyCode) {
        KeyEvent.KEYCODE_F1 -> {
            onInput.invoke("\u001BOP")
            return true
        }
        KeyEvent.KEYCODE_F2 -> {
            onInput.invoke("\u001BOQ")
            return true
        }
        KeyEvent.KEYCODE_F3 -> {
            onInput.invoke("\u001BOR")
            return true
        }
        KeyEvent.KEYCODE_F4 -> {
            onInput.invoke("\u001BOS")
            return true
        }
        KeyEvent.KEYCODE_F5 -> {
            onInput.invoke("\u001B[15~")
            return true
        }
        KeyEvent.KEYCODE_F6 -> {
            onInput.invoke("\u001B[17~")
            return true
        }
        KeyEvent.KEYCODE_F7 -> {
            onInput.invoke("\u001B[18~")
            return true
        }
        KeyEvent.KEYCODE_F8 -> {
            onInput.invoke("\u001B[19~")
            return true
        }
        KeyEvent.KEYCODE_F9 -> {
            onInput.invoke("\u001B[20~")
            return true
        }
        KeyEvent.KEYCODE_F10 -> {
            onInput.invoke("\u001B[21~")
            return true
        }
        KeyEvent.KEYCODE_F11 -> {
            onInput.invoke("\u001B[23~")
            return true
        }
        KeyEvent.KEYCODE_F12 -> {
            onInput.invoke("\u001B[24~")
            return true
        }
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
            onInput.invoke("\t")
            return true
        }
        KeyEvent.KEYCODE_ESCAPE -> {
            onInput.invoke("\u001B")
            return true
        }
        KeyEvent.KEYCODE_FORWARD_DEL -> {
            onInput.invoke("\u001B[3~")
            return true
        }
        KeyEvent.KEYCODE_INSERT -> {
            onInput.invoke("\u001B[2~")
            return true
        }
        KeyEvent.KEYCODE_PAGE_UP -> {
            onInput.invoke("\u001B[5~")
            return true
        }
        KeyEvent.KEYCODE_PAGE_DOWN -> {
            onInput.invoke("\u001B[6~")
            return true
        }
        KeyEvent.KEYCODE_MOVE_HOME -> {
            onInput.invoke("\u001B[H")
            return true
        }
        KeyEvent.KEYCODE_MOVE_END -> {
            onInput.invoke("\u001B[F")
            return true
        }
    }

    return false
}
