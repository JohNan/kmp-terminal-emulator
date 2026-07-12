import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.sshclient.composeapp.presentation.screens.terminal.TerminalRenderer
import com.sshclient.data.terminal.TerminalEmulator
import com.sshclient.domain.model.TerminalColorScheme
import kotlinx.coroutines.launch

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "compose-receiver") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
                TerminalDemoScreen()
            }
        }
    }
}

@Composable
fun TerminalDemoScreen() {
    val terminalCols = 80
    val terminalRows = 24

    // Initialize emulator
    val emulator = remember { TerminalEmulator(terminalRows, terminalCols) }
    val scope = rememberCoroutineScope()

    // Command buffer for local REPL shell
    val commandBuffer = remember { StringBuilder() }

    val terminalState by emulator.screenState.collectAsState()

    // Helper to print text to the emulator
    val printToTerminal: (String) -> Unit = remember(emulator, scope) {
        { text ->
            scope.launch {
                emulator.processOutput(text)
            }
        }
    }

    // Print welcome instructions
    LaunchedEffect(Unit) {
        printToTerminal("\u001B[1;36mKMP Wasm Terminal Proof of Concept\u001B[0m\r\n")
        printToTerminal(
            "Type commands here. Try typing " +
                "'\u001B[33mhelp\u001B[0m', '\u001B[33mping\u001B[0m', or '\u001B[33mcolor\u001B[0m'.\r\n\r\n"
        )
        printToTerminal("kmp-wasm-shell$ ")
    }

    val focusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TerminalRenderer(
            modifier = Modifier.fillMaxSize(),
            terminalState = terminalState,
            onInput = { input ->
                for (char in input) {
                    when (char) {
                        '\r', '\n' -> {
                            val cmd = commandBuffer.toString().trim()
                            commandBuffer.setLength(0)
                            printToTerminal("\r\n")
                            when (cmd) {
                                "help" -> {
                                    printToTerminal("Available commands:\r\n")
                                    printToTerminal("  help  - Show this help message\r\n")
                                    printToTerminal("  ping  - Respond with a pong\r\n")
                                    printToTerminal("  clear - Clear the screen\r\n")
                                    printToTerminal("  color - Print some ANSI colored text\r\n")
                                }
                                "ping" -> {
                                    printToTerminal("pong!\r\n")
                                }
                                "clear" -> {
                                    printToTerminal("\u001B[2J\u001B[H")
                                }
                                "color" -> {
                                    printToTerminal("ANSI standard colors:\r\n")
                                    printToTerminal(
                                        "  \u001B[31mRed\u001B[0m  \u001B[32mGreen\u001B[0m  " +
                                            "\u001B[33mYellow\u001B[0m  \u001B[34mBlue\u001B[0m  " +
                                            "\u001B[35mMagenta\u001B[0m  \u001B[36mCyan\u001B[0m\r\n"
                                    )
                                }
                                "" -> {}
                                else -> {
                                    printToTerminal("Unknown command: $cmd\r\n")
                                }
                            }
                            printToTerminal("kmp-wasm-shell$ ")
                        }
                        '\u007f', '\b' -> {
                            if (commandBuffer.isNotEmpty()) {
                                commandBuffer.deleteAt(commandBuffer.length - 1)
                                printToTerminal("\b \b") // send backspace, space, backspace to erase char
                            }
                        }
                        else -> {
                            if (char.code in 32..126) {
                                commandBuffer.append(char)
                                printToTerminal(char.toString())
                            }
                        }
                    }
                }
            },
            onArrowKey = { arrowDirection, _ ->
                printToTerminal(
                    "\r\n[Arrow key pressed: $arrowDirection]\r\nkmp-wasm-shell$ " + commandBuffer.toString()
                )
            },
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
