@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlin.js.ExperimentalWasmJsInterop::class
)

package com.johnan.terminal.demoweb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.terminalConfig
import com.johnan.terminal.ui.TerminalCursorStyle
import com.johnan.terminal.ui.TerminalRenderer
import com.johnan.terminal.ui.terminalUiConfig
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

@JsFun(
    "(write, resize, focus, dispose, setTheme, setCursorStyle, setFontSize) => " +
        "({ write, resize, focus, dispose, setTheme, setCursorStyle, setFontSize })"
)
external fun createTerminalInstanceJs(
    write: (String) -> Unit,
    resize: (Int, Int) -> Unit,
    focus: () -> Unit,
    dispose: () -> Unit,
    setTheme: (String) -> Unit,
    setCursorStyle: (String) -> Unit,
    setFontSize: (Float) -> Unit
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
        "window.setTerminalTheme = (name) => instance.setTheme(name);" +
        "window.setTerminalCursorStyle = (style) => instance.setCursorStyle(style);" +
        "window.setTerminalFontSize = (size) => instance.setFontSize(size);" +
        "}"
)
external fun registerGlobalFunctions(instance: JsAny)

fun main() {
    val createFunc: (TerminalConfig) -> JsAny = { config ->
        val containerId = config.containerId
        val rows = config.rows
        val cols = config.cols
        val onInput = config.onInput

        val emulator = TerminalEmulator(
            config = terminalConfig {
                initialRows = rows
                initialCols = cols
            }
        )
        val focusRequester = FocusRequester()
        var isFocusRequesterInitialized = false
        val scope = CoroutineScope(Dispatchers.Main)

        val isDisposedState = mutableStateOf(false)
        val currentColorSchemeState = mutableStateOf(TerminalColorScheme.DEFAULT)
        val currentCursorStyleState = mutableStateOf(TerminalCursorStyle.BLOCK)
        val currentFontSizeState = mutableFloatStateOf(14f)

        ComposeViewport(viewportContainerId = containerId) {
            val isDisposed by remember { isDisposedState }
            val colorScheme by remember { currentColorSchemeState }
            val cursorStyle by remember { currentCursorStyleState }
            val fontSize by remember { currentFontSizeState }

            if (!isDisposed) {
                val uiConfig = remember(colorScheme, cursorStyle, fontSize) {
                    terminalUiConfig {
                        this.colorScheme = colorScheme
                        typography {
                            this.fontSize = fontSize.sp
                        }
                        cursor {
                            this.style = cursorStyle
                            this.blink = true
                        }
                        gestures {
                            enableTouchToFocus = true
                            enableSelection = true
                            wheelScrollMultiplier = 1.2f
                        }
                    }
                }

                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
                        val terminalState by emulator.screenState.collectAsState()

                        Box(modifier = Modifier.fillMaxSize()) {
                            TerminalRenderer(
                                modifier = Modifier.fillMaxSize(),
                                terminalState = terminalState,
                                config = uiConfig,
                                onInput = { input ->
                                    if (!isDisposed) {
                                        onInput(input)
                                    }
                                },
                                onArrowKey = { _, _ -> },
                                onLog = {},
                                focusRequester = focusRequester,
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

        val setTheme: (String) -> Unit = { name ->
            val match = TerminalColorScheme.getById(name.lowercase())
                ?: TerminalColorScheme.PRESETS.find { it.name.equals(name, ignoreCase = true) }
                ?: TerminalColorScheme.DEFAULT
            currentColorSchemeState.value = match
        }

        val setCursorStyle: (String) -> Unit = { styleName ->
            val match = when (styleName.uppercase()) {
                "BLOCK" -> TerminalCursorStyle.BLOCK
                "BEAM", "BAR" -> TerminalCursorStyle.BEAM
                "UNDERLINE", "LINE" -> TerminalCursorStyle.UNDERLINE
                else -> TerminalCursorStyle.BLOCK
            }
            currentCursorStyleState.value = match
        }

        val setFontSize: (Float) -> Unit = { size ->
            currentFontSizeState.floatValue = size.coerceIn(10f, 28f)
        }

        createTerminalInstanceJs(write, resize, focus, dispose, setTheme, setCursorStyle, setFontSize)
    }

    registerCreateKmpTerminal(createFunc)

    // Backward compatibility: automatically create instance if #compose-receiver exists
    if (hasDefaultContainer()) {
        val config = createDefaultConfig()
        val instance = createFunc(config)
        registerGlobalFunctions(instance)
    }
}
