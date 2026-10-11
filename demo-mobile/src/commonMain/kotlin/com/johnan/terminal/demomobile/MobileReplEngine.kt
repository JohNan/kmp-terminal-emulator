package com.johnan.terminal.demomobile

import com.johnan.terminal.core.TerminalEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Interactive REPL engine and ANSI sequence generator designed for mobile demonstration.
 */
class MobileReplEngine(
    private val emulator: TerminalEmulator,
    private val scope: CoroutineScope,
) {
    private var activeJob: Job? = null
    var isInAltScreen: Boolean = false
        private set

    private val inputBuffer = StringBuilder()
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    suspend fun start() {
        printBanner()
        printPrompt()
    }

    fun stopActiveAnimation() {
        activeJob?.cancel()
        activeJob = null
        if (isInAltScreen) {
            scope.launch {
                emulator.processOutput("\u001B[?1049l\u001B[?25h")
                isInAltScreen = false
                printPrompt()
            }
        }
    }

    suspend fun printBanner() {
        val banner = buildString {
            append("\u001B[1;36m┌────────────────────────────────────────────────────────┐\u001B[0m\r\n")
            append(
                "\u001B[1;36m│\u001B[0m   \u001B[1;32m⚡ KMP Terminal Emulator - Mobile Showcase\u001B[0m           \u001B[1;36m│\u001B[0m\r\n"
            )
            append(
                "\u001B[1;36m│\u001B[0m   Compose Multiplatform Android & iOS Native Demo      \u001B[1;36m│\u001B[0m\r\n"
            )
            append("\u001B[1;36m└────────────────────────────────────────────────────────┘\u001B[0m\r\n")
            append(
                "\u001B[90mType \u001B[1;33mhelp\u001B[0m\u001B[90m or tap the quick-action buttons above.\u001B[0m\r\n\r\n"
            )
        }
        emulator.processOutput(banner)
    }

    suspend fun printPrompt() {
        emulator.processOutput("\u001B[1;32mmobile\u001B[0m:\u001B[1;34m~#\u001B[0m ")
    }

    fun handleInput(input: String) {
        scope.launch {
            for (ch in input) {
                when (ch) {
                    '\r', '\n' -> {
                        emulator.processOutput("\r\n")
                        val line = inputBuffer.toString().trim()
                        inputBuffer.clear()
                        if (line.isNotEmpty()) {
                            history.add(line)
                            historyIndex = history.size
                            executeCommand(line)
                        } else {
                            printPrompt()
                        }
                    }
                    '\u007F', '\b' -> {
                        if (inputBuffer.isNotEmpty()) {
                            inputBuffer.deleteAt(inputBuffer.length - 1)
                            emulator.processOutput("\b \b")
                        }
                    }
                    '\u0003' -> { // Ctrl+C
                        stopActiveAnimation()
                        inputBuffer.clear()
                        emulator.processOutput("^C\r\n")
                        printPrompt()
                    }
                    '\u000C' -> { // Ctrl+L
                        clearScreen()
                    }
                    else -> {
                        if (ch.code in 32..126) {
                            inputBuffer.append(ch)
                            emulator.processOutput(ch.toString())
                        }
                    }
                }
            }
        }
    }

    fun handleArrowKey(isUp: Boolean) {
        scope.launch {
            if (history.isEmpty()) return@launch
            if (isUp) {
                if (historyIndex > 0) historyIndex--
            } else {
                if (historyIndex < history.size - 1) historyIndex++ else historyIndex = history.size
            }

            val replacement = if (historyIndex in history.indices) history[historyIndex] else ""
            while (inputBuffer.isNotEmpty()) {
                inputBuffer.deleteAt(inputBuffer.length - 1)
                emulator.processOutput("\b \b")
            }
            inputBuffer.append(replacement)
            emulator.processOutput(replacement)
        }
    }

    private suspend fun executeCommand(command: String) {
        stopActiveAnimation()
        when (command.lowercase()) {
            "help" -> printHelp()
            "clear" -> clearScreen()
            "colors" -> runColorsDemo()
            "styles" -> runStylesDemo()
            "unicode" -> runUnicodeDemo()
            "htop" -> runHtopDemo()
            "matrix" -> runMatrixDemo()
            "progress" -> runProgressDemo()
            "about" -> printAbout()
            else -> {
                if (command.startsWith("echo ")) {
                    emulator.processOutput(command.removePrefix("echo ") + "\r\n")
                } else {
                    emulator.processOutput(
                        "\u001B[31mCommand not found:\u001B[0m $command. Type \u001B[33mhelp\u001B[0m for commands.\r\n"
                    )
                }
                printPrompt()
            }
        }
    }

    suspend fun clearScreen() {
        stopActiveAnimation()
        inputBuffer.clear()
        emulator.processOutput("\u001B[2J\u001B[H")
        printPrompt()
    }

    suspend fun printHelp() {
        val help = buildString {
            append("\u001B[1mAvailable Commands & Demonstrations:\u001B[0m\r\n")
            append("  \u001B[33mcolors\u001B[0m    - 16 ANSI, 256 indexed, and 24-bit TrueColor palettes\r\n")
            append("  \u001B[33mstyles\u001B[0m    - Font styling attributes (bold, italic, underline, blink)\r\n")
            append("  \u001B[33municode\u001B[0m   - UTF-8 box drawing and monospace symbol metrics\r\n")
            append("  \u001B[33mhtop\u001B[0m      - Alternate screen buffer simulation\r\n")
            append("  \u001B[33mmatrix\u001B[0m    - Live streaming matrix digital rain\r\n")
            append("  \u001B[33mprogress\u001B[0m  - Streaming progress bars & autoscroll check\r\n")
            append("  \u001B[33mclear\u001B[0m     - Clear terminal screen\r\n")
            append("  \u001B[33mabout\u001B[0m     - Architecture & library metadata\r\n")
        }
        emulator.processOutput(help)
        printPrompt()
    }

    suspend fun printAbout() {
        val text = buildString {
            append("\u001B[1;35mKMP Terminal Emulator Engine\u001B[0m\r\n")
            append("  • Core Engine: Pure Kotlin Multiplatform (:terminal-core)\r\n")
            append("  • Canvas UI: Compose Multiplatform Hardware Accelerated (:terminal-ui)\r\n")
            append("  • Viewport: Low-allocation monospace layout with soft-wrap detection\r\n")
            append("  • Gestures: Touch selection, long-press handles, and wheel-only scrolling\r\n")
        }
        emulator.processOutput(text)
        printPrompt()
    }

    fun runColorsDemo() {
        stopActiveAnimation()
        scope.launch {
            val sb = StringBuilder()
            sb.append("\r\n\u001B[1m1. Standard & Bright ANSI Colors:\u001B[0m\r\n")
            for (i in 0..7) {
                sb.append("\u001B[48;5;${i}m   \u001B[0m")
            }
            sb.append("\r\n")
            for (i in 8..15) {
                sb.append("\u001B[48;5;${i}m   \u001B[0m")
            }
            sb.append("\r\n\r\n\u001B[1m2. 24-bit TrueColor Gradient:\u001B[0m\r\n")
            for (col in 0..29) {
                val r = (col * 255 / 29)
                val g = ((29 - col) * 200 / 29)
                val b = 255
                sb.append("\u001B[48;2;$r;$g;${b}m \u001B[0m")
            }
            sb.append("\r\n\r\n")
            emulator.processOutput(sb.toString())
            printPrompt()
        }
    }

    fun runStylesDemo() {
        stopActiveAnimation()
        scope.launch {
            val sb = StringBuilder()
            sb.append("\r\n\u001B[1mText Styling Demonstration:\u001B[0m\r\n")
            sb.append("  \u001B[1mBold Text\u001B[0m\r\n")
            sb.append("  \u001B[2mDim Text\u001B[0m\r\n")
            sb.append("  \u001B[3mItalic Text\u001B[0m\r\n")
            sb.append("  \u001B[4mUnderline Text\u001B[0m\r\n")
            sb.append("  \u001B[5mBlinking Text\u001B[0m\r\n")
            sb.append("  \u001B[7mInverted Colors\u001B[0m\r\n")
            sb.append("  \u001B[9mStrikethrough\u001B[0m\r\n\r\n")
            emulator.processOutput(sb.toString())
            printPrompt()
        }
    }

    fun runUnicodeDemo() {
        stopActiveAnimation()
        scope.launch {
            val sb = StringBuilder()
            sb.append("\r\n\u001B[1mBox Drawing & Monospace Metrics:\u001B[0m\r\n")
            sb.append("  ┌─────────┬─────────┐\r\n")
            sb.append("  │ Col 1   │ Col 2   │\r\n")
            sb.append("  ├─────────┼─────────┤\r\n")
            sb.append("  │ 42.0 MB │ 98.4 %  │\r\n")
            sb.append("  └─────────┴─────────┘\r\n\r\n")
            emulator.processOutput(sb.toString())
            printPrompt()
        }
    }

    fun runHtopDemo() {
        stopActiveAnimation()
        isInAltScreen = true
        scope.launch {
            emulator.processOutput("\u001B[?1049h\u001B[H\u001B[2J")
            val htop = buildString {
                append(
                    "\u001B[1;37;44m  HTOP - Mobile Terminal Monitor               [Tasks: 34, 1 thr; 1 running]  \u001B[0m\r\n"
                )
                append("\u001B[1m  CPU[|||||||||||||||||||||||||||||||         68.2%]   Tasks: 34 total\u001B[0m\r\n")
                append(
                    "\u001B[1m  MEM[||||||||||||||||                        42.1%]   Uptime: 04:18:22\u001B[0m\r\n\r\n"
                )
                append(
                    "\u001B[7m  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   TIME+  Command         \u001B[0m\r\n"
                )
                append(" 1042 root       20   0  1.2G  140M   64M S 22.0  3.4  0:14.23 terminal-core   \r\n")
                append(" 1043 mobile     20   0  820M   98M   42M S 18.5  2.1  0:08.10 compose-skia    \r\n")
                append(" 1044 mobile     20   0  350M   32M   18M R  2.1  0.8  0:01.05 repl-worker     \r\n\r\n")
                append("\u001B[90m(Tap 'Clear' or type Ctrl+C in toolbar to exit alternate buffer)\u001B[0m\r\n")
            }
            emulator.processOutput(htop)
        }
    }

    fun runMatrixDemo() {
        stopActiveAnimation()
        activeJob = scope.launch {
            emulator.processOutput("\u001B[2J\u001B[H\u001B[?25l")
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%^&*"
            var step = 0
            while (isActive && step < 40) {
                val sb = StringBuilder()
                for (i in 0..12) {
                    val ch = chars[Random.nextInt(chars.length)]
                    val color = if (Random.nextBoolean()) "\u001B[1;32m" else "\u001B[32m"
                    sb.append("$color$ch \u001B[0m")
                }
                sb.append("\r\n")
                emulator.processOutput(sb.toString())
                step++
                delay(100)
            }
            emulator.processOutput("\u001B[?25h\r\n\u001B[32mMatrix simulation complete.\u001B[0m\r\n")
            printPrompt()
            activeJob = null
        }
    }

    fun runProgressDemo() {
        stopActiveAnimation()
        activeJob = scope.launch {
            emulator.processOutput("\r\n\u001B[1mStreaming Output & Autoscroll Test:\u001B[0m\r\n")
            for (p in 0..100 step 10) {
                if (!isActive) break
                val filled = p / 5
                val bar = "=".repeat(filled) + " ".repeat(20 - filled)
                emulator.processOutput("\r[\u001B[32m$bar\u001B[0m] $p%")
                delay(120)
            }
            emulator.processOutput("\r\n\u001B[32mDownload complete!\u001B[0m\r\n")
            printPrompt()
            activeJob = null
        }
    }
}
