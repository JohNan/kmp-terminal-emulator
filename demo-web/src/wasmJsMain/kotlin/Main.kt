@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlin.js.ExperimentalWasmJsInterop::class
)

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.sshclient.composeapp.presentation.screens.terminal.TerminalRenderer
import com.sshclient.data.terminal.TerminalEmulator
import com.sshclient.domain.model.TerminalColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@JsFun("(func) => { window.writeToTerminal = func; }")
external fun registerWriteToTerminal(func: (String) -> Unit)

@JsFun("(func) => { window.resizeTerminal = (r, c) => func(r, c); }")
external fun registerResizeTerminal(func: (Int, Int) -> Unit)

@JsFun("(func) => { window.focusTerminal = func; }")
external fun registerFocusTerminal(func: () -> Unit)

@JsFun("(text) => { if (typeof window.onTerminalInput === 'function') { window.onTerminalInput(text); } }")
external fun triggerTerminalInput(text: String)

fun main() {
    val emulator = TerminalEmulator(24, 80)
    val focusRequester = FocusRequester()
    val scope = CoroutineScope(Dispatchers.Main)

    // Register JS bridge functions
    registerWriteToTerminal { text ->
        scope.launch {
            emulator.processOutput(text)
        }
    }

    registerResizeTerminal { r, c ->
        scope.launch {
            emulator.resize(r, c)
        }
    }

    registerFocusTerminal {
        focusRequester.requestFocus()
    }

    // Initialize the viewport on the receiver element
    ComposeViewport(viewportContainerId = "compose-receiver") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
                val terminalState by emulator.screenState.collectAsState()

                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    TerminalRenderer(
                        modifier = Modifier.fillMaxSize(),
                        terminalState = terminalState,
                        onInput = { input ->
                            // Send input back to JavaScript
                            triggerTerminalInput(input)
                        },
                        onArrowKey = { _, _ -> },
                        onLog = {},
                        focusRequester = focusRequester,
                        colorScheme = TerminalColorScheme.DEFAULT,
                        fontSize = 14f
                    )
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        }
    }
}
