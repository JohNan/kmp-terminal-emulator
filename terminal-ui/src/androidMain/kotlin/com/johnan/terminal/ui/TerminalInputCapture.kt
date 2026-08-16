package com.johnan.terminal.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
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
    var inputView by remember { mutableStateOf<TerminalInputView?>(null) }

    LaunchedEffect(showKeyboardSignal) {
        if (showKeyboardSignal > 0) {
            focusRequester.requestFocus()
            inputView?.showKeyboard()
        }
    }

    AndroidView(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Terminal input field" },
        factory = { context ->
            TerminalInputView(context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }.also { inputView = it }
        },
        update = { view ->
            inputView = view
            view.onInput = onInput
            view.onArrowKey = onArrowKey
            view.onLog = onLog

            if (view.isFocusable && !view.isFocused && enabled) {
                view.requestFocus()
            }
        },
    )
}
