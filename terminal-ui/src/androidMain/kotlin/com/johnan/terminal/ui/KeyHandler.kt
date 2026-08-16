package com.johnan.terminal.ui

import android.view.KeyEvent
import com.johnan.terminal.core.ArrowDirection

/**
 * Handles hardware keyboard events with native Android KeyEvents to minimize object allocations.
 * This intercepts modifier combinations (Ctrl, Alt) and maps them to ANSI control characters:
 * - Ctrl + [A-Z] -> \u0001 to \u001A
 * - Alt + [A-Z] -> \u001B + character
 * Also maps hardware D-pad/Arrow keys and special keys (Tab, Esc, Backspace, Delete, Enter).
 */
fun handleAndroidHardwareKeyEvent(
    event: KeyEvent,
    onInput: ((String) -> Unit)?,
    onArrowKey: ((ArrowDirection, Boolean) -> Unit)?
): Boolean {
    // Only handle KEY DOWN events
    if (event.action != KeyEvent.ACTION_DOWN) return false

    val keyCode = event.keyCode
    val isCtrl = event.isCtrlPressed
    val isAlt = event.isAltPressed
    val isShift = event.isShiftPressed

    // Handle Arrow Keys
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

    // Handle Ctrl+Key combinations
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
            } // ESC
            KeyEvent.KEYCODE_BACKSLASH -> {
                onInput.invoke("\u001C")
                return true
            } // FS
            KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                onInput.invoke("\u001D")
                return true
            } // GS
            KeyEvent.KEYCODE_6 -> {
                onInput.invoke("\u001E")
                return true
            } // RS (Ctrl+6)
            KeyEvent.KEYCODE_MINUS -> {
                onInput.invoke("\u001F")
                return true
            } // US (Ctrl+-)
            KeyEvent.KEYCODE_DEL -> {
                onInput.invoke("\u0017")
                return true
            } // Ctrl+Backspace -> Ctrl+W
        }
    }

    // Handle Alt+Key combinations (ESC prefix method)
    if (isAlt && !isCtrl) {
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            // Using lowercase for standard Alt combinations
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
            } // Alt+Backspace
        }
    }

    // Function keys F1-F12
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

    // Handle special keys without modifiers (or ignoring modifiers where appropriate)
    when (keyCode) {
        KeyEvent.KEYCODE_ENTER -> {
            onInput.invoke("\r")
            return true
        } // \r is CARRIAGE_RETURN
        KeyEvent.KEYCODE_DEL -> {
            onInput.invoke("\u007F")
            return true
        } // \u007F is DELETE_CHAR
        KeyEvent.KEYCODE_TAB -> {
            onInput.invoke("\t")
            return true
        } // \t is TAB_CHAR
        KeyEvent.KEYCODE_ESCAPE -> {
            onInput.invoke("\u001B")
            return true
        } // \u001B is ESCAPE_CHAR
        KeyEvent.KEYCODE_FORWARD_DEL -> {
            onInput.invoke("\u001B[3~")
            return true
        } // Delete key
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
