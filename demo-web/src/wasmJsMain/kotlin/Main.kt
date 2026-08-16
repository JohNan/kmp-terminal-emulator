@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlin.js.ExperimentalWasmJsInterop::class
)

package com.sshclient.demoweb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeViewport
import com.sshclient.composeapp.presentation.screens.terminal.TerminalRenderer
import com.sshclient.data.terminal.TerminalEmulator
import com.sshclient.domain.model.TerminalColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.js.JsAny

external interface TerminalConfig : JsAny {
    val containerId: String
    val rows: Int
    val cols: Int
    val onInput: (String) -> Unit
}

@JsFun("(func) => { window.createKmpTerminal = (config) => func(config); }")
external fun registerCreateKmpTerminal(func: (TerminalConfig) -> JsAny)

@JsFun("(write, resize, focus, dispose) => ({ write, resize, focus, dispose })")
external fun createTerminalInstanceJs(
    write: (String) -> Unit,
    resize: (Int, Int) -> Unit,
    focus: () -> Unit,
    dispose: () -> Unit
): JsAny

@JsFun("(width, height) => { if (window.onTerminalCellMeasured) window.onTerminalCellMeasured(width, height); }")
external fun notifyCellMeasured(width: Double, height: Double)

@JsFun("(containerId) => { const el = document.getElementById(containerId); if (el) el.innerHTML = ''; }")
external fun clearContainer(containerId: String)

@JsFun("() => { return document.getElementById('compose-receiver') !== null; }")
external fun hasDefaultContainer(): Boolean

@JsFun(
    "() => { " +
        "return {" +
        "  containerId: 'compose-receiver'," +
        "  rows: 24," +
        "  cols: 80," +
        "  onInput: (input) => { " +
        "    if (typeof window.onTerminalInput === 'function') window.onTerminalInput(input); " +
        "  }" +
        "};" +
        "}"
)
external fun createDefaultConfig(): TerminalConfig

@JsFun(
    "(instance) => { " +
        "window.writeToTerminal = (text) => instance.write(text);" +
        "window.resizeTerminal = (r, c) => instance.resize(r, c);" +
        "window.focusTerminal = () => instance.focus();" +
        "}"
)
external fun registerGlobalFunctions(instance: JsAny)

fun main() {
    val createFunc: (TerminalConfig) -> JsAny = { config ->
        val containerId = config.containerId
        val rows = config.rows
        val cols = config.cols
        val onInput = config.onInput

        val emulator = TerminalEmulator(rows, cols)
        val focusRequester = FocusRequester()
        var isFocusRequesterInitialized = false
        val scope = CoroutineScope(Dispatchers.Main)

        val isDisposedState = mutableStateOf(false)

        ComposeViewport(viewportContainerId = containerId) {
            val isDisposed by remember { isDisposedState }

            if (!isDisposed) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
                        val terminalState by emulator.screenState.collectAsState()

                        Box(modifier = Modifier.fillMaxSize()) {
                            TerminalRenderer(
                                modifier = Modifier.fillMaxSize(),
                                terminalState = terminalState,
                                onInput = { input ->
                                    if (!isDisposed) {
                                        onInput(input)
                                    }
                                },
                                onArrowKey = { _, _ -> },
                                onLog = {},
                                focusRequester = focusRequester,
                                colorScheme = TerminalColorScheme.DEFAULT,
                                fontSize = 14f,
                                onCellMeasured = { width, height ->
                                    notifyCellMeasured(width.toDouble(), height.toDouble())
                                },
                                onResize = { r, c ->
                                    if (!isDisposedState.value) {
                                        scope.launch {
                                            emulator.resize(r, c)
                                        }
                                    }
                                }
                            )
                        }

                        LaunchedEffect(Unit) {
                            isFocusRequesterInitialized = true
                            try {
                                focusRequester.requestFocus()
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }

        val write: (String) -> Unit = { text ->
            if (!isDisposedState.value) {
                scope.launch {
                    emulator.processOutput(text)
                }
            }
        }

        val resize: (Int, Int) -> Unit = { r, c ->
            if (!isDisposedState.value) {
                scope.launch {
                    emulator.resize(r, c)
                }
            }
        }

        val focus: () -> Unit = {
            if (!isDisposedState.value && isFocusRequesterInitialized) {
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        val dispose: () -> Unit = {
            isDisposedState.value = true
            scope.cancel()
            clearContainer(containerId)
        }

        createTerminalInstanceJs(write, resize, focus, dispose)
    }

    registerCreateKmpTerminal(createFunc)

    // Backward compatibility: automatically create instance if #compose-receiver exists
    if (hasDefaultContainer()) {
        val config = createDefaultConfig()
        val instance = createFunc(config)
        registerGlobalFunctions(instance)
    }
}
