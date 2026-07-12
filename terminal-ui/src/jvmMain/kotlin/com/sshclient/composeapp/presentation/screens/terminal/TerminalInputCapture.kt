package com.sshclient.composeapp.presentation.screens.terminal

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import com.sshclient.presentation.screens.terminal.ArrowDirection

@Composable
actual fun TerminalInputCapture(
    enabled: Boolean,
    focusRequester: FocusRequester,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    onLog: (String) -> Unit,
    modifier: Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val newText = newValue.text
            val oldText = textFieldValue.text
            if (newText.length > oldText.length) {
                // Characters added
                val added = newText.substring(oldText.length)
                onInput(added)
            } else if (newText.length < oldText.length) {
                // Backspace pressed
                onInput("\u007f") // send DEL / backspace character
            }
            // Always keep the text value empty to continuously capture typing
            textFieldValue = TextFieldValue("")
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.None
        ),
        keyboardActions = KeyboardActions(
            onAny = {}
        ),
        textStyle = TextStyle(color = Color.Transparent), // Keep it invisible
        modifier = modifier
            .size(1.dp) // Make it tiny so it doesn't take layout space
            .focusRequester(focusRequester)
            .onKeyEvent { keyEvent ->
                // Capture hardware keyboard keys (like arrow keys) if any
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onArrowKey(ArrowDirection.LEFT, keyEvent.isShiftPressed)
                            true
                        }
                        Key.DirectionRight -> {
                            onArrowKey(ArrowDirection.RIGHT, keyEvent.isShiftPressed)
                            true
                        }
                        Key.DirectionUp -> {
                            onArrowKey(ArrowDirection.UP, keyEvent.isShiftPressed)
                            true
                        }
                        Key.DirectionDown -> {
                            onArrowKey(ArrowDirection.DOWN, keyEvent.isShiftPressed)
                            true
                        }
                        Key.Enter -> {
                            onInput("\n")
                            true
                        }
                        Key.Backspace -> {
                            onInput("\u007f")
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    )
}
