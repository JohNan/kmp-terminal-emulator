package com.johnan.terminal.demojvm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.terminalConfig
import com.johnan.terminal.ui.TerminalCursorStyle
import com.johnan.terminal.ui.TerminalRenderer
import com.johnan.terminal.ui.terminalUiConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KMP Terminal - JVM Desktop Demo & Capability Showcase",
        state = rememberWindowState(width = 1000.dp, height = 700.dp),
    ) {
        val emulator = remember {
            TerminalEmulator(
                config = terminalConfig {
                    initialRows = 28
                    initialCols = 90
                },
            )
        }
        val focusRequester = remember { FocusRequester() }
        val scope = rememberCoroutineScope()

        var selectedColorScheme by remember { mutableStateOf(TerminalColorScheme.DEFAULT) }
        var selectedCursorStyle by remember { mutableStateOf(TerminalCursorStyle.BLOCK) }
        var terminalFontSize by remember { mutableFloatStateOf(14f) }
        var activeJob by remember { mutableStateOf<Job?>(null) }
        var isInAltScreen by remember { mutableStateOf(false) }

        val commandBuffer = remember { StringBuilder() }
        val commandHistory = remember { mutableListOf<String>() }
        var historyIndex by remember { mutableStateOf(-1) }

        val writeToTerminal: (String) -> Unit = { text ->
            scope.launch { emulator.processOutput(text) }
        }

        fun stopActiveAnimation() {
            activeJob?.cancel()
            activeJob = null
            if (isInAltScreen) {
                writeToTerminal("\u001B[?1049l\u001B[?25h")
                isInAltScreen = false
            }
        }

        fun runColorsDemo() {
            stopActiveAnimation()
            val sb = StringBuilder()
            sb.append("\r\n\u001B[1;37m=== 1. Standard 16 ANSI Colors (Normal & Bright) ===\u001B[0m\r\n")
            sb.append("Normal: ")
            for (c in 30..37) {
                sb.append("\u001B[${c}m■ Color $c \u001B[0m")
            }
            sb.append("\r\nBright: ")
            for (c in 90..97) {
                sb.append("\u001B[${c}m■ Color $c \u001B[0m")
            }
            sb.append("\r\n\r\n")

            sb.append("\u001B[1;37m=== 2. 256 Indexed Color Palette ===\u001B[0m\r\n")
            for (r in 0 until 6) {
                for (g in 0 until 6) {
                    for (b in 0 until 6) {
                        val code = 16 + (r * 36) + (g * 6) + b
                        sb.append("\u001B[48;5;${code}m  \u001B[0m")
                    }
                    sb.append(" ")
                }
                sb.append("\r\n")
            }
            sb.append("Grayscale: ")
            for (code in 232..255) {
                sb.append("\u001B[48;5;${code}m  \u001B[0m")
            }
            sb.append("\r\n\r\n")

            sb.append("\u001B[1;37m=== 3. 24-bit TrueColor RGB Smooth Gradient ===\u001B[0m\r\n")
            for (i in 0 until 72) {
                val ratio = i.toDouble() / 72.0
                val r = (kotlin.math.sin(ratio * Math.PI) * 255).toInt().coerceIn(0, 255)
                val g = (kotlin.math.sin((ratio + 0.33) * Math.PI) * 255).toInt().coerceIn(0, 255)
                val b = (kotlin.math.sin((ratio + 0.66) * Math.PI) * 255).toInt().coerceIn(0, 255)
                sb.append("\u001B[38;2;$r;$g;${b}m█\u001B[0m")
            }
            sb.append("\r\n\r\n")
            writeToTerminal(sb.toString())
        }

        fun runStylesDemo() {
            stopActiveAnimation()
            val sb = StringBuilder()
            sb.append("\r\n\u001B[1;37m=== Text Formatting & Attributes ===\u001B[0m\r\n")
            sb.append("  \u001B[0mNormal text\u001B[0m\r\n")
            sb.append("  \u001B[1mBold text\u001B[0m\r\n")
            sb.append("  \u001B[2mDim / Faint text\u001B[0m\r\n")
            sb.append("  \u001B[3mItalic text\u001B[0m\r\n")
            sb.append("  \u001B[4mUnderlined text\u001B[0m\r\n")
            sb.append("  \u001B[21mDouble underlined text\u001B[0m\r\n")
            sb.append("  \u001B[5mBlinking text\u001B[0m\r\n")
            sb.append("  \u001B[7mInverse / Reverse video\u001B[0m\r\n")
            sb.append("  \u001B[9mCrossed out / Strikethrough\u001B[0m\r\n")
            sb.append(
                "  \u001B[1;3;4;33;44mCombined: Bold + Italic + Underline + " +
                    "Yellow on Blue\u001B[0m\r\n\r\n",
            )
            writeToTerminal(sb.toString())
        }

        fun runUnicodeDemo() {
            stopActiveAnimation()
            val sb = StringBuilder()
            sb.append("\r\n\u001B[1;37m=== Unicode, Box Drawing & Powerline Glyphs ===\u001B[0m\r\n")
            sb.append("\u001B[36m┌──────────────────────────────────────────────┐\u001B[0m\r\n")
            sb.append(
                "\u001B[36m│\u001B[0m  \u001B[1;33m⚡ KMP Multiplatform Monospace Grid\u001B[0m" +
                    "         \u001B[36m│\u001B[0m\r\n",
            )
            sb.append("\u001B[36m├──────────────────────┬───────────────────────┤\u001B[0m\r\n")
            sb.append(
                "\u001B[36m│\u001B[0m Double Lines: ╔═╦═╗  " +
                    "\u001B[36m│\u001B[0m Rounded: ╭───┬───╮     \u001B[36m│\u001B[0m\r\n",
            )
            sb.append(
                "\u001B[36m│\u001B[0m               ╠═╬═╣  " +
                    "\u001B[36m│\u001B[0m          │   │   │     \u001B[36m│\u001B[0m\r\n",
            )
            sb.append(
                "\u001B[36m│\u001B[0m               ╚═╩═╝  " +
                    "\u001B[36m│\u001B[0m          ╰───┴───╯     \u001B[36m│\u001B[0m\r\n",
            )
            sb.append("\u001B[36m├──────────────────────┴───────────────────────┤\u001B[0m\r\n")
            sb.append("\u001B[36m│\u001B[0m Blocks & Shades: ░▒▓█ ▌▐ ▄▀ ■ □ ▲ ▼ ◆ ◈       \u001B[36m│\u001B[0m\r\n")
            sb.append("\u001B[36m│\u001B[0m Braille Graph:   ⡀⣀⣄⣤⣦⣶⣷⣿                   \u001B[36m│\u001B[0m\r\n")
            sb.append("\u001B[36m│\u001B[0m Tree Hierarchy:                               \u001B[36m│\u001B[0m\r\n")
            sb.append(
                "\u001B[36m│\u001B[0m   ├── \u001B[34mterminal-core\u001B[0m " +
                    "(zero UI dependencies)   \u001B[36m│\u001B[0m\r\n",
            )
            sb.append(
                "\u001B[36m│\u001B[0m   └── \u001B[32mterminal-ui\u001B[0m   " +
                    "(Compose Multiplatform)   \u001B[36m│\u001B[0m\r\n",
            )
            sb.append(
                "\u001B[36m│\u001B[0m Powerline: \u001B[44;30m master \u001B[42;34m" +
                    "\u001B[30m ✓ clean \u001B[49;32m\u001B[0m             \u001B[36m│\u001B[0m\r\n",
            )
            sb.append("\u001B[36m└──────────────────────────────────────────────┘\u001B[0m\r\n\r\n")
            writeToTerminal(sb.toString())
        }

        fun runProgressDemo() {
            stopActiveAnimation()
            activeJob = scope.launch {
                writeToTerminal("\r\n\u001B[1;37m=== Dynamic Progress Bars & Spinners ===\u001B[0m\r\n")
                val spinners = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
                for (percent in 0..100 step 2) {
                    if (!isActive) break
                    val sp = spinners[(percent / 2) % spinners.size]
                    val filled = percent / 4
                    val empty = 25 - filled
                    val bar = "█".repeat(filled) + "░".repeat(empty)
                    val color = if (percent < 50) {
                        "\u001B[33m"
                    } else if (percent < 80) {
                        "\u001B[36m"
                    } else {
                        "\u001B[32m"
                    }
                    writeToTerminal(
                        "\r\u001B[K\u001B[35m$sp\u001B[0m Downloading assets: " +
                            "[$color$bar\u001B[0m] \u001B[1m$percent%\u001B[0m",
                    )
                    delay(30)
                }
                writeToTerminal("\r\n\u001B[32m✔ Download complete!\u001B[0m\r\n\r\nkmp-jvm-shell$ ")
                activeJob = null
            }
        }

        fun runMatrixDemo() {
            stopActiveAnimation()
            activeJob = scope.launch {
                writeToTerminal("\u001B[?25l\u001B[2J\u001B[H")
                val cols = 80
                val rows = 24
                val drops = IntArray(cols) { Random.nextInt(-20, 0) }
                val chars = "abcdefghijklmnopqrstuvwxyz0123456789@#$%&*+-=~<>{}[]".toCharArray()

                val startTime = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - startTime < 8000) {
                    val frame = StringBuilder()
                    for (c in 0 until cols) {
                        val row = drops[c]
                        if (row in 1..rows) {
                            val ch = chars[Random.nextInt(chars.size)]
                            frame.append("\u001B[$row;${c + 1}H\u001B[1;37m$ch")
                            if (row > 1) {
                                val ch2 = chars[Random.nextInt(chars.size)]
                                frame.append("\u001B[${row - 1};${c + 1}H\u001B[1;32m$ch2")
                            }
                            if (row > 3) {
                                val ch3 = chars[Random.nextInt(chars.size)]
                                frame.append("\u001B[${row - 3};${c + 1}H\u001B[2;32m$ch3")
                            }
                            if (row > 8) {
                                frame.append("\u001B[${row - 8};${c + 1}H ")
                            }
                        }
                        drops[c]++
                        if (drops[c] > rows + 10) {
                            drops[c] = Random.nextInt(-10, 0)
                        }
                    }
                    writeToTerminal(frame.toString())
                    delay(40)
                }
                writeToTerminal(
                    "\u001B[?25h\u001B[2J\u001B[H\u001B[32m" +
                        "[Matrix animation ended. Press Enter]\u001B[0m\r\n\r\nkmp-jvm-shell$ ",
                )
                activeJob = null
            }
        }

        fun runHtopDemo() {
            stopActiveAnimation()
            isInAltScreen = true
            activeJob = scope.launch {
                // Enter Alternate Screen Buffer and hide cursor
                writeToTerminal("\u001B[?1049h\u001B[?25l\u001B[2J\u001B[H")
                var tick = 0
                while (isActive && isInAltScreen) {
                    val sb = StringBuilder()
                    sb.append("\u001B[H")
                    val cpu1 = (40 + 30 * kotlin.math.sin(tick * 0.2)).toInt().coerceIn(5, 98)
                    val cpu2 = (55 + 25 * kotlin.math.cos(tick * 0.15)).toInt().coerceIn(5, 98)
                    val cpu3 = (30 + 40 * kotlin.math.sin(tick * 0.3)).toInt().coerceIn(5, 98)
                    val cpu4 = (70 + 20 * kotlin.math.cos(tick * 0.25)).toInt().coerceIn(5, 98)
                    val mem = (3840 + (tick % 50) * 12)

                    fun makeBar(pct: Int): String {
                        val filled = pct / 5
                        val empty = 20 - filled
                        return "\u001B[32m" + "|".repeat(filled) + "\u001B[0m" + " ".repeat(empty)
                    }

                    sb.append(
                        "\u001B[1;36m 1 \u001B[0m[${makeBar(cpu1)}] \u001B[1m$cpu1%\u001B[0m       " +
                            "\u001B[1;36mTasks:\u001B[0m \u001B[1;32m74 total, 2 running\u001B[0m\r\n",
                    )
                    sb.append(
                        "\u001B[1;36m 2 \u001B[0m[${makeBar(cpu2)}] \u001B[1m$cpu2%\u001B[0m       " +
                            "\u001B[1;36mLoad average:\u001B[0m 0.42 0.38 0.31\r\n",
                    )
                    sb.append(
                        "\u001B[1;36m 3 \u001B[0m[${makeBar(cpu3)}] \u001B[1m$cpu3%\u001B[0m       " +
                            "\u001B[1;36mUptime:\u001B[0m 14 days, 03:22:18\r\n",
                    )
                    sb.append(
                        "\u001B[1;36m 4 \u001B[0m[${makeBar(cpu4)}] \u001B[1m$cpu4%\u001B[0m       " +
                            "\u001B[1;36mBuffer Mode:\u001B[0m \u001B[1;33mAlternate Screen\u001B[0m\r\n",
                    )
                    sb.append(
                        "\u001B[1;36mMem\u001B[0m[\u001B[34m" + "|".repeat(12) + "\u001B[0m" +
                            " ".repeat(8) + "] $mem/16384 MB\r\n\r\n",
                    )

                    sb.append(
                        "\u001B[7;1m  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   " +
                            "TIME+  Command                     \u001B[0m\r\n",
                    )
                    sb.append(
                        String.format(
                            " %4d root       20   0  1.2G  142M   45M S %4.1f  1.2  02:14.2 compose-render\r\n",
                            1024,
                            cpu1 * 0.4,
                        ),
                    )
                    sb.append(
                        String.format(
                            " %4d johan      20   0  850M   98M   32M S %4.1f  0.8  00:45.1 kotlin-wasm   \r\n",
                            1432,
                            cpu2 * 0.3,
                        ),
                    )
                    sb.append(
                        String.format(
                            " %4d johan      20   0  420M   64M   20M R %4.1f  0.5  01:12.8 kmp-terminal  \r\n",
                            2048,
                            cpu3 * 0.5,
                        ),
                    )
                    sb.append(
                        String.format(
                            " %4d system     20   0  310M   28M   14M S  0.0  0.2  00:03.0 pty-subsystem \r\n",
                            3012,
                        ),
                    )
                    sb.append(
                        String.format(
                            " %4d johan      20   0  180M   16M    8M S  0.0  0.1  00:00.4 htop-sim      \r\n",
                            4096,
                        ),
                    )
                    sb.append(
                        "\r\n\u001B[1;33m[Press 'q' or click 'Exit Htop' to return to normal buffer " +
                            "without losing history]\u001B[0m",
                    )

                    writeToTerminal(sb.toString())
                    tick++
                    delay(500)
                }
            }
        }

        fun executeCommand(rawCmd: String) {
            val cmd = rawCmd.trim()
            if (cmd.isNotEmpty()) {
                commandHistory.add(cmd)
                historyIndex = commandHistory.size
            }
            writeToTerminal("\r\n")
            val parts = cmd.split(" ")
            val action = parts.firstOrNull()?.lowercase() ?: ""

            when (action) {
                "help" -> {
                    val sb = StringBuilder()
                    sb.append("\u001B[1;36mKMP Terminal Emulator Showcase - Commands Catalog\u001B[0m\r\n")
                    sb.append("  \u001B[1;33mcolors\u001B[0m   - 16 ANSI colors, 256 colors & 24-bit TrueColor\r\n")
                    sb.append("  \u001B[1;33mstyles\u001B[0m   - Bold, italic, underline, strikethrough, inverse\r\n")
                    sb.append("  \u001B[1;33municode\u001B[0m  - Box drawing, trees, braille & powerline symbols\r\n")
                    sb.append("  \u001B[1;33mprogress\u001B[0m - Animated progress bars and live spinners\r\n")
                    sb.append("  \u001B[1;33mmatrix\u001B[0m   - Digital rain animation with cursor positioning\r\n")
                    sb.append("  \u001B[1;33mhtop\u001B[0m     - Live system monitor in Alternate Screen Buffer\r\n")
                    sb.append("  \u001B[1;33mclear\u001B[0m    - Clear screen buffer\r\n")
                    sb.append("  \u001B[1;33mecho\u001B[0m     - Echo text back to terminal\r\n")
                    sb.append("  \u001B[1;33mping\u001B[0m     - Respond with pong\r\n")
                    writeToTerminal(sb.toString())
                }
                "colors" -> runColorsDemo()
                "styles" -> runStylesDemo()
                "unicode", "box" -> runUnicodeDemo()
                "progress" -> {
                    runProgressDemo()
                    return
                }
                "matrix" -> {
                    runMatrixDemo()
                    return
                }
                "htop", "top" -> {
                    runHtopDemo()
                    return
                }
                "clear" -> writeToTerminal("\u001B[2J\u001B[H")
                "ping" -> writeToTerminal("pong!\r\n")
                "echo" -> {
                    val echoText = parts.drop(1).joinToString(" ")
                    writeToTerminal("$echoText\r\n")
                }
                "" -> {}
                else -> {
                    writeToTerminal(
                        "Unknown command: '$cmd'. Type '\u001B[33mhelp\u001B[0m' for available commands.\r\n",
                    )
                }
            }
            writeToTerminal("kmp-jvm-shell$ ")
        }

        val uiConfig = remember(selectedColorScheme, selectedCursorStyle, terminalFontSize) {
            terminalUiConfig {
                typography {
                    fontSize = terminalFontSize.sp
                }
                colorScheme = selectedColorScheme
                cursor {
                    style = selectedCursorStyle
                    blink = true
                }
                gestures {
                    enableTouchToFocus = true
                    enableSelection = true
                    wheelScrollMultiplier = 1.2f
                }
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Color(0xFF0D1117),
                surface = Color(0xFF161B22),
                primary = Color(0xFF58A6FF),
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Controls & Showcase Toolbar
                    TopToolbar(
                        selectedColorScheme = selectedColorScheme,
                        onColorSchemeChange = { selectedColorScheme = it },
                        selectedCursorStyle = selectedCursorStyle,
                        onCursorStyleChange = { selectedCursorStyle = it },
                        fontSize = terminalFontSize,
                        onFontSizeChange = { terminalFontSize = it.coerceIn(10f, 24f) },
                        isInAltScreen = isInAltScreen,
                        onExitAltScreen = {
                            stopActiveAnimation()
                            writeToTerminal("kmp-jvm-shell$ ")
                        },
                        onRunColors = {
                            runColorsDemo()
                            writeToTerminal("kmp-jvm-shell$ ")
                        },
                        onRunStyles = {
                            runStylesDemo()
                            writeToTerminal("kmp-jvm-shell$ ")
                        },
                        onRunUnicode = {
                            runUnicodeDemo()
                            writeToTerminal("kmp-jvm-shell$ ")
                        },
                        onRunProgress = { runProgressDemo() },
                        onRunMatrix = { runMatrixDemo() },
                        onRunHtop = { runHtopDemo() },
                        onClear = { writeToTerminal("\u001B[2J\u001B[Hkmp-jvm-shell$ ") },
                    )

                    // Terminal Canvas Container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(selectedColorScheme.background)
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp)),
                    ) {
                        val terminalState by emulator.screenState.collectAsState()

                        TerminalRenderer(
                            modifier = Modifier.fillMaxSize(),
                            terminalState = terminalState,
                            config = uiConfig,
                            onInput = { input ->
                                if (isInAltScreen || activeJob != null) {
                                    if (input.contains("q") || input.contains("\u0003")) {
                                        stopActiveAnimation()
                                        writeToTerminal("\r\nkmp-jvm-shell$ ")
                                    }
                                    return@TerminalRenderer
                                }

                                for (char in input) {
                                    when (char) {
                                        '\r', '\n' -> {
                                            val cmd = commandBuffer.toString()
                                            commandBuffer.setLength(0)
                                            executeCommand(cmd)
                                        }
                                        '\u007f', '\b' -> {
                                            if (commandBuffer.isNotEmpty()) {
                                                commandBuffer.deleteAt(commandBuffer.length - 1)
                                                writeToTerminal("\b \b")
                                            }
                                        }
                                        '\u0003' -> { // Ctrl+C
                                            commandBuffer.setLength(0)
                                            writeToTerminal("^C\r\nkmp-jvm-shell$ ")
                                        }
                                        else -> {
                                            if (char.code in 32..126) {
                                                commandBuffer.append(char)
                                                writeToTerminal(char.toString())
                                            }
                                        }
                                    }
                                }
                            },
                            onArrowKey = { arrow, _ ->
                                if (!isInAltScreen && activeJob == null) {
                                    when (arrow) {
                                        com.johnan.terminal.core.ArrowDirection.UP -> {
                                            if (commandHistory.isNotEmpty() && historyIndex > 0) {
                                                historyIndex--
                                                val prev = commandHistory[historyIndex]
                                                val clearBack = "\b \b".repeat(commandBuffer.length)
                                                commandBuffer.setLength(0)
                                                commandBuffer.append(prev)
                                                writeToTerminal("$clearBack$prev")
                                            }
                                        }
                                        com.johnan.terminal.core.ArrowDirection.DOWN -> {
                                            if (historyIndex < commandHistory.size - 1) {
                                                historyIndex++
                                                val next = commandHistory[historyIndex]
                                                val clearBack = "\b \b".repeat(commandBuffer.length)
                                                commandBuffer.setLength(0)
                                                commandBuffer.append(next)
                                                writeToTerminal("$clearBack$next")
                                            } else if (historyIndex == commandHistory.size - 1) {
                                                historyIndex = commandHistory.size
                                                val clearBack = "\b \b".repeat(commandBuffer.length)
                                                commandBuffer.setLength(0)
                                                writeToTerminal(clearBack)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            onLog = {},
                            focusRequester = focusRequester,
                            onResize = { r, c ->
                                scope.launch { emulator.resize(r, c) }
                            },
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    writeToTerminal(
                        "\u001B[1;36m╭────────────────────────────────────────────────────────────╮\u001B[0m\r\n",
                    )
                    writeToTerminal(
                        "\u001B[1;36m│\u001B[0m  \u001B[1;32m⚡ KMP Terminal Emulator (Compose Desktop Demo)" +
                            "\u001B[0m           \u001B[1;36m│\u001B[0m\r\n",
                    )
                    writeToTerminal(
                        "\u001B[1;36m│\u001B[0m  Features: 24-bit TrueColor, Alt-Buffer, Unicode, Matrix    " +
                            "\u001B[1;36m│\u001B[0m\r\n",
                    )
                    writeToTerminal(
                        "\u001B[1;36m│\u001B[0m  Try buttons above or type '\u001B[33mhelp\u001B[0m', " +
                            "'\u001B[33mcolors\u001B[0m', " +
                            "'\u001B[33mhtop\u001B[0m'.         \u001B[1;36m│\u001B[0m\r\n",
                    )
                    writeToTerminal(
                        "\u001B[1;36m╰────────────────────────────────────────────────────────────╯\u001B[0m\r\n\r\n",
                    )
                    writeToTerminal("kmp-jvm-shell$ ")
                }
            }
        }
    }
}

@Composable
fun TopToolbar(
    selectedColorScheme: TerminalColorScheme,
    onColorSchemeChange: (TerminalColorScheme) -> Unit,
    selectedCursorStyle: TerminalCursorStyle,
    onCursorStyleChange: (TerminalCursorStyle) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    isInAltScreen: Boolean,
    onExitAltScreen: () -> Unit,
    onRunColors: () -> Unit,
    onRunStyles: () -> Unit,
    onRunUnicode: () -> Unit,
    onRunProgress: () -> Unit,
    onRunMatrix: () -> Unit,
    onRunHtop: () -> Unit,
    onClear: () -> Unit,
) {
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    var cursorDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF161B22),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF30363D)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Title and badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "KMP Terminal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF58A6FF),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF238636).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF238636), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "JVM Desktop",
                            color = Color(0xFF3FB950),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Configuration dropdowns & font scaling
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Theme selector
                    Box {
                        OutlinedButton(
                            onClick = { themeDropdownExpanded = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC9D1D9)),
                        ) {
                            Text("Theme: ${selectedColorScheme.name}", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = themeDropdownExpanded,
                            onDismissRequest = { themeDropdownExpanded = false },
                        ) {
                            TerminalColorScheme.PRESETS.forEach { scheme ->
                                DropdownMenuItem(
                                    text = { Text(scheme.name) },
                                    onClick = {
                                        onColorSchemeChange(scheme)
                                        themeDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // Cursor style selector
                    Box {
                        OutlinedButton(
                            onClick = { cursorDropdownExpanded = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC9D1D9)),
                        ) {
                            Text("Cursor: ${selectedCursorStyle.name}", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = cursorDropdownExpanded,
                            onDismissRequest = { cursorDropdownExpanded = false },
                        ) {
                            TerminalCursorStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.name) },
                                    onClick = {
                                        onCursorStyleChange(style)
                                        cursorDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // Font size buttons
                    Row(
                        modifier = Modifier
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "-",
                            modifier = Modifier
                                .clickable { onFontSizeChange(fontSize - 1f) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color(0xFFC9D1D9),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${fontSize.toInt()}sp",
                            fontSize = 12.sp,
                            color = Color(0xFF8B949E),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Text(
                            "+",
                            modifier = Modifier
                                .clickable { onFontSizeChange(fontSize + 1f) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color(0xFFC9D1D9),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Demo Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Showcase:",
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E),
                    fontWeight = FontWeight.SemiBold,
                )

                if (isInAltScreen) {
                    Button(
                        onClick = onExitAltScreen,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                    ) {
                        Text("Exit Alternate Screen ('q')", fontSize = 11.sp)
                    }
                } else {
                    DemoChip("Colors & 24-bit", Color(0xFF1F6FEB), onRunColors)
                    DemoChip("Styles", Color(0xFF238636), onRunStyles)
                    DemoChip("Unicode & Box", Color(0xFF8957E5), onRunUnicode)
                    DemoChip("Progress Bar", Color(0xFFD29922), onRunProgress)
                    DemoChip("Matrix Rain", Color(0xFF238636), onRunMatrix)
                    DemoChip("Htop Alt-Buffer", Color(0xFFDA3633), onRunHtop)
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onClear,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8B949E)),
                ) {
                    Text("Clear", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DemoChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
