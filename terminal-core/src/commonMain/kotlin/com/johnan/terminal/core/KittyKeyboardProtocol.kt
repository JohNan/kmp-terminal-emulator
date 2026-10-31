package com.johnan.terminal.core

/**
 * Key event type according to the Kitty Keyboard Protocol.
 */
enum class KeyEventType(
    val code: Int,
) {
    PRESS(1),
    REPEAT(2),
    RELEASE(3),
}

/**
 * Implementation of the progressive keyboard enhancement protocol (Kitty Keyboard Protocol).
 *
 * Defines capability flags, modifier constants, and key event encoding routines.
 */
object KittyKeyboardProtocol {
    const val DISAMBIGUATE_ESCAPE_CODES = 1
    const val REPORT_EVENT_TYPES = 2
    const val REPORT_ALTERNATE_KEYS = 4
    const val REPORT_ALL_KEYS_AS_ESCAPE_CODES = 8
    const val REPORT_ASSOCIATED_TEXT = 16
    const val ALL_FLAGS_MASK = 31

    const val MODIFIER_SHIFT = 1
    const val MODIFIER_ALT = 2
    const val MODIFIER_CTRL = 4
    const val MODIFIER_SUPER = 8

    /**
     * Encodes a key event into an ANSI escape sequence according to the active Kitty keyboard flags.
     */
    fun encodeKeyEvent(
        codepoint: Int,
        shiftedCodepoint: Int? = null,
        baseLayoutCodepoint: Int? = null,
        modifiers: Int = 0,
        eventType: KeyEventType = KeyEventType.PRESS,
        associatedText: String? = null,
        flags: Int = 0,
    ): String {
        val disambiguate = (flags and DISAMBIGUATE_ESCAPE_CODES) != 0
        val reportEventTypes = (flags and REPORT_EVENT_TYPES) != 0
        val reportAlternateKeys = (flags and REPORT_ALTERNATE_KEYS) != 0
        val reportAllKeys = (flags and REPORT_ALL_KEYS_AS_ESCAPE_CODES) != 0
        val reportText = (flags and REPORT_ASSOCIATED_TEXT) != 0

        if (flags == 0) {
            if (eventType == KeyEventType.RELEASE) return ""
            return when {
                codepoint == 13 -> "\r"
                codepoint == 9 -> if ((modifiers and MODIFIER_SHIFT) != 0) "\u001B[Z" else "\t"
                codepoint == 127 || codepoint == 8 -> {
                    if ((modifiers and MODIFIER_ALT) != 0) "\u001B\u007F" else "\u007F"
                }
                codepoint == 27 -> {
                    if ((modifiers and MODIFIER_ALT) != 0) "\u001B\u001B" else "\u001B"
                }
                (modifiers and MODIFIER_CTRL) != 0 &&
                    (codepoint in 'a'.code..'z'.code || codepoint in 'A'.code..'Z'.code) -> {
                    val lower = if (codepoint in 'A'.code..'Z'.code) codepoint + 32 else codepoint
                    val ctrlChar = (lower - 'a'.code + 1).toChar().toString()
                    if ((modifiers and MODIFIER_ALT) != 0) "\u001B$ctrlChar" else ctrlChar
                }
                else -> {
                    val ch = if ((modifiers and MODIFIER_SHIFT) != 0 && shiftedCodepoint != null) {
                        shiftedCodepoint
                    } else {
                        codepoint
                    }
                    val s = codepointToString(ch)
                    if ((modifiers and MODIFIER_ALT) != 0) "\u001B$s" else s
                }
            }
        }

        if (eventType == KeyEventType.RELEASE && !reportEventTypes) return ""

        val isModifiedShiftPrintable = modifiers == MODIFIER_SHIFT &&
            !reportAllKeys &&
            codepoint >= 32 &&
            codepoint != 127

        val isDisambiguatedKey = codepoint == 27 ||
            ((codepoint == 13 || codepoint == 9 || codepoint == 127 || codepoint == 8) && modifiers != 0)

        val needCsiU = reportAllKeys ||
            codepoint == 27 ||
            (modifiers != 0 && !isModifiedShiftPrintable) ||
            (reportEventTypes && eventType != KeyEventType.PRESS) ||
            (disambiguate && isDisambiguatedKey)

        if (!needCsiU) {
            return when (codepoint) {
                13 -> "\r"
                9 -> "\t"
                127, 8 -> "\u007F"
                else -> {
                    val ch = if ((modifiers and MODIFIER_SHIFT) != 0 && shiftedCodepoint != null) {
                        shiftedCodepoint
                    } else {
                        codepoint
                    }
                    codepointToString(ch)
                }
            }
        }

        val effectiveCodepoint = if (codepoint == 8) 127 else codepoint
        val keyPart = if (reportAlternateKeys && (shiftedCodepoint != null || baseLayoutCodepoint != null)) {
            if (baseLayoutCodepoint != null) {
                "$effectiveCodepoint:${shiftedCodepoint ?: ""}:$baseLayoutCodepoint"
            } else {
                "$effectiveCodepoint:$shiftedCodepoint"
            }
        } else {
            effectiveCodepoint.toString()
        }

        val modParam = 1 + modifiers
        val needModSection = modifiers != 0 ||
            (reportEventTypes && eventType != KeyEventType.PRESS) ||
            (reportText && !associatedText.isNullOrEmpty())

        val modPart = if (reportEventTypes && eventType != KeyEventType.PRESS) {
            "$modParam:${eventType.code}"
        } else if (needModSection) {
            "$modParam"
        } else {
            ""
        }

        val textPart = if (reportText && !associatedText.isNullOrEmpty()) {
            stringToCodepoints(associatedText).joinToString(":")
        } else {
            ""
        }

        return "\u001B[$keyPart" +
            (if (needModSection) ";$modPart" else "") +
            (if (textPart.isNotEmpty()) ";$textPart" else "") +
            "u"
    }

    private fun codepointToString(cp: Int): String {
        if (cp <= 0xFFFF) return cp.toChar().toString()
        val high = ((cp - 0x10000) shr 10) + 0xD800
        val low = ((cp - 0x10000) and 0x3FF) + 0xDC00
        return "${high.toChar()}${low.toChar()}"
    }

    private fun stringToCodepoints(text: String): List<Int> {
        val list = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                val high = ch.code
                val low = text[i + 1].code
                list.add(((high - 0xD800) shl 10) + (low - 0xDC00) + 0x10000)
                i += 2
            } else {
                list.add(ch.code)
                i++
            }
        }
        return list
    }
}
