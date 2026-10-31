package com.johnan.terminal.core

/**
 * FinalTerm / FTCS (OSC 133) semantic shell integration boundary markers.
 */
enum class PromptMarkType {
    PROMPT_START,
    COMMAND_START,
    OUTPUT_START,
    COMMAND_FINISHED,
}

/**
 * Recorded semantic prompt boundary at an absolute buffer line ([absLine] = scrollback index or
 * scrollbackSize + row).
 */
data class PromptMark(
    val type: PromptMarkType,
    val absLine: Int,
    val col: Int,
    val exitCode: Int? = null,
) {
    val absoluteLine: Int
        get() = absLine
}

/**
 * Parses OSC 7 (`file://hostname/path`) working directory URIs and decodes RFC 3986 percent-encoded UTF-8 sequences.
 */
internal fun decodeOsc7WorkingDirectory(rawUri: String): String? {
    val trimmed = rawUri.trim()
    if (trimmed.isEmpty()) return null
    val pathPart = if (trimmed.startsWith("file://")) {
        val afterScheme = trimmed.substring(7)
        val slashIndex = afterScheme.indexOf('/')
        if (slashIndex < 0) return null
        afterScheme.substring(slashIndex)
    } else if (trimmed.startsWith("/")) {
        trimmed
    } else {
        return null
    }
    return percentDecodeUtf8(pathPart)
}

internal fun percentDecodeUtf8(input: String): String {
    if (!input.contains('%')) return input
    val bytes = ByteArray(input.length)
    var byteCount = 0
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '%' && i + 2 < input.length) {
            val h1 = hexDigit(input[i + 1])
            val h2 = hexDigit(input[i + 2])
            if (h1 >= 0 && h2 >= 0) {
                bytes[byteCount++] = ((h1 shl 4) or h2).toByte()
                i += 3
                continue
            }
        }
        val encoded = c.toString().encodeToByteArray()
        for (b in encoded) {
            bytes[byteCount++] = b
        }
        i++
    }
    return bytes.decodeToString(0, byteCount)
}

private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
