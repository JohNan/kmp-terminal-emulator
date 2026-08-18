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
        CHARSET,
    }

    private var state = State.NORMAL
    private val paramBuffer = StringBuilder()
    private var leftoverBytes = ByteArray(0)

    private val paramValues = IntArray(64)
    private var paramCount = 0
    private var paramsParsed = false

    private var g0Charset = 'B'
    private var g1Charset = 'B'
    private var activeCharset = 0
    private var charsetTarget = 0

    private val currentCharset: Char get() = if (activeCharset == 0) g0Charset else g1Charset
    private var lastPrintableChar: Char? = null

    companion object {
        const val MAX_OSC52_PAYLOAD_SIZE = 65536
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
    fun processBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return

        val combinedBytes = if (leftoverBytes.isNotEmpty()) {
            val combined = ByteArray(leftoverBytes.size + bytes.size)
            leftoverBytes.copyInto(combined, 0)
            bytes.copyInto(combined, leftoverBytes.size)
            leftoverBytes = ByteArray(0)
            combined
        } else {
            bytes
        }

        var validLen = combinedBytes.size
        for (i in 1..min(3, combinedBytes.size)) {
            val byte = combinedBytes[combinedBytes.size - i].toInt()
            if ((byte and 0xC0) == 0xC0) {
                val expectedLen = when {
                    (byte and 0xE0) == 0xC0 -> 2
                    (byte and 0xF0) == 0xE0 -> 3
                    (byte and 0xF8) == 0xF0 -> 4
                    else -> 1
                }
                if (expectedLen > i) {
                    validLen = combinedBytes.size - i
                    leftoverBytes = combinedBytes.copyOfRange(validLen, combinedBytes.size)
                }
                break
            }
        }

        if (validLen == 0) return

        val text = combinedBytes.decodeToString(endIndex = validLen)
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

                if (count > 0) {
                    val substring = text.substring(charIndex, charIndex + count)
                    if (charset == '0') {
                        val mapped = CharArray(substring.length)
                        for (i in substring.indices) {
                            mapped[i] = mapCharset(substring[i], charset)
                        }
                        val mappedString = mapped.concatToString()
                        screenBuffer.writeText(mappedString)
                        lastPrintableChar = mapped.lastOrNull()
                    } else {
                        screenBuffer.writeText(substring)
                        lastPrintableChar = substring.lastOrNull()
                    }

                    charIndex += count
                    continue
                }
            }
            processChar(text[charIndex])
            charIndex++
        }
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

    private fun isPrintable(char: Char): Boolean {
        return (char in '\u0020'..'\u007E') || (char in '\u00A0'..'\uFFFF')
    }

    private fun processChar(char: Char) {
        when (state) {
            State.NORMAL -> processNormal(char)
            State.ESC -> processEscape(char)
            State.CSI -> processCsi(char)
            State.OSC -> processOsc(char)
            State.CHARSET -> processCharset(char)
        }
    }

    private fun processNormal(char: Char) {
        when (char) {
            '\u001B' -> {
                state = State.ESC
                paramBuffer.clear()
                paramsParsed = false
            }
            '\r' -> screenBuffer.carriageReturn()
            '\n' -> screenBuffer.lineFeed()
            '\b' -> screenBuffer.moveCursor(0, -1)
            '\t' -> {
                val nextTab = ((screenBuffer.cursorCol / 8) + 1) * 8
                screenBuffer.setCursorPosition(screenBuffer.cursorRow, nextTab, terminalEmulator.originModeEnabled)
            }
            '\u000E' -> activeCharset = 1
            '\u000F' -> activeCharset = 0
            '\u0007' -> terminalEmulator.triggerBell()
            in '\u0020'..'\u007E', in '\u00A0'..'\uFFFF' -> {
                lastPrintableChar = mapCharset(char, currentCharset)
                screenBuffer.writeChar(lastPrintableChar!!)
            }
        }
    }

    private fun processEscape(char: Char) {
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
            in '0'..'9', ';', '?' -> {
                paramBuffer.append(char)
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
                    14 -> terminalEmulator.sendResponse("\u001B[4;${screenBuffer.rows * 16};${screenBuffer.cols * 8}t")
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
                val charToRepeat = lastPrintableChar ?: ' '
                log("REP") { "char='$charToRepeat', count=$count" }
                repeat(count) { screenBuffer.writeChar(charToRepeat) }
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
                log("DA") { "param=$param" }
                if (param == 0) {
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
            else -> {
                log("UNKNOWN_CSI") { "char=$char, params=$paramBuffer" }
                state = State.NORMAL
            }
        }
    }

    private fun processOsc(char: Char) {
        when (char) {
            '\u0007', '\u001B' -> {
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
                    }
                }

                if (char == '\u001B') {
                    state = State.ESC
                    paramBuffer.clear()
                    paramsParsed = false
                } else {
                    state = State.NORMAL
                }
            }
            else -> {
                paramBuffer.append(char)
            }
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
        if (paramBuffer.isEmpty()) return 0

        var currentVal = 0
        var hasDigit = false
        var i = 0

        if (paramBuffer.isNotEmpty() && paramBuffer[0] == '?') {
            i++
        }

        while (i < paramBuffer.length) {
            val c = paramBuffer[i]
            if (c in '0'..'9') {
                currentVal = currentVal * 10 + (c - '0')
                hasDigit = true
            } else if (c == ';') {
                if (hasDigit) {
                    if (paramCount < paramValues.size) {
                        paramValues[paramCount++] = currentVal
                    }
                }
                currentVal = 0
                hasDigit = false
            }
            i++
        }

        if (hasDigit) {
            if (paramCount < paramValues.size) {
                paramValues[paramCount++] = currentVal
            }
        }

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
                4 -> screenBuffer.setTextAttributes(underline = true)
                5, 6 -> screenBuffer.setTextAttributes(blink = true)
                7 -> screenBuffer.setTextAttributes(reverse = true)
                8 -> screenBuffer.setTextAttributes(conceal = true)
                9 -> screenBuffer.setTextAttributes(strikethrough = true)
                22 -> screenBuffer.setTextAttributes(bold = false, dim = false)
                23 -> screenBuffer.setTextAttributes(italic = false)
                24 -> screenBuffer.setTextAttributes(underline = false)
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
            }
            i++
        }
    }

    private fun parseColor(
        i: Int,
        count: Int,
    ): Pair<TerminalColor, Int>? {
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
                    1006 -> {
                        log("MouseTracking", "SGR Format (1006)")
                        terminalEmulator.sgrMouseModeEnabled = true
                    }
                    2004 -> {
                        log("BracketedPaste", "Enabled")
                        terminalEmulator.bracketedPasteModeEnabled = true
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
                    1006 -> {
                        log("MouseTracking", "SGR Format Disabled (1006)")
                        terminalEmulator.sgrMouseModeEnabled = false
                    }
                    2004 -> {
                        log("BracketedPaste", "Disabled")
                        terminalEmulator.bracketedPasteModeEnabled = false
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
