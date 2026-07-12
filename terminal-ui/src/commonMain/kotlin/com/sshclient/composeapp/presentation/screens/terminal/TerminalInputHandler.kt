package com.sshclient.composeapp.presentation.screens.terminal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.sp
import com.sshclient.presentation.screens.terminal.ArrowDirection

// Input buffer configuration
const val MAX_INPUT_BUFFER_SIZE = 100 // Maximum characters to keep in input buffer
const val INPUT_BUFFER_TRIM_SIZE = 50 // Characters to keep after trimming
val HIDDEN_TEXT_SIZE = 1.sp // Tiny font size for invisible input field

// Terminal control character constants
const val CARRIAGE_RETURN = "\r"
const val DELETE_CHAR = "\u007f" // DEL character for backspace
const val TAB_CHAR = "\t"
const val ESCAPE_CHAR = "\u001b" // ESC character

/**
 * Applies modifier key transformations to a character from the soft keyboard.
 */
fun applyModifierToChar(
    char: Char,
    modifierState: ModifierKeyState,
): String {
    return when {
        // Ctrl is active - convert letters to control codes
        modifierState.ctrlPressed -> {
            when (char.lowercaseChar()) {
                'a' -> "\u0001" // Ctrl+A (SOH)
                'b' -> "\u0002" // Ctrl+B (STX)
                'c' -> "\u0003" // Ctrl+C (ETX - Interrupt)
                'd' -> "\u0004" // Ctrl+D (EOT)
                'e' -> "\u0005" // Ctrl+E (ENQ)
                'f' -> "\u0006" // Ctrl+F (ACK)
                'g' -> "\u0007" // Ctrl+G (BEL)
                'h' -> "\u0008" // Ctrl+H (BS - Backspace)
                'i' -> "\u0009" // Ctrl+I (HT - Tab)
                'j' -> "\u000A" // Ctrl+J (LF)
                'k' -> "\u000B" // Ctrl+K (VT)
                'l' -> "\u000C" // Ctrl+L (FF - Clear screen)
                'm' -> "\u000D" // Ctrl+M (CR)
                'n' -> "\u000E" // Ctrl+N (SO)
                'o' -> "\u000F" // Ctrl+O (SI)
                'p' -> "\u0010" // Ctrl+P (DLE)
                'q' -> "\u0011" // Ctrl+Q (DC1 - XON)
                'r' -> "\u0012" // Ctrl+R (DC2)
                's' -> "\u0013" // Ctrl+S (DC3 - XOFF)
                't' -> "\u0014" // Ctrl+T (DC4)
                'u' -> "\u0015" // Ctrl+U (NAK)
                'v' -> "\u0016" // Ctrl+V (SYN)
                'w' -> "\u0017" // Ctrl+W (ETB)
                'x' -> "\u0018" // Ctrl+X (CAN)
                'y' -> "\u0019" // Ctrl+Y (EM)
                'z' -> "\u001A" // Ctrl+Z (SUB - Suspend)
                '[' -> "\u001B" // Ctrl+[ (ESC)
                '\\' -> "\u001C" // Ctrl+\ (FS)
                ']' -> "\u001D" // Ctrl+] (GS)
                '^' -> "\u001E" // Ctrl+^ (RS)
                '_' -> "\u001F" // Ctrl+_ (US)
                else -> char.toString() // Pass through other characters unchanged
            }
        }
        // Alt is active - prefix with ESC
        modifierState.altPressed -> {
            "$ESCAPE_CHAR$char"
        }
        // No modifiers - pass through unchanged
        else -> char.toString()
    }
}

/**
 * Handles hardware keyboard events with modifier keys (Ctrl, Alt, Shift)
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

    // Handle Ctrl+Key combinations
    if (isCtrl && !isAlt) {
        when (key) {
            // Ctrl+A through Ctrl+Z map to ASCII 0x01-0x1A
            Key.A -> {
                onInput("\u0001")
                return true
            } // SOH - Beginning of line
            Key.B -> {
                onInput("\u0002")
                return true
            } // STX - Move back one char
            Key.C -> {
                onInput("\u0003")
                return true
            } // ETX - Interrupt (SIGINT)
            Key.D -> {
                onInput("\u0004")
                return true
            } // EOT - EOF / Logout
            Key.E -> {
                onInput("\u0005")
                return true
            } // ENQ - End of line
            Key.F -> {
                onInput("\u0006")
                return true
            } // ACK - Move forward one char
            Key.G -> {
                onInput("\u0007")
                return true
            } // BEL - Bell
            Key.H -> {
                onInput("\u0008")
                return true
            } // BS - Backspace
            Key.I -> {
                onInput("\u0009")
                return true
            } // HT - Tab
            Key.J -> {
                onInput("\u000A")
                return true
            } // LF - Line feed
            Key.K -> {
                onInput("\u000B")
                return true
            } // VT - Used by readline to kill line forward
            Key.L -> {
                onInput("\u000C")
                return true
            } // FF - Clear screen
            Key.M -> {
                onInput("\u000D")
                return true
            } // CR - Carriage return
            Key.N -> {
                onInput("\u000E")
                return true
            } // SO - Next history
            Key.O -> {
                onInput("\u000F")
                return true
            } // SI
            Key.P -> {
                onInput("\u0010")
                return true
            } // DLE - Previous history
            Key.Q -> {
                onInput("\u0011")
                return true
            } // DC1 - XON
            Key.R -> {
                onInput("\u0012")
                return true
            } // DC2 - Reverse search
            Key.S -> {
                onInput("\u0013")
                return true
            } // DC3 - XOFF
            Key.T -> {
                onInput("\u0014")
                return true
            } // DC4 - Transpose chars
            Key.U -> {
                onInput("\u0015")
                return true
            } // NAK - Used by readline to kill line backward
            Key.V -> {
                onInput("\u0016")
                return true
            } // SYN - Quoted insert
            Key.W -> {
                onInput("\u0017")
                return true
            } // ETB - Used by readline to delete word backward
            Key.X -> {
                onInput("\u0018")
                return true
            } // CAN
            Key.Y -> {
                onInput("\u0019")
                return true
            } // EM - Yank
            Key.Z -> {
                onInput("\u001A")
                return true
            } // SUB - Suspend (SIGTSTP)

            // Ctrl+[ through Ctrl+_ (special control characters)
            Key.LeftBracket -> {
                onInput("\u001B")
                return true
            } // ESC
            Key.Backslash -> {
                onInput("\u001C")
                return true
            } // FS - Quit
            Key.RightBracket -> {
                onInput("\u001D")
                return true
            } // GS
            Key.Six -> {
                onInput("\u001E")
                return true
            } // RS (Ctrl+6)
            Key.Minus -> {
                onInput("\u001F")
                return true
            } // US (Ctrl+-)

            // Ctrl+Backspace - delete word backward
            Key.Backspace -> {
                onInput("\u0017")
                return true
            } // Same as Ctrl+W

            // Let other keys fall through
            else -> return false
        }
    }

    // Handle Alt+Key combinations (ESC prefix method)
    if (isAlt && !isCtrl) {
        when (key) {
            // Alt+letter combinations for word navigation and editing
            Key.A -> {
                onInput("\u001Ba")
                return true
            }
            Key.B -> {
                onInput("\u001Bb")
                return true
            } // Move back one word
            Key.C -> {
                onInput("\u001Bc")
                return true
            } // Capitalize word
            Key.D -> {
                onInput("\u001Bd")
                return true
            } // Delete word forward
            Key.F -> {
                onInput("\u001Bf")
                return true
            } // Move forward one word
            Key.L -> {
                onInput("\u001Bl")
                return true
            } // Lowercase word
            Key.T -> {
                onInput("\u001Bt")
                return true
            } // Transpose words
            Key.U -> {
                onInput("\u001Bu")
                return true
            } // Uppercase word
            Key.Period -> {
                onInput("\u001B.")
                return true
            } // Yank last argument
            Key.Backspace -> {
                onInput("\u001B\u007f")
                return true
            } // Delete word backward

            // Let other Alt+key combinations fall through
            else -> return false
        }
    }

    // Handle Function keys F1-F12
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

    // Handle arrow keys by delegating to ViewModel
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

    // Handle special keys without modifiers
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
            onInput("\u001B[3~") // Delete key escape sequence
            return true
        }
        Key.Insert -> {
            onInput("\u001B[2~") // Insert key escape sequence
            return true
        }
        Key.PageUp -> {
            onInput("\u001B[5~") // Page Up escape sequence
            return true
        }
        Key.PageDown -> {
            onInput("\u001B[6~") // Page Down escape sequence
            return true
        }
        Key.MoveHome -> {
            onInput("\u001B[H") // Home key escape sequence
            return true
        }
        Key.MoveEnd -> {
            onInput("\u001B[F") // End key escape sequence
            return true
        }
        else -> return false
    }
}
