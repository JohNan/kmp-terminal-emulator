package com.johnan.terminal.tui

/**
 * Functional keys supported by TUI applications.
 */
enum class TuiKey {
    CHAR,
    ENTER,
    BACKSPACE,
    DELETE,
    TAB,
    ESCAPE,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN
}

/**
 * High-level TUI input events parsed from ANSI / VT byte streams.
 */
sealed interface TuiEvent {
    data class Key(
        val key: TuiKey,
        val char: Char? = null,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false,
        val eventType: Int = 1,
    ) : TuiEvent

    data class Mouse(
        val button: Int,
        val col: Int,
        val row: Int,
        val released: Boolean,
        val wheelUp: Boolean = false,
        val wheelDown: Boolean = false
    ) : TuiEvent

    data class Paste(
        val text: String
    ) : TuiEvent
}

/**
 * Parser for converting raw terminal byte streams into [TuiEvent] instances
 * and encoding [TuiEvent.Key] events into standard ANSI sequences.
 */
class TuiInputParser {
    /**
     * Parses a byte array chunk into a list of [TuiEvent] instances.
     */
    fun parse(bytes: ByteArray, length: Int = bytes.size): List<TuiEvent> {
        val events = mutableListOf<TuiEvent>()
        var i = 0
        val len = minOf(length, bytes.size)

        while (i < len) {
            val b = bytes[i].toInt() and 0xFF

            if (b == 0x1B) { // Escape
                if (i + 1 >= len) {
                    events.add(TuiEvent.Key(TuiKey.ESCAPE))
                    i++
                    continue
                }

                val b2 = bytes[i + 1].toInt() and 0xFF
                if (b2 == '['.code) { // CSI
                    // Check Bracketed Paste: \u001b[200~ ... \u001b[201~
                    if (i + 6 <= len && bytes.decodeToString(i, i + 6) == "\u001b[200~") {
                        val endMarker = "\u001b[201~"
                        var foundEnd = -1
                        for (k in i + 6..len - 6) {
                            if (bytes.decodeToString(k, k + 6) == endMarker) {
                                foundEnd = k
                                break
                            }
                        }

                        if (foundEnd != -1) {
                            val text = bytes.decodeToString(i + 6, foundEnd)
                            events.add(TuiEvent.Paste(text))
                            i = foundEnd + 6
                        } else {
                            val text = bytes.decodeToString(i + 6, len)
                            events.add(TuiEvent.Paste(text))
                            i = len
                        }
                        continue
                    }

                    // Check SGR Mouse: \u001b[<btn;col;rowM or m
                    if (i + 2 < len && bytes[i + 2].toInt() == '<'.code) {
                        var j = i + 3
                        while (j < len && (bytes[j].toInt() in '0'.code..'9'.code || bytes[j].toInt() == ';'.code)) {
                            j++
                        }
                        if (j < len && (bytes[j].toInt() == 'M'.code || bytes[j].toInt() == 'm'.code)) {
                            val term = bytes[j].toInt().toChar()
                            val parts = bytes.decodeToString(i + 3, j).split(';')
                            if (parts.size == 3) {
                                val btn = parts[0].toIntOrNull() ?: 0
                                val col = (parts[1].toIntOrNull() ?: 1) - 1
                                val row = (parts[2].toIntOrNull() ?: 1) - 1
                                val released = term == 'm'
                                val wheelUp = btn == 64
                                val wheelDown = btn == 65
                                events.add(
                                    TuiEvent.Mouse(
                                        button = btn,
                                        col = maxOf(0, col),
                                        row = maxOf(0, row),
                                        released = released,
                                        wheelUp = wheelUp,
                                        wheelDown = wheelDown
                                    )
                                )
                                i = j + 1
                                continue
                            }
                        }
                    }

                    // Other CSI sequences
                    var j = i + 2
                    while (j < len && (bytes[j].toInt() and 0xFF) in 0x20..0x3F) {
                        j++
                    }
                    if (j < len) {
                        val finalChar = (bytes[j].toInt() and 0xFF).toChar()
                        val paramStr = bytes.decodeToString(i + 2, j)
                        val paramGroups = if (paramStr.isEmpty()) emptyList() else paramStr.split(';')
                        val keySubParams = paramGroups.getOrNull(0)?.split(':') ?: emptyList()
                        val firstParam = keySubParams.getOrNull(0)?.toIntOrNull() ?: 0
                        val shiftedParam = keySubParams.getOrNull(1)?.toIntOrNull()

                        val modSubParams = paramGroups.getOrNull(1)?.split(':') ?: emptyList()
                        val modVal = modSubParams.getOrNull(0)?.toIntOrNull() ?: 1
                        val eventType = modSubParams.getOrNull(1)?.toIntOrNull() ?: 1

                        var shift = false
                        var alt = false
                        var ctrl = false
                        if (paramGroups.size >= 2) {
                            val mod = modVal - 1
                            shift = (mod and 1) != 0
                            alt = (mod and 2) != 0
                            ctrl = (mod and 4) != 0
                        }

                        val keyEvent = when (finalChar) {
                            'A' -> TuiEvent.Key(
                                TuiKey.UP,
                                shift = shift,
                                alt = alt,
                                ctrl = ctrl,
                                eventType = eventType,
                            )
                            'B' -> TuiEvent.Key(
                                TuiKey.DOWN,
                                shift = shift,
                                alt = alt,
                                ctrl = ctrl,
                                eventType = eventType,
                            )
                            'C' -> TuiEvent.Key(
                                TuiKey.RIGHT,
                                shift = shift,
                                alt = alt,
                                ctrl = ctrl,
                                eventType = eventType,
                            )
                            'D' -> TuiEvent.Key(
                                TuiKey.LEFT,
                                shift = shift,
                                alt = alt,
                                ctrl = ctrl,
                                eventType = eventType,
                            )
                            'H' -> TuiEvent.Key(
                                TuiKey.HOME,
                                shift = shift,
                                alt = alt,
                                ctrl = ctrl,
                                eventType = eventType,
                            )
                            'F' -> TuiEvent.Key(
                                TuiKey.END,
                                shift = shift,
                                alt = alt,
                                ctrl = ctrl,
                                eventType = eventType,
                            )
                            'Z' -> TuiEvent.Key(
                                TuiKey.TAB,
                                shift = true,
                                alt = alt,
                                ctrl = ctrl,
                                eventType = eventType,
                            )
                            '~' -> {
                                when (firstParam) {
                                    1, 7 -> TuiEvent.Key(
                                        TuiKey.HOME,
                                        shift = shift,
                                        alt = alt,
                                        ctrl = ctrl,
                                        eventType = eventType,
                                    )
                                    4, 8 -> TuiEvent.Key(
                                        TuiKey.END,
                                        shift = shift,
                                        alt = alt,
                                        ctrl = ctrl,
                                        eventType = eventType,
                                    )
                                    3 -> TuiEvent.Key(
                                        TuiKey.DELETE,
                                        shift = shift,
                                        alt = alt,
                                        ctrl = ctrl,
                                        eventType = eventType,
                                    )
                                    5 -> TuiEvent.Key(
                                        TuiKey.PAGE_UP,
                                        shift = shift,
                                        alt = alt,
                                        ctrl = ctrl,
                                        eventType = eventType,
                                    )
                                    6 -> TuiEvent.Key(
                                        TuiKey.PAGE_DOWN,
                                        shift = shift,
                                        alt = alt,
                                        ctrl = ctrl,
                                        eventType = eventType,
                                    )
                                    else -> null
                                }
                            }
                            'u' -> when (firstParam) {
                                13 -> TuiEvent.Key(
                                    TuiKey.ENTER,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                9 -> TuiEvent.Key(
                                    TuiKey.TAB,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                127, 8 -> TuiEvent.Key(
                                    TuiKey.BACKSPACE,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                27 -> TuiEvent.Key(
                                    TuiKey.ESCAPE,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57350 -> TuiEvent.Key(
                                    TuiKey.LEFT,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57351 -> TuiEvent.Key(
                                    TuiKey.RIGHT,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57352 -> TuiEvent.Key(
                                    TuiKey.UP,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57353 -> TuiEvent.Key(
                                    TuiKey.DOWN,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57354 -> TuiEvent.Key(
                                    TuiKey.PAGE_UP,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57355 -> TuiEvent.Key(
                                    TuiKey.PAGE_DOWN,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57356 -> TuiEvent.Key(
                                    TuiKey.HOME,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57357 -> TuiEvent.Key(
                                    TuiKey.END,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                57349 -> TuiEvent.Key(
                                    TuiKey.DELETE,
                                    shift = shift,
                                    alt = alt,
                                    ctrl = ctrl,
                                    eventType = eventType,
                                )
                                in 32..0x10FFFF -> {
                                    val ch = (if (shift && shiftedParam != null) shiftedParam else firstParam).toChar()
                                    TuiEvent.Key(
                                        TuiKey.CHAR,
                                        char = ch,
                                        shift = shift,
                                        alt = alt,
                                        ctrl = ctrl,
                                        eventType = eventType,
                                    )
                                }
                                else -> null
                            }
                            else -> null
                        }

                        if (keyEvent != null) {
                            events.add(keyEvent)
                            i = j + 1
                            continue
                        }
                    }
                } else if (b2 == 'O'.code && i + 2 < len) { // SS3
                    val c3 = (bytes[i + 2].toInt() and 0xFF).toChar()
                    val key = when (c3) {
                        'A' -> TuiKey.UP
                        'B' -> TuiKey.DOWN
                        'C' -> TuiKey.RIGHT
                        'D' -> TuiKey.LEFT
                        'H' -> TuiKey.HOME
                        'F' -> TuiKey.END
                        else -> null
                    }
                    if (key != null) {
                        events.add(TuiEvent.Key(key))
                        i += 3
                        continue
                    }
                } else if (b2 in 32..126) { // Alt + key
                    events.add(TuiEvent.Key(TuiKey.CHAR, char = b2.toChar(), alt = true))
                    i += 2
                    continue
                }

                // Unrecognized escape prefix fallback
                events.add(TuiEvent.Key(TuiKey.ESCAPE))
                i++
                continue
            }

            // Normal control characters
            when (b) {
                13 -> {
                    if (i + 1 < len && bytes[i + 1].toInt() == 10) {
                        i++
                    }
                    events.add(TuiEvent.Key(TuiKey.ENTER))
                    i++
                }
                10 -> {
                    events.add(TuiEvent.Key(TuiKey.ENTER))
                    i++
                }
                9 -> {
                    events.add(TuiEvent.Key(TuiKey.TAB))
                    i++
                }
                127, 8 -> {
                    events.add(TuiEvent.Key(TuiKey.BACKSPACE))
                    i++
                }
                in 1..26 -> {
                    val c = ('a'.code + b - 1).toChar()
                    events.add(TuiEvent.Key(TuiKey.CHAR, char = c, ctrl = true))
                    i++
                }
                in 32..126 -> {
                    events.add(TuiEvent.Key(TuiKey.CHAR, char = b.toChar()))
                    i++
                }
                in 128..255 -> {
                    val count = when {
                        (b and 0xE0) == 0xC0 -> 2
                        (b and 0xF0) == 0xE0 -> 3
                        (b and 0xF8) == 0xF0 -> 4
                        else -> 1
                    }
                    val end = minOf(len, i + count)
                    val str = bytes.decodeToString(i, end)
                    for (ch in str) {
                        events.add(TuiEvent.Key(TuiKey.CHAR, char = ch))
                    }
                    i = end
                }
                else -> {
                    i++
                }
            }
        }

        return events
    }

    /**
     * Converts a [TuiEvent.Key] event to its corresponding ANSI byte sequence.
     */
    fun toAnsiSequence(
        event: TuiEvent.Key,
        applicationCursorKeys: Boolean = false,
        kittyKeyboardFlags: Int = 0,
    ): String {
        if (kittyKeyboardFlags == 0) {
            if (event.eventType == 3) return ""
            val mod = 1 + (if (event.shift) 1 else 0) + (if (event.alt) 2 else 0) + (if (event.ctrl) 4 else 0)

            return when (event.key) {
                TuiKey.ENTER -> "\r"
                TuiKey.BACKSPACE -> "\u007f"
                TuiKey.TAB -> if (event.shift) "\u001b[Z" else "\t"
                TuiKey.ESCAPE -> "\u001b"
                TuiKey.UP -> if (mod > 1) {
                    "\u001b[1;${mod}A"
                } else if (applicationCursorKeys) {
                    "\u001bOA"
                } else {
                    "\u001b[A"
                }
                TuiKey.DOWN -> if (mod > 1) {
                    "\u001b[1;${mod}B"
                } else if (applicationCursorKeys) {
                    "\u001bOB"
                } else {
                    "\u001b[B"
                }
                TuiKey.RIGHT -> if (mod > 1) {
                    "\u001b[1;${mod}C"
                } else if (applicationCursorKeys) {
                    "\u001bOC"
                } else {
                    "\u001b[C"
                }
                TuiKey.LEFT -> if (mod > 1) {
                    "\u001b[1;${mod}D"
                } else if (applicationCursorKeys) {
                    "\u001bOD"
                } else {
                    "\u001b[D"
                }
                TuiKey.HOME -> if (mod > 1) "\u001b[1;${mod}H" else "\u001b[H"
                TuiKey.END -> if (mod > 1) "\u001b[1;${mod}F" else "\u001b[F"
                TuiKey.PAGE_UP -> if (mod > 1) "\u001b[5;$mod~" else "\u001b[5~"
                TuiKey.PAGE_DOWN -> if (mod > 1) "\u001b[6;$mod~" else "\u001b[6~"
                TuiKey.DELETE -> if (mod > 1) "\u001b[3;$mod~" else "\u001b[3~"
                TuiKey.CHAR -> {
                    if (event.ctrl && event.char != null) {
                        val c = event.char.lowercaseChar()
                        if (c in 'a'..'z') {
                            (c.code - 'a'.code + 1).toChar().toString()
                        } else {
                            event.char.toString()
                        }
                    } else if (event.alt && event.char != null) {
                        "\u001b${event.char}"
                    } else {
                        event.char?.toString() ?: ""
                    }
                }
            }
        }

        val reportEventTypes = (kittyKeyboardFlags and 2) != 0
        val reportAllKeys = (kittyKeyboardFlags and 8) != 0
        if (event.eventType == 3 && !reportEventTypes) return ""

        val mod = 1 + (if (event.shift) 1 else 0) + (if (event.alt) 2 else 0) + (if (event.ctrl) 4 else 0)
        val hasEventSuffix = reportEventTypes && event.eventType != 1
        val modSuffix = if (hasEventSuffix) {
            "$mod:${event.eventType}"
        } else if (mod > 1) {
            "$mod"
        } else {
            ""
        }

        return when (event.key) {
            TuiKey.ESCAPE -> "\u001b[27${if (modSuffix.isNotEmpty()) ";$modSuffix" else ""}u"
            TuiKey.ENTER, TuiKey.TAB, TuiKey.BACKSPACE -> {
                val cp = when (event.key) {
                    TuiKey.ENTER -> 13
                    TuiKey.TAB -> 9
                    else -> 127
                }
                if (reportAllKeys || mod > 1 || hasEventSuffix) {
                    "\u001b[$cp${if (modSuffix.isNotEmpty()) ";$modSuffix" else ""}u"
                } else {
                    when (event.key) {
                        TuiKey.ENTER -> "\r"
                        TuiKey.TAB -> "\t"
                        else -> "\u007f"
                    }
                }
            }
            TuiKey.UP, TuiKey.DOWN, TuiKey.RIGHT, TuiKey.LEFT, TuiKey.HOME, TuiKey.END -> {
                val finalChar = when (event.key) {
                    TuiKey.UP -> 'A'
                    TuiKey.DOWN -> 'B'
                    TuiKey.RIGHT -> 'C'
                    TuiKey.LEFT -> 'D'
                    TuiKey.HOME -> 'H'
                    else -> 'F'
                }
                if (modSuffix.isNotEmpty()) {
                    "\u001b[1;$modSuffix$finalChar"
                } else if (applicationCursorKeys && finalChar in "ABCD") {
                    "\u001bO$finalChar"
                } else {
                    "\u001b[$finalChar"
                }
            }
            TuiKey.PAGE_UP, TuiKey.PAGE_DOWN, TuiKey.DELETE -> {
                val code = when (event.key) {
                    TuiKey.PAGE_UP -> 5
                    TuiKey.PAGE_DOWN -> 6
                    else -> 3
                }
                if (modSuffix.isNotEmpty()) "\u001b[$code;$modSuffix~" else "\u001b[$code~"
            }
            TuiKey.CHAR -> {
                val ch = event.char ?: return ""
                val baseCp = if (event.ctrl || event.alt) ch.lowercaseChar().code else ch.code
                if (reportAllKeys || event.ctrl || event.alt || hasEventSuffix) {
                    "\u001b[$baseCp${if (modSuffix.isNotEmpty()) ";$modSuffix" else ""}u"
                } else {
                    ch.toString()
                }
            }
        }
    }
}
