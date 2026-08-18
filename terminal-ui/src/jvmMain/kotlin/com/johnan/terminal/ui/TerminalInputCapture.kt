package com.johnan.terminal.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.johnan.terminal.core.ArrowDirection

@Composable
actual fun TerminalInputCapture(
    enabled: Boolean,
    focusRequester: FocusRequester,
    showKeyboardSignal: Int,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    onLog: (String) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(showKeyboardSignal) {
        if (showKeyboardSignal > 0) {
            focusRequester.requestFocus()
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val newText = newValue.text
            val oldText = textFieldValue.text
            if (newText.length > oldText.length) {
                val added = newText.substring(oldText.length)
                onInput(added)
            } else if (newText.length < oldText.length) {
                onInput("\u007f")
            }
            textFieldValue = TextFieldValue("")
        },
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.None,
            ),
        keyboardActions =
            KeyboardActions(
                onAny = {},
            ),
        textStyle = TextStyle(color = Color.Transparent),
        modifier =
            modifier
                .size(1.dp)
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
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
                },
    )
}
