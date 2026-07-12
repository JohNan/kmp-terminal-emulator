package com.sshclient.data.terminal

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min

/**
 * ANSI escape sequence parser
 *
 * Parses VT100/ANSI escape sequences and applies them to a screen buffer.
 * Supports:
 * - CSI sequences (Control Sequence Introducer): ESC [ ...
 * - SGR (Select Graphic Rendition): colors and text attributes
 * - Cursor movement
 * - Screen clearing
 * - Bracketed paste mode
 */
class AnsiParser(
    private val terminalEmulator: TerminalEmulator,
    private val screenBuffer: ScreenBuffer,
    private val logCallback: ((String) -> Unit)? = null,
) {
    // Parser state
    private enum class State {
        NORMAL, // Normal character processing
        ESC, // Just received ESC
        CSI, // In CSI sequence (ESC [)
        OSC, // Operating System Command (ESC ])
        CHARSET, // Charset selection (ESC ( or ESC ))
    }

    private var state = State.NORMAL
    private val paramBuffer = StringBuilder()

    // Leftover bytes from previous chunk for UTF-8 decoding
    private var leftoverBytes = ByteArray(0)

    // Reusable buffers for parameters to avoid allocation
    // Standard sequences usually have few params, but 256-color or custom sequences can have more.
    private val paramValues = IntArray(64)
    private var paramCount = 0
    private var paramsParsed = false

    // Charset selection state
    private var g0Charset = 'B' // Default to ASCII
    private var g1Charset = 'B' // Default to ASCII
    private var activeCharset = 0 // 0 for G0, 1 for G1
    private var charsetTarget = 0 // Used during parsing ESC ( or ESC )

    // Current active charset character
    private val currentCharset: Char get() = if (activeCharset == 0) g0Charset else g1Charset

    // Track the last printable character for CSI b (Repeat Character)
    private var lastPrintableChar: Char? = null
    // Use inline function with lambda for params to avoid String allocation when logging is disabled
    private inline fun log(
        operation: String,
        crossinline params: () -> String,
    ) {
        if (logCallback != null) {
            val now = Clock.System.now()
            val dt = now.toLocalDateTime(TimeZone.currentSystemDefault())
            // Pad to format HH:mm:ss.SSS
            val h = dt.hour.toString().padStart(2, '0')
            val m = dt.minute.toString().padStart(2, '0')
            val s = dt.second.toString().padStart(2, '0')
            val ms = (dt.nanosecond / 1_000_000).toString().padStart(3, '0')
            val timestamp = "$h:$m:$s.$ms"
            val message = "[$timestamp][terminal] $operation ${params()}"
            logCallback.invoke(message)
        }
    }

    // Overload for simple strings to maintain cleaner call sites for static messages
    private fun log(
        operation: String,
        params: String = "",
    ) {
        if (logCallback != null) {
            log(operation) { params }
        }
    }

    /**
     * Process bytes as UTF-8 encoded text
     */
    fun processBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return

        // Prepend leftover bytes from previous chunk
        val combinedBytes = if (leftoverBytes.isNotEmpty()) {
            val combined = ByteArray(leftoverBytes.size + bytes.size)
            leftoverBytes.copyInto(combined, 0)
            bytes.copyInto(combined, leftoverBytes.size)
            leftoverBytes = ByteArray(0)
            combined
        } else {
            bytes
        }

        // Find complete UTF-8 sequence length
        var validLen = combinedBytes.size
        // A UTF-8 character can be up to 4 bytes long. Check the last up to 3 bytes to see if they are part of an incomplete character.
        for (i in 1..min(3, combinedBytes.size)) {
            val byte = combinedBytes[combinedBytes.size - i].toInt()
            // If it's a leading byte and it indicates more bytes than are present
            if ((byte and 0xC0) == 0xC0) {
                val expectedLen = when {
                    (byte and 0xE0) == 0xC0 -> 2
                    (byte and 0xF0) == 0xE0 -> 3
                    (byte and 0xF8) == 0xF0 -> 4
                    else -> 1 // Invalid, let it be handled by decodeToString (which replaces with replacement char)
                }
                if (expectedLen > i) {
                    // Incomplete sequence found
                    validLen = combinedBytes.size - i
                    leftoverBytes = combinedBytes.copyOfRange(validLen, combinedBytes.size)
                }
                break
            }
        }

        if (validLen == 0) return

        // Decode valid bytes
        val text = combinedBytes.decodeToString(endIndex = validLen)

        // Process decoded characters
        var charIndex = 0
        while (charIndex < text.length) {
            if (state == State.NORMAL) {
                var count = 0
                val charset = currentCharset

                // Look ahead for printable characters
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
                        // Apply DEC Special Graphics mapping
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

    /**
     * Map a character to line drawing character if applicable
     */
    private fun mapCharset(char: Char, charset: Char): Char {
        if (charset != '0') return char // Only map for DEC Special Graphics
        return when (char) {
            'j' -> '┘' // lower right corner
            'k' -> '┐' // upper right corner
            'l' -> '┌' // upper left corner
            'm' -> '└' // lower left corner
            'n' -> '┼' // crossing lines
            'q' -> '─' // horizontal line
            't' -> '├' // left T
            'u' -> '┤' // right T
            'v' -> '┴' // bottom T
            'w' -> '┬' // top T
            'x' -> '│' // vertical line
            'a' -> '▒' // checkerboard
            else -> char
        }
    }

    private fun isPrintable(char: Char): Boolean {
        return (char in '\u0020'..'\u007E') || (char in '\u00A0'..'\uFFFF')
    }

    /**
     * Process a single character
     */
    private fun processChar(char: Char) {
        when (state) {
            State.NORMAL -> processNormal(char)
            State.ESC -> processEscape(char)
            State.CSI -> processCsi(char)
            State.OSC -> processOsc(char)
            State.CHARSET -> processCharset(char)
        }
    }

    /**
     * Process normal character
     */
    private fun processNormal(char: Char) {
        when (char) {
            '\u001B' -> { // ESC
                state = State.ESC
                paramBuffer.clear()
                paramsParsed = false
            }
            '\r' -> screenBuffer.carriageReturn()
            '\n' -> screenBuffer.lineFeed()
            '\b' -> screenBuffer.moveCursor(0, -1) // Backspace
            '\t' -> {
                // Tab: move to next tab stop (every 8 columns)
                val nextTab = ((screenBuffer.cursorCol / 8) + 1) * 8
                screenBuffer.setCursorPosition(screenBuffer.cursorRow, nextTab, terminalEmulator.originModeEnabled)
            }
            '\u000E' -> {
                // Shift Out (LS1) -> invoke G1
                activeCharset = 1
            }
            '\u000F' -> {
                // Shift In (LS0) -> invoke G0
                activeCharset = 0
            }
            '\u0007' -> { // Bell
                terminalEmulator.triggerBell()
            }
            in '\u0020'..'\u007E', in '\u00A0'..'\uFFFF' -> {
                // Printable characters (ASCII + extended Unicode)
                lastPrintableChar = mapCharset(char, currentCharset)
                screenBuffer.writeChar(lastPrintableChar!!)
            }
            // Ignore other control characters
        }
    }

    /**
     * Process escape sequence
     */
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
                // Reverse index (move up, scroll if at top)
                log("RI", "Reverse Index")
                screenBuffer.cursorUp()
                state = State.NORMAL
            }
            'D' -> {
                // Index (move down, scroll if at bottom)
                log("IND", "Index")
                screenBuffer.lineFeed()
                state = State.NORMAL
            }
            'E' -> {
                // Next line
                log("NEL", "Next Line")
                screenBuffer.carriageReturn()
                screenBuffer.lineFeed()
                state = State.NORMAL
            }
            '7' -> {
                // Save cursor position
                log("DECSC", "Save Cursor")
                screenBuffer.saveCursor()
                state = State.NORMAL
            }
            '8' -> {
                // Restore cursor position
                log("DECRC", "Restore Cursor")
                screenBuffer.restoreCursor()
                state = State.NORMAL
            }
            'c' -> {
                // Reset terminal
                log("RIS", "Reset Initial State")
                screenBuffer.clearScreen()
                screenBuffer.setCursorPosition(0, 0)
                screenBuffer.resetTextAttributes()
                state = State.NORMAL
            }
            '=' -> { // Application Keypad Mode
                log("DECKPAM", "Enabled")
                terminalEmulator.applicationKeypadModeEnabled = true
                state = State.NORMAL
            }
            '>' -> { // Normal Keypad Mode
                log("DECKPNM", "Disabled (Normal)")
                terminalEmulator.applicationKeypadModeEnabled = false
                state = State.NORMAL
            }
            else -> {
                // Unknown escape sequence, return to normal
                log("UNKNOWN_ESC") { "char=$char" }
                state = State.NORMAL
            }
        }
    }

    /**
     * Process CSI sequence (ESC [)
     */
    private fun processCsi(char: Char) {
        when (char) {
            in '0'..'9', ';', '?' -> {
                // Accumulate parameters
                paramBuffer.append(char)
            }
            'd' -> { // VPA - Vertical Line Position Absolute
                val row = parseParam(0, 1) - 1
                log("VPA", "row=${row + 1}")
                // Set row, keep current col
                screenBuffer.setCursorPosition(row, screenBuffer.cursorCol, terminalEmulator.originModeEnabled)
                state = State.NORMAL
            }
            'G' -> { // CHA - Cursor Horizontal Absolute
                val col = parseParam(0, 1) - 1
                log("CHA", "col=${col + 1}")
                // Keep current row, set col
                screenBuffer.setCursorPosition(screenBuffer.cursorRow, col, terminalEmulator.originModeEnabled)
                state = State.NORMAL
            }
            't' -> { // Window manipulation
                val mode = parseParam(0, 0)
                log("WindowManipulation") { "mode=$mode" }
                when (mode) {
                    14 -> { // Report text area size in pixels
                        // We don't have exact pixel sizes easily available in TerminalEmulator without Compose context.
                        // Standard terminal fallback is reporting character size multiplied by a typical font size,
                        // or just returning empty/ignored. Let's send a basic response if requested.
                        // Format: CSI 4 ; height ; width t
                        terminalEmulator.sendResponse("\u001B[4;${screenBuffer.rows * 16};${screenBuffer.cols * 8}t")
                    }
                    18 -> { // Report text area size in characters
                        // Format: CSI 8 ; rows ; cols t
                        terminalEmulator.sendResponse("\u001B[8;${screenBuffer.rows};${screenBuffer.cols}t")
                    }
                    22 -> { // Save window title
                        terminalEmulator.pushWindowTitle()
                    }
                    23 -> { // Restore window title
                        terminalEmulator.popWindowTitle()
                    }
                }
                state = State.NORMAL
            }
            'A' -> { // Cursor up
                val count = parseParam(0, 1)
                log("CUU") { "count=$count" }
                screenBuffer.cursorUp(count)
                state = State.NORMAL
            }
            'B' -> { // Cursor down
                val count = parseParam(0, 1)
                log("CUD") { "count=$count" }
                screenBuffer.cursorDown(count)
                state = State.NORMAL
            }
            'C' -> { // Cursor forward (right)
                val count = parseParam(0, 1)
                log("CUF") { "count=$count" }
                screenBuffer.cursorForward(count)
                state = State.NORMAL
            }
            'D' -> { // Cursor backward (left)
                val count = parseParam(0, 1)
                log("CUB") { "count=$count" }
                screenBuffer.cursorBackward(count)
                state = State.NORMAL
            }
            'S' -> { // Scroll Up
                val lines = parseParam(0, 1)
                log("SU") { "lines=$lines" }
                screenBuffer.scrollUp(lines)
                state = State.NORMAL
            }
            'T' -> { // Scroll Down
                val lines = parseParam(0, 1)
                log("SD") { "lines=$lines" }
                screenBuffer.scrollDown(lines)
                state = State.NORMAL
            }
            'b' -> { // Repeat Character
                val count = parseParam(0, 1)
                val charToRepeat = lastPrintableChar ?: ' '
                log("REP") { "char='$charToRepeat', count=$count" }
                repeat(count) { screenBuffer.writeChar(charToRepeat) }
                state = State.NORMAL
            }
            'H', 'f' -> { // Cursor position (row;col)
                parseParamsToBuffer()
                val row = (if (paramCount > 0) paramValues[0] else 1) - 1
                val col = (if (paramCount > 1) paramValues[1] else 1) - 1
                log("CUP") { "row=${row + 1}, col=${col + 1}, originMode=${terminalEmulator.originModeEnabled}" }
                screenBuffer.setCursorPosition(row, col, terminalEmulator.originModeEnabled)
                state = State.NORMAL
            }
            'c' -> { // DA - Primary Device Attributes
                val param = parseParam(0, 0)
                log("DA") { "param=$param" }
                if (param == 0) {
                    // Respond with VT100/VT220 terminal capability
                    terminalEmulator.sendResponse("\u001B[?1;0c")
                }
                state = State.NORMAL
            }
            'n' -> { // DSR - Device Status Report
                val param = parseParam(0, 0)
                log("DSR") { "param=$param" }
                if (param == 6) {
                    // Cursor Position Report (CPR)
                    // Terminal responds with CSI row ; col R (1-based indices)
                    val row = screenBuffer.cursorRow + 1
                    val col = screenBuffer.cursorCol + 1
                    terminalEmulator.sendResponse("\u001B[$row;${col}R")
                }
                state = State.NORMAL
            }
            'J' -> { // Erase in display
                val mode = parseParam(0, 0)
                log("ED") { "mode=$mode, cursor=${screenBuffer.cursorRow + 1}:${screenBuffer.cursorCol + 1}" }
                when (mode) {
                    0 -> screenBuffer.clearToEndOfScreen()
                    1 -> screenBuffer.clearToStartOfScreen()
                    2, 3 -> screenBuffer.clearScreen()
                }
                state = State.NORMAL
            }
            'K' -> { // Erase in line
                val mode = parseParam(0, 0)
                log("EL") { "mode=$mode, cursor=${screenBuffer.cursorRow + 1}:${screenBuffer.cursorCol + 1}" }
                when (mode) {
                    0 -> screenBuffer.clearToEndOfLine()
                    1 -> screenBuffer.clearToStartOfLine()
                    2 -> screenBuffer.clearLine()
                }
                state = State.NORMAL
            }
            'm' -> { // SGR - Select Graphic Rendition
                parseParamsToBuffer()
                // SGR is very frequent, maybe don't log every single one unless debugging
                // processSgr(paramCount)
                // log("SGR") { "params=${formatParams(paramCount)}" }
                processSgr(paramCount)
                state = State.NORMAL
            }
            'h' -> { // Set mode
                parseParamsToBuffer()
                setMode(paramCount)
                state = State.NORMAL
            }
            'l' -> { // Reset mode
                parseParamsToBuffer()
                resetMode(paramCount)
                state = State.NORMAL
            }
            's' -> { // Save cursor position
                log("SCOSC", "Save Cursor")
                screenBuffer.saveCursor()
                state = State.NORMAL
            }
            'u' -> { // Restore cursor position
                log("SCORC", "Restore Cursor")
                screenBuffer.restoreCursor()
                state = State.NORMAL
            }
            'r' -> { // DECSTBM - Set scrolling region (top;bottom)
                parseParamsToBuffer()
                val top = (if (paramCount > 0) paramValues[0] else 1) - 1 // Convert to 0-indexed
                val bottom = (if (paramCount > 1) paramValues[1] else screenBuffer.rows) - 1

                log("DECSTBM") { "top=${top + 1}, bottom=${bottom + 1}, bufferRows=${screenBuffer.rows}" }

                // Validate parameters: top must be less than bottom
                if (top >= 0 && bottom < screenBuffer.rows && top < bottom) {
                    screenBuffer.setScrollingRegion(top, bottom)
                    // DECSTBM also moves cursor to 1,1
                    screenBuffer.setCursorPosition(0, 0, terminalEmulator.originModeEnabled)
                } else {
                    // Invalid parameters: reset to full screen
                    screenBuffer.resetScrollingRegion()
                }
                state = State.NORMAL
            }
            'L' -> { // IL - Insert lines
                val count = parseParam(0, 1)
                log("IL") { "count=$count" }
                screenBuffer.insertLines(count)
                state = State.NORMAL
            }
            'M' -> { // DL - Delete lines
                val count = parseParam(0, 1)
                log("DL") { "count=$count" }
                screenBuffer.deleteLines(count)
                state = State.NORMAL
            }
            'P' -> { // DCH - Delete Character
                val count = parseParam(0, 1)
                log("DCH") { "count=$count" }
                screenBuffer.deleteCharacters(count)
                state = State.NORMAL
            }
            '@' -> { // ICH - Insert Character
                val count = parseParam(0, 1)
                log("ICH") { "count=$count" }
                screenBuffer.insertCharacters(count)
                state = State.NORMAL
            }
            'X' -> { // ECH - Erase Character
                val count = parseParam(0, 1)
                log("ECH") { "count=$count" }
                screenBuffer.eraseCharacters(count)
                state = State.NORMAL
            }
            else -> {
                // Unknown CSI sequence, return to normal
                log("UNKNOWN_CSI") { "char=$char, params=$paramBuffer" }
                state = State.NORMAL
            }
        }
    }

    /**
     * Process OSC (Operating System Command) sequence
     */
    private fun processOsc(char: Char) {
        when (char) {
            '\u0007', '\u001B' -> { // BEL or ESC terminates OSC
                // Note: ESC \ is the ST (String Terminator), but we just trigger on ESC for simplicity
                val oscData = paramBuffer.toString()
                val semicolonIndex = oscData.indexOf(';')

                if (semicolonIndex != -1) {
                    val command = oscData.substring(0, semicolonIndex)
                    val value = oscData.substring(semicolonIndex + 1)

                    when (command) {
                        "52" -> { // OSC 52: Manipulate Selection Data (Clipboard)
                            val parts = value.split(";")
                            if (parts.size == 2) {
                                val pd = parts[1]
                                if (pd == "?") {
                                    // Read request: silently ignore for security
                                    log("OSC 52", "Read request ignored for security")
                                } else {
                                    try {
                                        val decodedBytes = Base64Decoder.decode(pd)
                                        val decodedString = decodedBytes.decodeToString()
                                        terminalEmulator.copyToClipboard(decodedString)
                                        log("OSC 52", "Copied to clipboard")
                                    } catch (e: Exception) {
                                        log("OSC 52", "Failed to decode base64 payload: ${e.message}")
                                    }
                                }
                            } else {
                                log("OSC 52", "Invalid payload format")
                            }
                        }

                        "0", "2" -> { // Set window title
                            terminalEmulator.setWindowTitle(value)
                            log("OSC", "Set window title: $value")
                        }
                    }
                }

                // If it was ESC, we might be expecting a '\' next, but we just return to NORMAL
                // and the next '\' will be processed as a normal char (or ignored if we handled it properly).
                // Usually ST is ESC \. If we just saw ESC, we should transition to ESC state, not NORMAL.
                if (char == '\u001B') {
                    state = State.ESC
                    paramBuffer.clear()
                    paramsParsed = false
                } else {
                    state = State.NORMAL
                }
            }
            else -> {
                // Accumulate OSC data
                paramBuffer.append(char)
            }
        }
    }

    /**
     * Process charset selection
     */
    private fun processCharset(char: Char) {
        // Charset selection (ESC ( or ESC ))
        if (charsetTarget == 0) {
            g0Charset = char
        } else {
            g1Charset = char
        }
        state = State.NORMAL
    }

    /**
     * Parse parameters from buffer into internal array to avoid allocation.
     * @return Number of parameters parsed
     */
    private fun parseParamsToBuffer(): Int {
        if (paramsParsed) return paramCount
        paramsParsed = true
        paramCount = 0
        if (paramBuffer.isEmpty()) return 0

        var currentVal = 0
        var hasDigit = false
        var i = 0

        // Skip private mode prefix if present
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
                // Reset for next parameter
                currentVal = 0
                hasDigit = false
            }
            i++
        }

        // Add the last value if it exists
        if (hasDigit) {
            if (paramCount < paramValues.size) {
                paramValues[paramCount++] = currentVal
            }
        }

        return paramCount
    }

    /**
     * Parse a single parameter at index, with default value
     */
    private fun parseParam(
        index: Int,
        default: Int,
    ): Int {
        parseParamsToBuffer()
        return if (index < paramCount) paramValues[index] else default
    }

    /**
     * Helper to format parameters for logging without creating a list
     */
    private fun formatParams(count: Int): String {
        if (count == 0) return "[]"
        val sb = StringBuilder("[")
        for (i in 0 until count) {
            if (i > 0) sb.append(", ")
            sb.append(paramValues[i])
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Process SGR (Select Graphic Rendition) parameters
     * These control text colors and attributes
     */
    private fun processSgr(count: Int) {
        if (count == 0) {
            // Empty SGR resets all attributes
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

                // Foreground colors (30-37: standard, 90-97: bright)
                in 30..37 ->
                    screenBuffer.setTextAttributes(
                        foreground = TerminalColor.buildStandard(code - 30),
                    )
                in 90..97 ->
                    screenBuffer.setTextAttributes(
                        foreground = TerminalColor.buildStandard(code - 90 + 8),
                    )
                39 -> screenBuffer.setTextAttributes(foreground = TerminalColor.Default)

                // Background colors (40-47: standard, 100-107: bright)
                in 40..47 ->
                    screenBuffer.setTextAttributes(
                        background = TerminalColor.buildStandard(code - 40),
                    )
                in 100..107 ->
                    screenBuffer.setTextAttributes(
                        background = TerminalColor.buildStandard(code - 100 + 8),
                    )
                49 -> screenBuffer.setTextAttributes(background = TerminalColor.Default)

                // 256-color mode
                38 -> {
                    // Foreground 256-color: 38;5;N or 38;2;R;G;B
                    val result = parseColor(i, count)
                    if (result != null) {
                        screenBuffer.setTextAttributes(foreground = result.first)
                        i += result.second
                    }
                }
                48 -> {
                    // Background 256-color: 48;5;N or 48;2;R;G;B
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

    /**
     * Parse color from SGR parameters (256-color or RGB)
     * @return Pair of TerminalColor and number of parameters consumed, or null if invalid
     */
    private fun parseColor(
        i: Int,
        count: Int,
    ): Pair<TerminalColor, Int>? {
        // 256-color palette: 5;N
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
        // True color RGB: 2;R;G;B
        if (i + 4 < count && paramValues[i + 1] == 2) {
            val r = paramValues[i + 2]
            val g = paramValues[i + 3]
            val b = paramValues[i + 4]
            return TerminalColor.Rgb(r, g, b) to 4
        }
        return null
    }

    /**
     * Set terminal mode
     */
    private fun setMode(count: Int) {
        // Check if this is a private mode (starts with ?)
        val isPrivate = paramBuffer.isNotEmpty() && paramBuffer[0] == '?'

        if (isPrivate) {
            // Private modes (DEC modes)
            for (i in 0 until count) {
                when (val mode = paramValues[i]) {
                    1 -> {
                        log("DECCKM", "Enabled")
                        terminalEmulator.applicationCursorKeysEnabled = true
                    }
                    3 -> log("DECCOLM", "132 column mode ignored") // 132 column mode (DECCOLM) - ignore
                    4 -> log("DECSCLM", "Smooth scroll mode ignored") // Smooth scroll (DECSCLM) - ignore
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
                    47 -> {
                        log("Screen", "Use Alternate Buffer (47)")
                        screenBuffer.useAlternateScreen()
                    }
                    1047 -> {
                        log("Screen", "Use Alternate Buffer (1047)")
                        screenBuffer.useAlternateScreen()
                    }
                    1049 -> {
                        log("Screen", "Use Alternate Buffer + Save Cursor (1049)")
                        // Switch to alternate screen (which internally saves cursor)
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
            // Standard ANSI modes
            for (i in 0 until count) {
                when (val mode = paramValues[i]) {
                    4 -> log("IRM", "Insert Mode")
                    20 -> log("LNM", "Line Feed/New Line Mode")
                    else -> log("UNKNOWN_ANSI_SET") { "mode=$mode" }
                }
            }
        }
    }

    /**
     * Reset terminal mode
     */
    private fun resetMode(count: Int) {
        // Check if this is a private mode (starts with ?)
        val isPrivate = paramBuffer.isNotEmpty() && paramBuffer[0] == '?'

        if (isPrivate) {
            // Private modes (DEC modes)
            for (i in 0 until count) {
                when (val mode = paramValues[i]) {
                    1 -> {
                        log("DECCKM", "Disabled")
                        terminalEmulator.applicationCursorKeysEnabled = false
                    }
                    3 -> log("DECCOLM", "80 column mode ignored") // 80 column mode - ignore
                    4 -> log("DECSCLM", "Jump scroll mode ignored") // Jump scroll - ignore
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
                    47 -> {
                        log("Screen", "Use Primary Buffer (47)")
                        screenBuffer.usePrimaryScreen()
                    }
                    1047 -> {
                        log("Screen", "Use Primary Buffer (1047)")
                        screenBuffer.usePrimaryScreen()
                    }
                    1049 -> {
                        log("Screen", "Use Primary Buffer + Restore Cursor (1049)")
                        // Switch to primary screen and restore cursor
                        screenBuffer.usePrimaryScreen()
                        // screenBuffer.restoreCursor() // ScreenBuffer.usePrimaryScreen already restores cursor if it was saved by useAlternateScreen?
                        // ScreenBuffer.useAlternateScreen saves cursor. usePrimaryScreen restores it.
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
            // Standard ANSI modes
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
        if (cleanSrc.endsWith("==")) padding = 2
        else if (cleanSrc.endsWith("=")) padding = 1

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
