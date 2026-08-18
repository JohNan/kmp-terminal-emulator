package com.johnan.terminal.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.ArrowDirection

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
): String {
    return when {
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
                else -> char.toString()
            }
        }
        modifierState.altPressed -> "$ESCAPE_CHAR$char"
        else -> char.toString()
    }
}

/**
 * Processes hardware key combinations (Ctrl/Alt modifiers, Function keys, navigation) into VT escape sequences.
 */
fun handleHardwareKeyEvent(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
): Boolean {
    val key = keyEvent.key
    val isCtrl = keyEvent.isCtrlPressed
    val isAlt = keyEvent.isAltPressed
    val isShift = keyEvent.isShiftPressed

    if (isCtrl && !isAlt) {
        when (key) {
            Key.A -> {
                onInput("\u0001")
                return true
            }
            Key.B -> {
                onInput("\u0002")
                return true
            }
            Key.C -> {
                onInput("\u0003")
                return true
            }
            Key.D -> {
                onInput("\u0004")
                return true
            }
            Key.E -> {
                onInput("\u0005")
                return true
            }
            Key.F -> {
                onInput("\u0006")
                return true
            }
            Key.G -> {
                onInput("\u0007")
                return true
            }
            Key.H -> {
                onInput("\u0008")
                return true
            }
            Key.I -> {
                onInput("\u0009")
                return true
            }
            Key.J -> {
                onInput("\u000A")
                return true
            }
            Key.K -> {
                onInput("\u000B")
                return true
            }
            Key.L -> {
                onInput("\u000C")
                return true
            }
            Key.M -> {
                onInput("\u000D")
                return true
            }
            Key.N -> {
                onInput("\u000E")
                return true
            }
            Key.O -> {
                onInput("\u000F")
                return true
            }
            Key.P -> {
                onInput("\u0010")
                return true
            }
            Key.Q -> {
                onInput("\u0011")
                return true
            }
            Key.R -> {
                onInput("\u0012")
                return true
            }
            Key.S -> {
                onInput("\u0013")
                return true
            }
            Key.T -> {
                onInput("\u0014")
                return true
            }
            Key.U -> {
                onInput("\u0015")
                return true
            }
            Key.V -> {
                onInput("\u0016")
                return true
            }
            Key.W -> {
                onInput("\u0017")
                return true
            }
            Key.X -> {
                onInput("\u0018")
                return true
            }
            Key.Y -> {
                onInput("\u0019")
                return true
            }
            Key.Z -> {
                onInput("\u001A")
                return true
            }
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
            else -> return false
        }
    }

    if (isAlt && !isCtrl) {
        when (key) {
            Key.A -> {
                onInput("\u001Ba")
                return true
            }
            Key.B -> {
                onInput("\u001Bb")
                return true
            }
            Key.C -> {
                onInput("\u001Bc")
                return true
            }
            Key.D -> {
                onInput("\u001Bd")
                return true
            }
            Key.F -> {
                onInput("\u001Bf")
                return true
            }
            Key.L -> {
                onInput("\u001Bl")
                return true
            }
            Key.T -> {
                onInput("\u001Bt")
                return true
            }
            Key.U -> {
                onInput("\u001Bu")
                return true
            }
            Key.Period -> {
                onInput("\u001B.")
                return true
            }
            Key.Backspace -> {
                onInput("\u001B\u007f")
                return true
            }
            else -> return false
        }
    }

    when (key) {
        Key.F1 -> {
            onInput("\u001BOP")
            return true
        }
        Key.F2 -> {
            onInput("\u001BOQ")
            return true
        }
        Key.F3 -> {
            onInput("\u001BOR")
            return true
        }
        Key.F4 -> {
            onInput("\u001BOS")
            return true
        }
        Key.F5 -> {
            onInput("\u001B[15~")
            return true
        }
        Key.F6 -> {
            onInput("\u001B[17~")
            return true
        }
        Key.F7 -> {
            onInput("\u001B[18~")
            return true
        }
        Key.F8 -> {
            onInput("\u001B[19~")
            return true
        }
        Key.F9 -> {
            onInput("\u001B[20~")
            return true
        }
        Key.F10 -> {
            onInput("\u001B[21~")
            return true
        }
        Key.F11 -> {
            onInput("\u001B[23~")
            return true
        }
        Key.F12 -> {
            onInput("\u001B[24~")
            return true
        }
        else -> {}
    }

    when (key) {
        Key.DirectionUp -> {
            onArrowKey(ArrowDirection.UP, isShift)
            return true
        }
        Key.DirectionDown -> {
            onArrowKey(ArrowDirection.DOWN, isShift)
            return true
        }
        Key.DirectionLeft -> {
            onArrowKey(ArrowDirection.LEFT, isShift)
            return true
        }
        Key.DirectionRight -> {
            onArrowKey(ArrowDirection.RIGHT, isShift)
            return true
        }
        else -> {}
    }

    when (key) {
        Key.Enter -> {
            onInput(CARRIAGE_RETURN)
            return true
        }
        Key.Backspace -> {
            onInput(DELETE_CHAR)
            return true
        }
        Key.Tab -> {
            onInput(TAB_CHAR)
            return true
        }
        Key.Escape -> {
            onInput(ESCAPE_CHAR)
            return true
        }
        Key.Delete -> {
            onInput("\u001B[3~")
            return true
        }
        Key.Insert -> {
            onInput("\u001B[2~")
            return true
        }
        Key.PageUp -> {
            onInput("\u001B[5~")
            return true
        }
        Key.PageDown -> {
            onInput("\u001B[6~")
            return true
        }
        Key.MoveHome -> {
            onInput("\u001B[H")
            return true
        }
        Key.MoveEnd -> {
            onInput("\u001B[F")
            return true
        }
        else -> return false
    }
}
