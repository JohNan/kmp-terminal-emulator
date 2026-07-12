package com.sshclient.composeapp.presentation.screens.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.sshclient.presentation.screens.terminal.ArrowDirection
import androidx.compose.foundation.layout.fillMaxSize

@Composable
actual fun TerminalInputCapture(
    enabled: Boolean,
    focusRequester: FocusRequester,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    onLog: (String) -> Unit,
    modifier: Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "Terminal input field" },
        factory = { context ->
            TerminalInputView(context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
        },
        update = { view ->
            view.onInput = onInput
            view.onArrowKey = onArrowKey
            view.onLog = onLog

            if (view.isFocusable && !view.isFocused && enabled) {
                view.requestFocus()
            }
        }
    )
}
