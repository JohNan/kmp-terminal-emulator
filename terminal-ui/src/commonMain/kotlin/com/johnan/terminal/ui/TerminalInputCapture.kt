package com.johnan.terminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.johnan.terminal.core.ArrowDirection

/**
 * Platform-specific input interceptor capturing software keyboard IME commits and hardware key events.
 */
@Composable
expect fun TerminalInputCapture(
    enabled: Boolean,
    focusRequester: FocusRequester,
    showKeyboardSignal: Int = 0,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    onLog: (String) -> Unit,
    modifier: Modifier = Modifier,
)
