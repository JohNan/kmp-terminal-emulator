package com.johnan.terminal.core

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min
import kotlin.time.Clock

/**
 * ANSI / VT100 / xterm escape sequence parser that executes control codes and SGR attributes on [ScreenBuffer].
 */
class AnsiParser(
    private val terminalEmulator: TerminalEmulator,
    private val screenBuffer: ScreenBuffer,
    private val logCallback: ((String) -> Unit)? = null,
) {
    private enum class State {
        NORMAL,
        ESC,
        CSI,
        OSC,
        APC,
        DCS,
        PM,
        SOS,
        CHARSET,
    }

    private var state = State.NORMAL
    private var escapeInStringState: State? = null
    private val paramBuffer = StringBuilder()
    private var leftoverBytes = ByteArray(0)

    private val paramValues = IntArray(64)
    private val subParamCounts = IntArray(64)
    private val subParamValues = IntArray(64 * 8)
    private var paramCount = 0
    private var paramsParsed = false

    private var g0Charset = 'B'
    private var g1Charset = 'B'
    private var activeCharset = 0
    private var charsetTarget = 0

    private val currentCharset: Char get() = if (activeCharset == 0) g0Charset else g1Charset
    private var lastPrintableCodepoint: Int? = null
    private var pendingHighSurrogate: Char? = null

    private var kittyChunkHeader: Map<String, String>? = null
    private val kittyChunkPayload = StringBuilder()

    /**
     * Creates a copy of this parser bound to [terminalEmulator] and [screenBuffer], including any partially
     * parsed escape sequence, buffered parameters and incomplete UTF-8 bytes.
     *
     * Every mutable field is copied; the immutable constructor references are replaced by the arguments.
     */
    internal fun copy(
        terminalEmulator: TerminalEmulator,
        screenBuffer: ScreenBuffer,
        logCallback: ((String) -> Unit)? = null,
    ): AnsiParser {
        val clone = AnsiParser(terminalEmulator, screenBuffer, logCallback)
        clone.state = state
        clone.paramBuffer.append(paramBuffer)
        clone.leftoverBytes = leftoverBytes.copyOf()
        paramValues.copyInto(clone.paramValues)
        subParamCounts.copyInto(clone.subParamCounts)
        subParamValues.copyInto(clone.subParamValues)
        clone.paramCount = paramCount
        clone.paramsParsed = paramsParsed
        clone.g0Charset = g0Charset
        clone.g1Charset = g1Charset
        clone.activeCharset = activeCharset
        clone.charsetTarget = charsetTarget
        clone.lastPrintableCodepoint = lastPrintableCodepoint
        clone.pendingHighSurrogate = pendingHighSurrogate
        clone.escapeInStringState = escapeInStringState
        clone.kittyChunkHeader = kittyChunkHeader?.toMap()
        clone.kittyChunkPayload.append(kittyChunkPayload)
        return clone
    }

    companion object {
        const val MAX_OSC52_PAYLOAD_SIZE = 65536
        const val MAX_CONTROL_STRING_SIZE = 16 * 1024 * 1024
    }

    private inline fun log(
        operation: String,
        crossinline params: () -> String,
    ) {
        if (logCallback != null) {
            val now = Clock.System.now()
            val dt = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val h = dt.hour.toString().padStart(2, '0')
            val m = dt.minute.toString().padStart(2, '0')
            val s = dt.second.toString().padStart(2, '0')
            val ms = (dt.nanosecond / 1_000_000).toString().padStart(3, '0')
            val timestamp = "$h:$m:$s.$ms"
            val message = "[$timestamp][terminal] $operation ${params()}"
            logCallback.invoke(message)
        }
    }

    private fun log(
        operation: String,
        params: String = "",
    ) {
        if (logCallback != null) {
            log(operation) { params }
        }
    }

    /**
     * Decodes and parses incoming UTF-8 byte stream, buffering incomplete multi-byte sequences across chunks.
     */
    fun processBytes(bytes: ByteArray, length: Int = bytes.size) {
        val len = length.coerceIn(0, bytes.size)
        if (len == 0 && leftoverBytes.isEmpty()) return

        val combinedBytes = if (leftoverBytes.isNotEmpty()) {
            val combined = ByteArray(leftoverBytes.size + len)
            leftoverBytes.copyInto(combined, 0)
            bytes.copyInto(combined, leftoverBytes.size, 0, len)
            leftoverBytes = ByteArray(0)
            combined
        } else {
            bytes
        }

        val totalLen = if (combinedBytes === bytes) len else combinedBytes.size
        var validLen = totalLen
        for (i in 1..min(3, totalLen)) {
            val byte = combinedBytes[totalLen - i].toInt()
            if ((byte and 0xC0) == 0xC0) {
                val expectedLen = when {
                    (byte and 0xE0) == 0xC0 -> 2
                    (byte and 0xF0) == 0xE0 -> 3
                    (byte and 0xF8) == 0xF0 -> 4
                    else -> 1
                }
                if (expectedLen > i) {
                    val splitPoint = totalLen - i
                    leftoverBytes = combinedBytes.copyOfRange(splitPoint, totalLen)
                    validLen = splitPoint
                }
                break
            }
        }

        if (validLen == 0) return

        val text = combinedBytes.decodeToString(startIndex = 0, endIndex = validLen)
        var charIndex = 0
        while (charIndex < text.length) {
            if (state == State.NORMAL) {
                var count = 0
                val charset = currentCharset

                while ((charIndex + count) < text.length) {
                    val c = text[charIndex + count]
                    if (isPrintable(c)) {
                        count++
                    } else {
                        break
                    }
                }

                if (count > 0 && text[charIndex + count - 1].isHighSurrogate()) {
                    count--
                }

                if (count > 0) {
                    val substring = text.substring(charIndex, charIndex + count)
                    if (charset == '0') {
                        val mapped = CharArray(substring.length)
                        for (i in substring.indices) {
                            mapped[i] = mapCharset(substring[i], charset)
                        }
                        val mappedString = mapped.concatToString()
                        screenBuffer.writeText(mappedString)
                        lastPrintableCodepoint = lastCodepoint(mappedString)
                    } else {
                        screenBuffer.writeText(substring)
                        lastPrintableCodepoint = lastCodepoint(substring)
                    }

                    charIndex += count
                    continue
                }
            }
            processChar(text[charIndex])
            charIndex++
        }
    }

    private fun lastCodepoint(text: CharSequence): Int? {
        if (text.isEmpty()) return null
        val last = text.last()
        if (last.isLowSurrogate() && text.length >= 2 && text[text.length - 2].isHighSurrogate()) {
            val high = text[text.length - 2].code
            val low = last.code
            return ((high - 0xD800) shl 10) + (low - 0xDC00) + 0x10000
        }
        return last.code
    }

    private fun mapCharset(char: Char, charset: Char): Char {
        if (charset != '0') return char
        return when (char) {
            'j' -> '┘'
            'k' -> '┐'
            'l' -> '┌'
            'm' -> '└'
            'n' -> '┼'
            'q' -> '─'
            't' -> '├'
            'u' -> '┤'
            'v' -> '┴'
            'w' -> '┬'
            'x' -> '│'
            'a' -> '▒'
            else -> char
        }
    }

    private fun isPrintable(char: Char): Boolean = (char in '\u0020'..'\u007E') || (char in '\u00A0'..'\uFFFF')

    private fun processChar(char: Char) {
        when (state) {
            State.NORMAL -> processNormal(char)
            State.ESC -> processEscape(char)
            State.CSI -> processCsi(char)
            State.OSC -> processOsc(char)
            State.APC, State.DCS, State.PM, State.SOS -> processControlString(char)
            State.CHARSET -> processCharset(char)
        }
    }

    private fun processNormal(char: Char) {
        when (char) {
            '\u001B' -> {
                pendingHighSurrogate = null
                state = State.ESC
                paramBuffer.clear()
                paramsParsed = false
            }
            '\r' -> {
                pendingHighSurrogate = null
                screenBuffer.carriageReturn()
            }
            '\n' -> {
                pendingHighSurrogate = null
                screenBuffer.lineFeed()
            }
            '\b' -> {
                pendingHighSurrogate = null
                screenBuffer.moveCursor(0, -1)
            }
            '\t' -> {
                pendingHighSurrogate = null
                val nextTab = ((screenBuffer.cursorCol / 8) + 1) * 8
                screenBuffer.setCursorPosition(screenBuffer.cursorRow, nextTab, terminalEmulator.originModeEnabled)
            }
            '\u000E' -> {
                pendingHighSurrogate = null
                activeCharset = 1
            }
            '\u000F' -> {
                pendingHighSurrogate = null
                activeCharset = 0
            }
            '\u0007' -> {
                pendingHighSurrogate = null
                terminalEmulator.triggerBell()
            }
            in '\u0020'..'\u007E', in '\u00A0'..'\uFFFF' -> {
                if (char.isHighSurrogate()) {
                    pendingHighSurrogate = char
                } else if (char.isLowSurrogate()) {
                    val high = pendingHighSurrogate
                    pendingHighSurrogate = null
                    if (high != null) {
                        val codepoint = ((high.code - 0xD800) shl 10) + (char.code - 0xDC00) + 0x10000
                        lastPrintableCodepoint = codepoint
                        screenBuffer.writeCodepoint(codepoint)
                    } else {
                        lastPrintableCodepoint = char.code
                        screenBuffer.writeCodepoint(char.code)
                    }
                } else {
                    pendingHighSurrogate = null
                    val mapped = mapCharset(char, currentCharset)
                    lastPrintableCodepoint = mapped.code
                    screenBuffer.writeCodepoint(mapped.code)
                }
            }
        }
    }

    private fun processEscape(char: Char) {
        if (escapeInStringState != null) {
            val prev = escapeInStringState
            escapeInStringState = null
            if (char == '\\') {
                if (prev == State.OSC) {
                    finishOsc()
                } else if (prev == State.APC) {
                    finishApc()
                } else if (prev != null) {
                    log(prev.name) { "len=${paramBuffer.length}" }
                }
                state = State.NORMAL
                paramBuffer.clear()
                paramsParsed = false
                return
            }
            if (prev == State.OSC) {
                finishOsc()
            }
            paramBuffer.clear()
            paramsParsed = false
        }

        when (char) {
            '[' -> {
                state = State.CSI
                paramBuffer.clear()
                paramsParsed = false
            }
            ']' -> {
                state = State.OSC
                paramBuffer.clear()
                paramsParsed = false
            }
            '_' -> {
                state = State.APC
                paramBuffer.clear()
                paramsParsed = false
            }
            'P' -> {
                state = State.DCS
                paramBuffer.clear()
                paramsParsed = false
            }
            '^' -> {
                state = State.PM
                paramBuffer.clear()
                paramsParsed = false
            }
            'X' -> {
                state = State.SOS
                paramBuffer.clear()
                paramsParsed = false
            }
            '(' -> {
                state = State.CHARSET
                paramBuffer.clear()
                paramsParsed = false
                charsetTarget = 0
            }
            ')' -> {
                state = State.CHARSET
                paramBuffer.clear()
                paramsParsed = false
                charsetTarget = 1
            }
            'M' -> {
                log("RI", "Reverse Index")
                screenBuffer.cursorUp()
                state = State.NORMAL
            }
            '\\' -> {
                log("ST", "String Terminator")
                state = State.NORMAL
            }
            'D' -> {
                log("IND", "Index")
                screenBuffer.lineFeed()
                state = State.NORMAL
            }
            'E' -> {
                log("NEL", "Next Line")
                screenBuffer.carriageReturn()
                screenBuffer.lineFeed()
                state = State.NORMAL
            }
            '7' -> {
                log("DECSC", "Save Cursor")
                screenBuffer.saveCursor()
                state = State.NORMAL
            }
            '8' -> {
                log("DECRC", "Restore Cursor")
                screenBuffer.restoreCursor()
                state = State.NORMAL
            }
            'c' -> {
                log("RIS", "Reset Initial State")
                screenBuffer.clearScreen()
                screenBuffer.setCursorPosition(0, 0)
                screenBuffer.resetTextAttributes()
                terminalEmulator.synchronizedOutputEnabled = false
                terminalEmulator.focusReportingEnabled = false
                terminalEmulator.cursorShape = CursorShape.BLOCK
                state = State.NORMAL
            }
            '=' -> {
                log("DECKPAM", "Enabled")
                terminalEmulator.applicationKeypadModeEnabled = true
                state = State.NORMAL
            }
            '>' -> {
                log("DECKPNM", "Disabled (Normal)")
                terminalEmulator.applicationKeypadModeEnabled = false
                state = State.NORMAL
            }
            else -> {
                log("UNKNOWN_ESC") { "char=$char" }
                state = State.NORMAL
            }
        }
    }

    private fun processCsi(char: Char) {
        when (char) {
            in '0'..'9', ';', ':', '?', '>', '<', '=', ' ', '$', '!' -> {
                if (paramBuffer.length < 256) {
                    paramBuffer.append(char)
                }
            }
            'd' -> {
                val row = parseParam(0, 1) - 1
                log("VPA", "row=${row + 1}")
                screenBuffer.setCursorPosition(row, screenBuffer.cursorCol, terminalEmulator.originModeEnabled)
                state = State.NORMAL
            }
            'G' -> {
                val col = parseParam(0, 1) - 1
                log("CHA", "col=${col + 1}")
                screenBuffer.setCursorPosition(screenBuffer.cursorRow, col, terminalEmulator.originModeEnabled)
                state = State.NORMAL
            }
            't' -> {
                val mode = parseParam(0, 0)
                log("WindowManipulation") { "mode=$mode" }
                when (mode) {
                    14 -> terminalEmulator.sendResponse(
                        "\u001B[4;${screenBuffer.rows * terminalEmulator.cellHeightPx};" +
                            "${screenBuffer.cols * terminalEmulator.cellWidthPx}t",
                    )
                    16 -> terminalEmulator.sendResponse(
                        "\u001B[6;${terminalEmulator.cellHeightPx};${terminalEmulator.cellWidthPx}t",
                    )
                    18 -> terminalEmulator.sendResponse("\u001B[8;${screenBuffer.rows};${screenBuffer.cols}t")
                    22 -> terminalEmulator.pushWindowTitle()
                    23 -> terminalEmulator.popWindowTitle()
                }
                state = State.NORMAL
            }
            'A' -> {
                val count = parseParam(0, 1)
                log("CUU") { "count=$count" }
                screenBuffer.cursorUp(count)
                state = State.NORMAL
            }
            'B' -> {
                val count = parseParam(0, 1)
                log("CUD") { "count=$count" }
                screenBuffer.cursorDown(count)
                state = State.NORMAL
            }
            'C' -> {
                val count = parseParam(0, 1)
                log("CUF") { "count=$count" }
                screenBuffer.cursorForward(count)
                state = State.NORMAL
            }
            'D' -> {
                val count = parseParam(0, 1)
                log("CUB") { "count=$count" }
                screenBuffer.cursorBackward(count)
                state = State.NORMAL
            }
            'S' -> {
                val lines = parseParam(0, 1)
                log("SU") { "lines=$lines" }
                screenBuffer.scrollUp(lines)
                state = State.NORMAL
            }
            'T' -> {
                val lines = parseParam(0, 1)
                log("SD") { "lines=$lines" }
                screenBuffer.scrollDown(lines)
                state = State.NORMAL
            }
            'b' -> {
                val count = parseParam(0, 1)
                val cpToRepeat = lastPrintableCodepoint ?: ' '.code
                log("REP") { "codepoint=$cpToRepeat, count=$count" }
                repeat(count) { screenBuffer.writeCodepoint(cpToRepeat) }
                state = State.NORMAL
            }
            'H', 'f' -> {
                parseParamsToBuffer()
                val row = (if (paramCount > 0) paramValues[0] else 1) - 1
                val col = (if (paramCount > 1) paramValues[1] else 1) - 1
                log("CUP") { "row=${row + 1}, col=${col + 1}, originMode=${terminalEmulator.originModeEnabled}" }
                screenBuffer.setCursorPosition(row, col, terminalEmulator.originModeEnabled)
                state = State.NORMAL
            }
            'c' -> {
                val param = parseParam(0, 0)
                val prefix = if (paramBuffer.isNotEmpty()) paramBuffer[0] else ' '
                log("DA") { "prefix=$prefix, param=$param" }
                if (prefix == '>') {
                    if (param == 0) {
                        terminalEmulator.sendResponse("\u001B[>0;10;0c")
                    }
                } else if (prefix != '<' && prefix != '=' && param == 0) {
                    terminalEmulator.sendResponse("\u001B[?1;0c")
                }
                state = State.NORMAL
            }
            'n' -> {
                val param = parseParam(0, 0)
                log("DSR") { "param=$param" }
                if (param == 6) {
                    val row = screenBuffer.cursorRow + 1
                    val col = screenBuffer.cursorCol + 1
                    terminalEmulator.sendResponse("\u001B[$row;${col}R")
                }
                state = State.NORMAL
            }
            'J' -> {
                val mode = parseParam(0, 0)
                log("ED") { "mode=$mode, cursor=${screenBuffer.cursorRow + 1}:${screenBuffer.cursorCol + 1}" }
                when (mode) {
                    0 -> screenBuffer.clearToEndOfScreen()
                    1 -> screenBuffer.clearToStartOfScreen()
                    2, 3 -> screenBuffer.clearScreen()
                }
                state = State.NORMAL
            }
            'K' -> {
                val mode = parseParam(0, 0)
                log("EL") { "mode=$mode, cursor=${screenBuffer.cursorRow + 1}:${screenBuffer.cursorCol + 1}" }
                when (mode) {
                    0 -> screenBuffer.clearToEndOfLine()
                    1 -> screenBuffer.clearToStartOfLine()
                    2 -> screenBuffer.clearLine()
                }
                state = State.NORMAL
            }
            'm' -> {
                parseParamsToBuffer()
                processSgr(paramCount)
                state = State.NORMAL
            }
            'h' -> {
                parseParamsToBuffer()
                setMode(paramCount)
                state = State.NORMAL
            }
            'l' -> {
                parseParamsToBuffer()
                resetMode(paramCount)
                state = State.NORMAL
            }
            's' -> {
                log("SCOSC", "Save Cursor")
                screenBuffer.saveCursor()
                state = State.NORMAL
            }
            'u' -> {
                log("SCORC", "Restore Cursor")
                screenBuffer.restoreCursor()
                state = State.NORMAL
            }
            'r' -> {
                parseParamsToBuffer()
                val top = (if (paramCount > 0) paramValues[0] else 1) - 1
                val bottom = (if (paramCount > 1) paramValues[1] else screenBuffer.rows) - 1

                log("DECSTBM") { "top=${top + 1}, bottom=${bottom + 1}, bufferRows=${screenBuffer.rows}" }

                if (top >= 0 && bottom < screenBuffer.rows && top < bottom) {
                    screenBuffer.setScrollingRegion(top, bottom)
                    screenBuffer.setCursorPosition(0, 0, terminalEmulator.originModeEnabled)
                } else {
                    screenBuffer.resetScrollingRegion()
                }
                state = State.NORMAL
            }
            'L' -> {
                val count = parseParam(0, 1)
                log("IL") { "count=$count" }
                screenBuffer.insertLines(count)
                state = State.NORMAL
            }
            'M' -> {
                val count = parseParam(0, 1)
                log("DL") { "count=$count" }
                screenBuffer.deleteLines(count)
                state = State.NORMAL
            }
            'P' -> {
                val count = parseParam(0, 1)
                log("DCH") { "count=$count" }
                screenBuffer.deleteCharacters(count)
                state = State.NORMAL
            }
            '@' -> {
                val count = parseParam(0, 1)
                log("ICH") { "count=$count" }
                screenBuffer.insertCharacters(count)
                state = State.NORMAL
            }
            'X' -> {
                val count = parseParam(0, 1)
                log("ECH") { "count=$count" }
                screenBuffer.eraseCharacters(count)
                state = State.NORMAL
            }
            'q' -> {
                val param = parseParam(0, 0)
                if (paramBuffer.startsWith(">")) {
                    if (param == 0) {
                        terminalEmulator.sendResponse("\u001BP>|kmp-terminal(0.3.11)\u001B\\")
                    }
                } else if (paramBuffer.endsWith(" ")) {
                    when (param) {
                        0, 1 -> {
                            terminalEmulator.cursorShape = CursorShape.BLOCK
                            terminalEmulator.cursorBlinking = true
                        }
                        2 -> {
                            terminalEmulator.cursorShape = CursorShape.BLOCK
                            terminalEmulator.cursorBlinking = false
                        }
                        3 -> {
                            terminalEmulator.cursorShape = CursorShape.UNDERLINE
                            terminalEmulator.cursorBlinking = true
                        }
                        4 -> {
                            terminalEmulator.cursorShape = CursorShape.UNDERLINE
                            terminalEmulator.cursorBlinking = false
                        }
                        5 -> {
                            terminalEmulator.cursorShape = CursorShape.BEAM
                            terminalEmulator.cursorBlinking = true
                        }
                        6 -> {
                            terminalEmulator.cursorShape = CursorShape.BEAM
                            terminalEmulator.cursorBlinking = false
                        }
                    }
                }
                state = State.NORMAL
            }
            'p' -> {
                if (paramBuffer.contentEquals("!")) {
                    log("DECSTR", "Soft Terminal Reset")
                    screenBuffer.resetTextAttributes()
                    terminalEmulator.originModeEnabled = false
                    screenBuffer.setAutoWrapMode(true)
                } else if (paramBuffer.startsWith("?") && paramBuffer.endsWith("$")) {
                    val mode = parseParam(0, 0)
                    log("DECRQM") { "mode=$mode" }
                    val status = when (mode) {
                        1 -> if (terminalEmulator.applicationCursorKeysEnabled) 1 else 2
                        5 -> if (terminalEmulator.invertScreenColors) 1 else 2
                        6 -> if (terminalEmulator.originModeEnabled) 1 else 2
                        7 -> if (screenBuffer.autoWrapMode) 1 else 2
                        12 -> if (terminalEmulator.cursorBlinking) 1 else 2
                        25 -> if (screenBuffer.cursorVisible) 1 else 2
                        47, 1047, 1049 -> if (screenBuffer.isUsingAlternateScreen()) 1 else 2
                        1000 -> if (terminalEmulator.mouseTrackingMode == MouseTrackingMode.Click) 1 else 2
                        1002 -> if (terminalEmulator.mouseTrackingMode == MouseTrackingMode.CellMotion) 1 else 2
                        1003 -> if (terminalEmulator.mouseTrackingMode == MouseTrackingMode.AllMotion) 1 else 2
                        1004 -> if (terminalEmulator.focusReportingEnabled) 1 else 2
                        1006 -> if (terminalEmulator.sgrMouseModeEnabled) 1 else 2
                        2004 -> if (terminalEmulator.bracketedPasteModeEnabled) 1 else 2
                        2026 -> if (terminalEmulator.synchronizedOutputEnabled) 1 else 2
                        else -> 0
                    }
                    terminalEmulator.sendResponse("\u001B[?$mode;${status}\$y")
                }
                state = State.NORMAL
            }
            else -> {
                log("UNKNOWN_CSI") { "char=$char, params=$paramBuffer" }
                state = State.NORMAL
            }
        }
    }

    private fun processOsc(char: Char) {
        when (char) {
            '\u0007', '\u009C' -> {
                finishOsc()
                paramBuffer.clear()
                paramsParsed = false
                state = State.NORMAL
            }
            '\u001B' -> {
                escapeInStringState = State.OSC
                state = State.ESC
            }
            else -> {
                if (paramBuffer.length < MAX_CONTROL_STRING_SIZE) {
                    paramBuffer.append(char)
                }
            }
        }
    }

    private fun finishOsc() {
        val oscData = paramBuffer.toString()
        val semicolonIndex = oscData.indexOf(';')

        if (semicolonIndex != -1) {
            val command = oscData.substring(0, semicolonIndex)
            val value = oscData.substring(semicolonIndex + 1)

            when (command) {
                "52" -> {
                    val parts = value.split(";")
                    if (parts.size == 2) {
                        val pd = parts[1]
                        if (pd == "?") {
                            log("OSC 52") { "Read request ignored for security" }
                        } else {
                            if (pd.length > MAX_OSC52_PAYLOAD_SIZE) {
                                log("OSC 52") {
                                    "Write payload exceeds limit ($MAX_OSC52_PAYLOAD_SIZE bytes)"
                                }
                            } else {
                                try {
                                    val decodedBytes = Base64Decoder.decode(pd)
                                    val decodedString = decodedBytes.decodeToString()
                                    terminalEmulator.handleOsc52Write(decodedString)
                                } catch (e: Exception) {
                                    log("OSC 52") { "Failed to decode base64 payload: ${e.message}" }
                                }
                            }
                        }
                    } else {
                        log("OSC 52") { "Invalid payload format" }
                    }
                }

                "0", "2" -> {
                    terminalEmulator.setWindowTitle(value)
                    log("OSC", "Set window title: $value")
                }

                "9" -> {
                    terminalEmulator.triggerNotification("Terminal Notification", value)
                    log("OSC 9") { "Notification: $value" }
                }

                "777" -> {
                    val parts = value.split(";", limit = 3)
                    if (parts.isNotEmpty() && parts[0] == "notify") {
                        val title = parts.getOrElse(1) { "" }
                        val body = parts.getOrElse(2) { "" }
                        terminalEmulator.triggerNotification(title, body)
                        log("OSC 777") { "Notification: title='$title', body='$body'" }
                    }
                }
            }
        }
    }

    private fun processControlString(char: Char) {
        if (char == '\u009C' || (char == '\u0007' && state == State.APC)) {
            if (state == State.APC) {
                finishApc()
            }
            paramBuffer.clear()
            paramsParsed = false
            state = State.NORMAL
        } else if (char == '\u001B') {
            escapeInStringState = state
            state = State.ESC
        } else {
            if (paramBuffer.length < MAX_CONTROL_STRING_SIZE) {
                paramBuffer.append(char)
            }
        }
    }

    private fun finishApc() {
        log("APC") { "len=${paramBuffer.length}" }
        if (paramBuffer.isNotEmpty() && paramBuffer[0] == 'G' && terminalEmulator.config.kittyGraphicsEnabled) {
            processKittyApc(paramBuffer.substring(1))
        }
    }

    private fun processKittyApc(content: String) {
        val semiIdx = content.indexOf(';')
        val headerStr = if (semiIdx >= 0) content.substring(0, semiIdx) else content
        val payloadChunk = if (semiIdx >= 0) content.substring(semiIdx + 1) else ""

        val currentHeader = mutableMapOf<String, String>()
        if (headerStr.isNotEmpty()) {
            val pairs = headerStr.split(',')
            for (pair in pairs) {
                val eqIdx = pair.indexOf('=')
                if (eqIdx >= 0) {
                    val key = pair.substring(0, eqIdx).trim()
                    val value = pair.substring(eqIdx + 1).trim()
                    currentHeader[key] = value
                }
            }
        }

        val isChunked = currentHeader["m"] == "1"
        val fullHeader: Map<String, String>
        val fullPayload: String

        if (isChunked) {
            if (kittyChunkPayload.length + payloadChunk.length > MAX_CONTROL_STRING_SIZE) {
                kittyChunkHeader = null
                kittyChunkPayload.clear()
                return
            }
            val existingHeader = kittyChunkHeader?.toMutableMap() ?: mutableMapOf()
            existingHeader.putAll(currentHeader)
            kittyChunkHeader = existingHeader
            kittyChunkPayload.append(payloadChunk)
            return
        } else if (kittyChunkHeader != null) {
            if (kittyChunkPayload.length + payloadChunk.length > MAX_CONTROL_STRING_SIZE) {
                kittyChunkHeader = null
                kittyChunkPayload.clear()
                return
            }
            val existingHeader = kittyChunkHeader!!.toMutableMap()
            existingHeader.putAll(currentHeader)
            kittyChunkPayload.append(payloadChunk)
            fullHeader = existingHeader
            fullPayload = kittyChunkPayload.toString()
            kittyChunkHeader = null
            kittyChunkPayload.clear()
        } else {
            fullHeader = currentHeader
            fullPayload = payloadChunk
        }

        val action = fullHeader["a"]?.firstOrNull() ?: 'T'
        val medium = fullHeader["t"] ?: "d"
        val quiet = fullHeader["q"]?.toIntOrNull() ?: 0
        val id = fullHeader["i"]?.toIntOrNull() ?: 0
        val imageNumber = fullHeader["I"]?.toIntOrNull() ?: 0
        val placementId = fullHeader["p"]?.toIntOrNull() ?: 0

        fun sendError(message: String) {
            if (quiet < 2) {
                val keys = mutableListOf<String>()
                if (id > 0) keys.add("i=$id")
                if (imageNumber > 0) keys.add("I=$imageNumber")
                if (placementId > 0) keys.add("p=$placementId")
                val keyStr = if (keys.isNotEmpty()) keys.joinToString(",") + ";" else ";"
                terminalEmulator.sendResponse("\u001B_G$keyStr$message\u001B\\")
            }
        }

        fun sendOk() {
            if (quiet == 0 && (id > 0 || imageNumber > 0)) {
                val keys = mutableListOf<String>()
                if (id > 0) keys.add("i=$id")
                if (imageNumber > 0) keys.add("I=$imageNumber")
                if (placementId > 0) keys.add("p=$placementId")
                val keyStr = if (keys.isNotEmpty()) keys.joinToString(",") + ";" else ";"
                terminalEmulator.sendResponse("\u001B_G${keyStr}OK\u001B\\")
            }
        }

        if (medium != "d") {
            sendError("EBADF:Unsupported transmission medium")
            return
        }

        when (action) {
            'q', 't', 'T' -> {
                var targetImage: TerminalImage? = null
                if (fullPayload.isNotEmpty()) {
                    val rawBytes: ByteArray
                    try {
                        var decoded = Base64Decoder.decode(fullPayload)
                        if (fullHeader["o"] == "z") {
                            decoded = ZlibInflater.inflate(decoded, terminalEmulator.config.maxImageMemoryBytes)
                        }
                        rawBytes = decoded
                    } catch (e: Exception) {
                        sendError("EINVAL:Failed to decode payload: ${e.message}")
                        return
                    }

                    val formatCode = fullHeader["f"]?.toIntOrNull() ?: 32
                    val widthPx: Int
                    val heightPx: Int
                    val rgbaBytes: ByteArray
                    var encodedPng: ByteArray? = null

                    try {
                        when (formatCode) {
                            100 -> {
                                val decodedPng = PngDecoder.decode(
                                    rawBytes,
                                    terminalEmulator.config.maxImageMemoryBytes
                                )
                                widthPx = decodedPng.width
                                heightPx = decodedPng.height
                                rgbaBytes = decodedPng.rgbaBytes
                                encodedPng = rawBytes
                            }
                            32 -> {
                                val s = fullHeader["s"]?.toIntOrNull() ?: 0
                                val v = fullHeader["v"]?.toIntOrNull() ?: 0
                                val totalPixels = s.toLong() * v.toLong()
                                val maxBytes = terminalEmulator.config.maxImageMemoryBytes.toLong()
                                require(
                                    s > 0 &&
                                        v > 0 &&
                                        totalPixels * 4L <= maxBytes &&
                                        rawBytes.size.toLong() >= totalPixels * 4L,
                                ) {
                                    "Invalid RGBA payload dimensions"
                                }
                                widthPx = s
                                heightPx = v
                                rgbaBytes = if (rawBytes.size == (totalPixels * 4L).toInt()) {
                                    rawBytes
                                } else {
                                    rawBytes.copyOf((totalPixels * 4L).toInt())
                                }
                            }
                            24 -> {
                                val s = fullHeader["s"]?.toIntOrNull() ?: 0
                                val v = fullHeader["v"]?.toIntOrNull() ?: 0
                                val totalPixels = s.toLong() * v.toLong()
                                val maxBytes = terminalEmulator.config.maxImageMemoryBytes.toLong()
                                require(
                                    s > 0 &&
                                        v > 0 &&
                                        totalPixels * 4L <= maxBytes &&
                                        rawBytes.size.toLong() >= totalPixels * 3L,
                                ) {
                                    "Invalid RGB payload dimensions"
                                }
                                widthPx = s
                                heightPx = v
                                val pixelCount = totalPixels.toInt()
                                val rgba = ByteArray(pixelCount * 4)
                                for (p in 0 until pixelCount) {
                                    val src = p * 3
                                    val dst = p * 4
                                    rgba[dst] = rawBytes[src]
                                    rgba[dst + 1] = rawBytes[src + 1]
                                    rgba[dst + 2] = rawBytes[src + 2]
                                    rgba[dst + 3] = 0xFF.toByte()
                                }
                                rgbaBytes = rgba
                            }
                            else -> {
                                sendError("EINVAL:Unsupported image format: $formatCode")
                                return
                            }
                        }
                    } catch (e: Exception) {
                        sendError("EINVAL:Invalid image data: ${e.message}")
                        return
                    }

                    if (action == 'q') {
                        sendOk()
                        return
                    }

                    val imageId = if (id > 0) id else screenBuffer.allocateInternalImageId()
                    val version = screenBuffer.nextVersion()
                    val img = TerminalImage(
                        id = imageId,
                        imageNumber = imageNumber,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        rgbaBytes = rgbaBytes,
                        encodedPngBytes = encodedPng,
                        version = version,
                    )
                    if (!screenBuffer.storeImage(img)) {
                        sendError("ENOMEM:Image exceeds maxImageMemoryBytes")
                        return
                    }
                    targetImage = img
                }

                if (action == 't') {
                    sendOk()
                    return
                }

                // If action == 'T'
                if (targetImage == null) {
                    targetImage = if (id > 0) {
                        screenBuffer.getImage(id)
                    } else if (imageNumber > 0) {
                        screenBuffer.findLatestImageByNumber(imageNumber)
                    } else {
                        null
                    }
                }

                if (targetImage == null) {
                    sendError("ENOENT:Image not found")
                    return
                }

                placeKittyImage(targetImage, fullHeader, placementId)
                sendOk()
            }
            'p' -> {
                val targetImage = if (id > 0) {
                    screenBuffer.getImage(id)
                } else if (imageNumber > 0) {
                    screenBuffer.findLatestImageByNumber(imageNumber)
                } else {
                    null
                }
                if (targetImage == null) {
                    sendError("ENOENT:Image not found")
                    return
                }

                placeKittyImage(targetImage, fullHeader, placementId)
                sendOk()
            }
            'd' -> {
                val deleteMode = fullHeader["d"]?.firstOrNull() ?: 'a'
                val x = fullHeader["x"]?.toIntOrNull() ?: 0
                val y = fullHeader["y"]?.toIntOrNull() ?: 0
                val z = fullHeader["z"]?.toIntOrNull() ?: 0
                screenBuffer.deleteKittyGraphics(
                    mode = deleteMode,
                    id = id,
                    imageNumber = imageNumber,
                    placementId = placementId,
                    x = x,
                    y = y,
                    z = z,
                )
            }
        }
    }

    private fun placeKittyImage(
        image: TerminalImage,
        header: Map<String, String>,
        placementId: Int,
    ) {
        val cellW = terminalEmulator.cellWidthPx.coerceAtLeast(1)
        val cellH = terminalEmulator.cellHeightPx.coerceAtLeast(1)

        val srcX = header["x"]?.toIntOrNull() ?: 0
        val srcY = header["y"]?.toIntOrNull() ?: 0
        val srcW = header["w"]?.toIntOrNull() ?: 0
        val srcH = header["h"]?.toIntOrNull() ?: 0
        val cellOffsetX = header["X"]?.toIntOrNull() ?: 0
        val cellOffsetY = header["Y"]?.toIntOrNull() ?: 0
        val zIndex = header["z"]?.toIntOrNull() ?: 0
        val isVirtual = header["U"]?.toIntOrNull() == 1
        val cursorPolicy = header["C"]?.toIntOrNull() ?: 0

        val effWidthPx = if (srcW > 0) srcW else image.widthPx
        val effHeightPx = if (srcH > 0) srcH else image.heightPx

        val cols = (header["c"]?.toIntOrNull() ?: ((effWidthPx + cellW - 1) / cellW)).coerceAtLeast(1)
        val rows = (header["r"]?.toIntOrNull() ?: ((effHeightPx + cellH - 1) / cellH)).coerceAtLeast(1)

        val anchorLine = (if (screenBuffer.isUsingAlternateScreen()) 0 else screenBuffer.scrollbackSize) +
            screenBuffer.cursorRow
        val col = screenBuffer.cursorCol

        val placement = TerminalImagePlacement(
            imageId = image.id,
            placementId = placementId,
            anchorLine = anchorLine,
            col = col,
            cols = cols,
            rows = rows,
            srcX = srcX,
            srcY = srcY,
            srcWidth = srcW,
            srcHeight = srcH,
            cellOffsetX = cellOffsetX,
            cellOffsetY = cellOffsetY,
            zIndex = zIndex,
            isVirtual = isVirtual,
        )
        screenBuffer.addPlacement(placement)

        if (!isVirtual && cursorPolicy == 0) {
            val startCol = col
            repeat(rows - 1) {
                screenBuffer.lineFeed()
            }
            screenBuffer.setCursorPosition(
                screenBuffer.cursorRow,
                (startCol + cols).coerceAtMost(screenBuffer.cols - 1),
                terminalEmulator.originModeEnabled,
            )
        }
    }

    private fun processCharset(char: Char) {
        if (charsetTarget == 0) {
            g0Charset = char
        } else {
            g1Charset = char
        }
        state = State.NORMAL
    }

    private fun parseParamsToBuffer(): Int {
        if (paramsParsed) return paramCount
        paramsParsed = true
        paramCount = 0
        subParamCounts.fill(0)
        if (paramBuffer.isEmpty()) return 0

        var start = 0
        val len = paramBuffer.length
        if (start < len) {
            val first = paramBuffer[start]
            if (first == '?' || first == '>' || first == '<' || first == '=') {
                start++
            }
        }

        var end = start
        while (end < len) {
            val c = paramBuffer[end]
            if (c == ' ' || c == '$' || c == '!') {
                break
            }
            end++
        }

        if (start >= end) {
            return 0
        }

        var paramStart = start
        var paramIdx = 0

        while (paramStart < end && paramIdx < paramValues.size) {
            var nextSemicolon = end
            for (j in paramStart until end) {
                if (paramBuffer[j] == ';') {
                    nextSemicolon = j
                    break
                }
            }

            var tokenStart = paramStart
            var isPrimary = true
            var subIdx = 0

            while (tokenStart <= nextSemicolon) {
                var nextColon = nextSemicolon
                for (j in tokenStart until nextSemicolon) {
                    if (paramBuffer[j] == ':') {
                        nextColon = j
                        break
                    }
                }

                var hasDigit = false
                var num = 0
                for (j in tokenStart until nextColon) {
                    val c = paramBuffer[j]
                    if (c in '0'..'9') {
                        num = num * 10 + (c - '0')
                        hasDigit = true
                    }
                }

                if (isPrimary) {
                    paramValues[paramIdx] = if (hasDigit) num else 0
                    isPrimary = false
                } else {
                    if (subIdx < 8) {
                        subParamValues[paramIdx * 8 + subIdx] = if (hasDigit) num else -1
                        subIdx++
                    }
                }

                if (nextColon == nextSemicolon) {
                    break
                }
                tokenStart = nextColon + 1
            }

            subParamCounts[paramIdx] = subIdx
            paramIdx++

            if (nextSemicolon == end) {
                break
            }
            paramStart = nextSemicolon + 1
            if (paramStart == end && paramIdx < paramValues.size) {
                paramValues[paramIdx] = 0
                subParamCounts[paramIdx] = 0
                paramIdx++
                break
            }
        }

        paramCount = paramIdx
        return paramCount
    }

    private fun parseParam(
        index: Int,
        default: Int,
    ): Int {
        parseParamsToBuffer()
        return if (index < paramCount) paramValues[index] else default
    }

    private fun processSgr(count: Int) {
        if (count == 0) {
            screenBuffer.resetTextAttributes()
            return
        }

        var i = 0
        while (i < count) {
            when (val code = paramValues[i]) {
                0 -> screenBuffer.resetTextAttributes()
                1 -> screenBuffer.setTextAttributes(bold = true)
                2 -> screenBuffer.setTextAttributes(dim = true)
                3 -> screenBuffer.setTextAttributes(italic = true)
                4 -> {
                    if (subParamCounts[i] > 0) {
                        val sub = subParamValues[i * 8].coerceAtLeast(0)
                        val style = UnderlineStyle.fromSubParam(sub)
                        screenBuffer.setTextAttributes(underline = style != UnderlineStyle.NONE, underlineStyle = style)
                    } else {
                        screenBuffer.setTextAttributes(underline = true, underlineStyle = UnderlineStyle.SINGLE)
                    }
                }
                5, 6 -> screenBuffer.setTextAttributes(blink = true)
                7 -> screenBuffer.setTextAttributes(reverse = true)
                8 -> screenBuffer.setTextAttributes(conceal = true)
                9 -> screenBuffer.setTextAttributes(strikethrough = true)
                21 -> screenBuffer.setTextAttributes(underline = true, underlineStyle = UnderlineStyle.DOUBLE)
                22 -> screenBuffer.setTextAttributes(bold = false, dim = false)
                23 -> screenBuffer.setTextAttributes(italic = false)
                24 -> screenBuffer.setTextAttributes(underline = false, underlineStyle = UnderlineStyle.NONE)
                25 -> screenBuffer.setTextAttributes(blink = false)
                27 -> screenBuffer.setTextAttributes(reverse = false)
                28 -> screenBuffer.setTextAttributes(conceal = false)
                29 -> screenBuffer.setTextAttributes(strikethrough = false)
                53 -> screenBuffer.setTextAttributes(overline = true)
                55 -> screenBuffer.setTextAttributes(overline = false)

                in 30..37 ->
                    screenBuffer.setTextAttributes(
                        foreground = TerminalColor.buildStandard(code - 30),
                    )
                in 90..97 ->
                    screenBuffer.setTextAttributes(
                        foreground = TerminalColor.buildStandard(code - 90 + 8),
                    )
                39 -> screenBuffer.setTextAttributes(foreground = TerminalColor.Default)

                in 40..47 ->
                    screenBuffer.setTextAttributes(
                        background = TerminalColor.buildStandard(code - 40),
                    )
                in 100..107 ->
                    screenBuffer.setTextAttributes(
                        background = TerminalColor.buildStandard(code - 100 + 8),
                    )
                49 -> screenBuffer.setTextAttributes(background = TerminalColor.Default)

                38 -> {
                    val result = parseColor(i, count)
                    if (result != null) {
                        screenBuffer.setTextAttributes(foreground = result.first)
                        i += result.second
                    }
                }
                48 -> {
                    val result = parseColor(i, count)
                    if (result != null) {
                        screenBuffer.setTextAttributes(background = result.first)
                        i += result.second
                    }
                }
                58 -> {
                    val result = parseColor(i, count)
                    if (result != null) {
                        screenBuffer.setTextAttributes(underlineColor = result.first)
                        i += result.second
                    }
                }
                59 -> screenBuffer.setTextAttributes(underlineColor = TerminalColor.Default)
            }
            i++
        }
    }

    private fun parseColor(
        i: Int,
        count: Int,
    ): Pair<TerminalColor, Int>? {
        if (subParamCounts[i] > 0) {
            if (subParamValues[i * 8] == 5 && subParamCounts[i] >= 2) {
                val colorCode = subParamValues[i * 8 + 1].coerceAtLeast(0)
                return TerminalColor.buildPalette256(colorCode) to 0
            }
            if (subParamValues[i * 8] == 2) {
                val (r, g, b) = when {
                    subParamCounts[i] >= 5 -> Triple(
                        subParamValues[i * 8 + 2],
                        subParamValues[i * 8 + 3],
                        subParamValues[i * 8 + 4],
                    )
                    subParamCounts[i] == 4 -> Triple(
                        subParamValues[i * 8 + 1],
                        subParamValues[i * 8 + 2],
                        subParamValues[i * 8 + 3],
                    )
                    else -> return null
                }
                return TerminalColor.Rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255),
                ) to 0
            }
            return null
        }
        if (i + 2 < count && paramValues[i + 1] == 5) {
            val colorCode = paramValues[i + 2]
            val color =
                if (colorCode < 16) {
                    TerminalColor.buildStandard(colorCode)
                } else {
                    TerminalColor.buildPalette256(colorCode)
                }
            return color to 2
        }
        if (i + 4 < count && paramValues[i + 1] == 2) {
            val r = paramValues[i + 2]
            val g = paramValues[i + 3]
            val b = paramValues[i + 4]
            return TerminalColor.Rgb(r, g, b) to 4
        }
        return null
    }

    private fun setMode(count: Int) {
        val isPrivate = paramBuffer.isNotEmpty() && paramBuffer[0] == '?'

        if (isPrivate) {
            for (i in 0 until count) {
                when (val mode = paramValues[i]) {
                    1 -> {
                        log("DECCKM", "Enabled")
                        terminalEmulator.applicationCursorKeysEnabled = true
                    }
                    3 -> log("DECCOLM", "132 column mode ignored")
                    4 -> log("DECSCLM", "Smooth scroll mode ignored")
                    5 -> {
                        log("DECSCNM", "Enabled (Reverse Video)")
                        terminalEmulator.invertScreenColors = true
                    }
                    6 -> {
                        log("DECOM", "Enabled (Origin Mode)")
                        terminalEmulator.originModeEnabled = true
                        screenBuffer.setCursorPosition(0, 0, true)
                    }
                    7 -> {
                        log("DECAWM", "Enabled (Auto Wrap)")
                        screenBuffer.setAutoWrapMode(true)
                    }
                    12 -> {
                        log("CursorBlinking", "Enabled")
                        terminalEmulator.cursorBlinking = true
                    }
                    25 -> {
                        log("DECTCEM", "Show Cursor")
                        screenBuffer.cursorVisible = true
                    }
                    47, 1047, 1049 -> {
                        log("Screen", "Use Alternate Buffer ($mode)")
                        screenBuffer.useAlternateScreen()
                    }
                    1000 -> {
                        log("MouseTracking", "Click (1000)")
                        terminalEmulator.mouseTrackingMode = MouseTrackingMode.Click
                    }
                    1002 -> {
                        log("MouseTracking", "CellMotion (1002)")
                        terminalEmulator.mouseTrackingMode = MouseTrackingMode.CellMotion
                    }
                    1003 -> {
                        log("MouseTracking", "AllMotion (1003)")
                        terminalEmulator.mouseTrackingMode = MouseTrackingMode.AllMotion
                    }
                    1004 -> {
                        log("FocusReporting", "Enabled (1004)")
                        terminalEmulator.focusReportingEnabled = true
                    }
                    1006 -> {
                        log("MouseTracking", "SGR Format (1006)")
                        terminalEmulator.sgrMouseModeEnabled = true
                    }
                    2004 -> {
                        log("BracketedPaste", "Enabled")
                        terminalEmulator.bracketedPasteModeEnabled = true
                    }
                    2026 -> {
                        log("SynchronizedOutput", "Enabled (2026)")
                        terminalEmulator.synchronizedOutputEnabled = true
                    }
                    else -> log("UNKNOWN_DEC_SET") { "mode=$mode" }
                }
            }
        } else {
            for (i in 0 until count) {
                when (val mode = paramValues[i]) {
                    4 -> log("IRM", "Insert Mode")
                    20 -> log("LNM", "Line Feed/New Line Mode")
                    else -> log("UNKNOWN_ANSI_SET") { "mode=$mode" }
                }
            }
        }
    }

    private fun resetMode(count: Int) {
        val isPrivate = paramBuffer.isNotEmpty() && paramBuffer[0] == '?'

        if (isPrivate) {
            for (i in 0 until count) {
                when (val mode = paramValues[i]) {
                    1 -> {
                        log("DECCKM", "Disabled")
                        terminalEmulator.applicationCursorKeysEnabled = false
                    }
                    3 -> log("DECCOLM", "80 column mode ignored")
                    4 -> log("DECSCLM", "Jump scroll mode ignored")
                    5 -> {
                        log("DECSCNM", "Disabled (Normal Video)")
                        terminalEmulator.invertScreenColors = false
                    }
                    6 -> {
                        log("DECOM", "Disabled (Absolute Origin)")
                        terminalEmulator.originModeEnabled = false
                        screenBuffer.setCursorPosition(0, 0, false)
                    }
                    7 -> {
                        log("DECAWM", "Disabled (No Auto Wrap)")
                        screenBuffer.setAutoWrapMode(false)
                    }
                    12 -> {
                        log("CursorBlinking", "Disabled")
                        terminalEmulator.cursorBlinking = false
                    }
                    25 -> {
                        log("DECTCEM", "Hide Cursor")
                        screenBuffer.cursorVisible = false
                    }
                    47, 1047, 1049 -> {
                        log("Screen", "Use Primary Buffer ($mode)")
                        screenBuffer.usePrimaryScreen()
                    }
                    1000, 1002, 1003 -> {
                        log("MouseTracking", "Disabled")
                        terminalEmulator.mouseTrackingMode = MouseTrackingMode.None
                    }
                    1004 -> {
                        log("FocusReporting", "Disabled (1004)")
                        terminalEmulator.focusReportingEnabled = false
                    }
                    1006 -> {
                        log("MouseTracking", "SGR Format Disabled (1006)")
                        terminalEmulator.sgrMouseModeEnabled = false
                    }
                    2004 -> {
                        log("BracketedPaste", "Disabled")
                        terminalEmulator.bracketedPasteModeEnabled = false
                    }
                    2026 -> {
                        log("SynchronizedOutput", "Disabled (2026)")
                        terminalEmulator.synchronizedOutputEnabled = false
                    }
                    else -> log("UNKNOWN_DEC_RESET") { "mode=$mode" }
                }
            }
        } else {
            for (i in 0 until count) {
                when (val mode = paramValues[i]) {
                    4 -> log("IRM", "Replace Mode")
                    20 -> log("LNM", "Normal Line Feed")
                    else -> log("UNKNOWN_ANSI_RESET") { "mode=$mode" }
                }
            }
        }
    }
}

private object Base64Decoder {
    private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE_TABLE = IntArray(256) { -1 }.apply {
        for (i in BASE64_CHARS.indices) {
            this[BASE64_CHARS[i].code] = i
        }
        this['='.code] = 0
    }

    fun decode(src: String): ByteArray {
        val cleanSrc = src.filter { it != '\r' && it != '\n' && it != ' ' && it != '\t' }
        if (cleanSrc.isEmpty()) return ByteArray(0)

        var padding = 0
        if (cleanSrc.endsWith("==")) {
            padding = 2
        } else if (cleanSrc.endsWith("=")) {
            padding = 1
        }

        val len = cleanSrc.length
        val byteLen = (len * 6) / 8 - padding
        val result = ByteArray(byteLen)

        var byteIdx = 0
        var i = 0
        while (i < len - 3) {
            val c0 = DECODE_TABLE[cleanSrc[i].code]
            val c1 = DECODE_TABLE[cleanSrc[i + 1].code]
            val c2 = DECODE_TABLE[cleanSrc[i + 2].code]
            val c3 = DECODE_TABLE[cleanSrc[i + 3].code]

            if (c0 == -1 || c1 == -1 || c2 == -1 || c3 == -1) {
                throw IllegalArgumentException("Invalid Base64 character")
            }

            val triplet = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3

            if (byteIdx < byteLen) result[byteIdx++] = ((triplet shl 8) ushr 24).toByte()
            if (byteIdx < byteLen) result[byteIdx++] = ((triplet shl 16) ushr 24).toByte()
            if (byteIdx < byteLen) result[byteIdx++] = ((triplet shl 24) ushr 24).toByte()
            i += 4
        }
        return result
    }
}
