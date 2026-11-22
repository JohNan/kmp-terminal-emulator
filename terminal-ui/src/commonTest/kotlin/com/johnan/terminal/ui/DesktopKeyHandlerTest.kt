package com.johnan.terminal.ui

import androidx.compose.ui.input.key.Key
import com.johnan.terminal.core.ArrowDirection
import com.johnan.terminal.core.KittyKeyboardProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopKeyHandlerTest {
    @Test
    fun `computeXtermModifierParam calculates all modifier combinations`() {
        assertEquals(1, computeXtermModifierParam(isShift = false, isAlt = false, isCtrl = false))
        assertEquals(2, computeXtermModifierParam(isShift = true, isAlt = false, isCtrl = false))
        assertEquals(3, computeXtermModifierParam(isShift = false, isAlt = true, isCtrl = false))
        assertEquals(4, computeXtermModifierParam(isShift = true, isAlt = true, isCtrl = false))
        assertEquals(5, computeXtermModifierParam(isShift = false, isAlt = false, isCtrl = true))
        assertEquals(6, computeXtermModifierParam(isShift = true, isAlt = false, isCtrl = true))
        assertEquals(7, computeXtermModifierParam(isShift = false, isAlt = true, isCtrl = true))
        assertEquals(8, computeXtermModifierParam(isShift = true, isAlt = true, isCtrl = true))
    }

    @Test
    fun `encodeModifiedArrowKey encodes ctrl and alt combinations and returns null for plain or shift`() {
        assertNull(encodeModifiedArrowKey(ArrowDirection.LEFT, isShift = false, isAlt = false, isCtrl = false))
        assertNull(encodeModifiedArrowKey(ArrowDirection.UP, isShift = true, isAlt = false, isCtrl = false))
        assertEquals(
            "\u001B[1;5D",
            encodeModifiedArrowKey(ArrowDirection.LEFT, isShift = false, isAlt = false, isCtrl = true),
        )
        assertEquals(
            "\u001B[1;3A",
            encodeModifiedArrowKey(ArrowDirection.UP, isShift = false, isAlt = true, isCtrl = false),
        )
        assertEquals(
            "\u001B[1;6C",
            encodeModifiedArrowKey(ArrowDirection.RIGHT, isShift = true, isAlt = false, isCtrl = true),
        )
        assertEquals(
            "\u001B[1;7B",
            encodeModifiedArrowKey(ArrowDirection.DOWN, isShift = false, isAlt = true, isCtrl = true),
        )
    }

    @Test
    fun `encodeModifiedSpecialKey formats unmodified and modified sequences for F1-F12 and nav keys`() {
        // F1-F4 SS3 unmodified, CSI 1;mod modified
        assertEquals("\u001BOP", encodeModifiedSpecialKey("P", isTildeSequence = false, modifierParam = 1))
        assertEquals("\u001BOQ", encodeModifiedSpecialKey("Q", isTildeSequence = false, modifierParam = 1))
        assertEquals("\u001BOR", encodeModifiedSpecialKey("R", isTildeSequence = false, modifierParam = 1))
        assertEquals("\u001BOS", encodeModifiedSpecialKey("S", isTildeSequence = false, modifierParam = 1))
        assertEquals("\u001B[1;5P", encodeModifiedSpecialKey("P", isTildeSequence = false, modifierParam = 5))
        assertEquals("\u001B[1;3Q", encodeModifiedSpecialKey("Q", isTildeSequence = false, modifierParam = 3))
        assertEquals("\u001B[1;2R", encodeModifiedSpecialKey("R", isTildeSequence = false, modifierParam = 2))
        assertEquals("\u001B[1;6S", encodeModifiedSpecialKey("S", isTildeSequence = false, modifierParam = 6))

        // F5-F12 tilde sequences
        val tildeFKeys = listOf("15", "17", "18", "19", "20", "21", "23", "24")
        for (code in tildeFKeys) {
            assertEquals("\u001B[$code~", encodeModifiedSpecialKey(code, isTildeSequence = true, modifierParam = 1))
            assertEquals(
                "\u001B[$code;5~",
                encodeModifiedSpecialKey(code, isTildeSequence = true, modifierParam = 5),
            )
        }

        // Home and End
        assertEquals("\u001B[H", encodeModifiedSpecialKey("H", isTildeSequence = false, modifierParam = 1))
        assertEquals("\u001B[F", encodeModifiedSpecialKey("F", isTildeSequence = false, modifierParam = 1))
        assertEquals("\u001B[1;5H", encodeModifiedSpecialKey("H", isTildeSequence = false, modifierParam = 5))
        assertEquals("\u001B[1;6F", encodeModifiedSpecialKey("F", isTildeSequence = false, modifierParam = 6))

        // Insert, Delete, PageUp, PageDown
        assertEquals("\u001B[2~", encodeModifiedSpecialKey("2", isTildeSequence = true, modifierParam = 1))
        assertEquals("\u001B[3~", encodeModifiedSpecialKey("3", isTildeSequence = true, modifierParam = 1))
        assertEquals("\u001B[5~", encodeModifiedSpecialKey("5", isTildeSequence = true, modifierParam = 1))
        assertEquals("\u001B[6~", encodeModifiedSpecialKey("6", isTildeSequence = true, modifierParam = 1))
        assertEquals("\u001B[2;2~", encodeModifiedSpecialKey("2", isTildeSequence = true, modifierParam = 2))
        assertEquals("\u001B[3;5~", encodeModifiedSpecialKey("3", isTildeSequence = true, modifierParam = 5))
        assertEquals("\u001B[5;2~", encodeModifiedSpecialKey("5", isTildeSequence = true, modifierParam = 2))
        assertEquals("\u001B[6;3~", encodeModifiedSpecialKey("6", isTildeSequence = true, modifierParam = 3))
    }

    @Test
    fun `ctrl shift letter shortcuts are not intercepted in legacy mode`() {
        val shortcutLetters = listOf(Key.C, Key.V, Key.F, Key.T, Key.W, Key.A, Key.Z)
        for (key in shortcutLetters) {
            val emitted = mutableListOf<String>()
            val handled = processHardwareKey(
                key = key,
                isShift = true,
                isAlt = false,
                isCtrl = true,
                onInput = { emitted.add(it) },
                onArrowKey = { _, _ -> },
                kittyKeyboardFlags = 0,
            )
            assertFalse(handled, "Ctrl+Shift+$key should return false")
            assertTrue(emitted.isEmpty(), "Ctrl+Shift+$key must not emit control characters")
        }

        // Unshifted Ctrl+C still emits SIGINT (\u0003)
        val emittedCtrlC = mutableListOf<String>()
        assertTrue(
            processHardwareKey(
                key = Key.C,
                isShift = false,
                isAlt = false,
                isCtrl = true,
                onInput = { emittedCtrlC.add(it) },
                onArrowKey = { _, _ -> },
            ),
        )
        assertEquals(listOf("\u0003"), emittedCtrlC)
    }

    @Test
    fun `ctrl space and ctrl 2 emit NUL when unshifted`() {
        val emitted = mutableListOf<String>()
        assertTrue(
            processHardwareKey(
                key = Key.Spacebar,
                isShift = false,
                isAlt = false,
                isCtrl = true,
                onInput = { emitted.add(it) },
                onArrowKey = { _, _ -> },
            ),
        )
        assertTrue(
            processHardwareKey(
                key = Key.Two,
                isShift = false,
                isAlt = false,
                isCtrl = true,
                onInput = { emitted.add(it) },
                onArrowKey = { _, _ -> },
            ),
        )
        assertEquals(listOf("\u0000", "\u0000"), emitted)

        // Ctrl+Shift+Space and Ctrl+Shift+2 should not emit NUL
        val shiftedEmitted = mutableListOf<String>()
        assertFalse(
            processHardwareKey(
                key = Key.Spacebar,
                isShift = true,
                isAlt = false,
                isCtrl = true,
                onInput = { shiftedEmitted.add(it) },
                onArrowKey = { _, _ -> },
            ),
        )
        assertFalse(
            processHardwareKey(
                key = Key.Two,
                isShift = true,
                isAlt = false,
                isCtrl = true,
                onInput = { shiftedEmitted.add(it) },
                onArrowKey = { _, _ -> },
            ),
        )
        assertTrue(shiftedEmitted.isEmpty())
    }

    @Test
    fun `tab handling respects legacy modifiers and kitty keyboard mode`() {
        val inputs = mutableListOf<String>()
        processHardwareKey(Key.Tab, isShift = false, isAlt = false, isCtrl = false, { inputs.add(it) }, { _, _ -> })
        processHardwareKey(Key.Tab, isShift = true, isAlt = false, isCtrl = false, { inputs.add(it) }, { _, _ -> })
        processHardwareKey(Key.Tab, isShift = false, isAlt = true, isCtrl = false, { inputs.add(it) }, { _, _ -> })
        processHardwareKey(Key.Tab, isShift = true, isAlt = true, isCtrl = false, { inputs.add(it) }, { _, _ -> })

        assertEquals(listOf("\t", "\u001B[Z", "\u001B\t", "\u001B\u001B[Z"), inputs)

        // Kitty mode preserves CSI u encoding for modified Tab
        val kittyInputs = mutableListOf<String>()
        processHardwareKey(
            key = Key.Tab,
            isShift = true,
            isAlt = false,
            isCtrl = false,
            onInput = { kittyInputs.add(it) },
            onArrowKey = { _, _ -> },
            kittyKeyboardFlags = KittyKeyboardProtocol.DISAMBIGUATE_ESCAPE_CODES,
        )
        assertEquals(listOf("\u001B[9;2u"), kittyInputs)
    }

    @Test
    fun `alt letters and digits emit ESC prefix with shift casing`() {
        val inputs = mutableListOf<String>()
        processHardwareKey(Key.A, isShift = false, isAlt = true, isCtrl = false, { inputs.add(it) }, { _, _ -> })
        processHardwareKey(Key.Z, isShift = false, isAlt = true, isCtrl = false, { inputs.add(it) }, { _, _ -> })
        processHardwareKey(Key.M, isShift = true, isAlt = true, isCtrl = false, { inputs.add(it) }, { _, _ -> })
        processHardwareKey(Key.Zero, isShift = false, isAlt = true, isCtrl = false, { inputs.add(it) }, { _, _ -> })
        processHardwareKey(Key.Nine, isShift = false, isAlt = true, isCtrl = false, { inputs.add(it) }, { _, _ -> })

        assertEquals(listOf("\u001Ba", "\u001Bz", "\u001BM", "\u001B0", "\u001B9"), inputs)
    }

    @Test
    fun `modified arrows and special keys bypass legacy ctrl early return`() {
        val inputs = mutableListOf<String>()
        val arrows = mutableListOf<Pair<ArrowDirection, Boolean>>()

        assertTrue(
            processHardwareKey(
                key = Key.DirectionLeft,
                isShift = false,
                isAlt = false,
                isCtrl = true,
                onInput = { inputs.add(it) },
                onArrowKey = { d, s -> arrows.add(d to s) },
            ),
        )
        assertTrue(
            processHardwareKey(
                key = Key.DirectionUp,
                isShift = false,
                isAlt = true,
                isCtrl = false,
                onInput = { inputs.add(it) },
                onArrowKey = { d, s -> arrows.add(d to s) },
            ),
        )
        assertTrue(
            processHardwareKey(
                key = Key.DirectionRight,
                isShift = true,
                isAlt = false,
                isCtrl = false,
                onInput = { inputs.add(it) },
                onArrowKey = { d, s -> arrows.add(d to s) },
            ),
        )
        assertTrue(
            processHardwareKey(
                key = Key.MoveHome,
                isShift = false,
                isAlt = false,
                isCtrl = true,
                onInput = { inputs.add(it) },
                onArrowKey = { d, s -> arrows.add(d to s) },
            ),
        )
        assertTrue(
            processHardwareKey(
                key = Key.F5,
                isShift = false,
                isAlt = true,
                isCtrl = false,
                onInput = { inputs.add(it) },
                onArrowKey = { d, s -> arrows.add(d to s) },
            ),
        )

        assertEquals(listOf("\u001B[1;5D", "\u001B[1;3A", "\u001B[1;5H", "\u001B[15;3~"), inputs)
        assertEquals(listOf(ArrowDirection.RIGHT to true), arrows)
    }

    @Test
    fun `applyModifierToChar handles legacy and kitty modes`() {
        assertEquals("\u0003", applyModifierToChar('c', ModifierKeyState(ctrlPressed = true)))
        assertEquals("\u001Bx", applyModifierToChar('x', ModifierKeyState(altPressed = true)))
        assertEquals(
            "\u001B[99;5u",
            applyModifierToChar(
                'c',
                ModifierKeyState(ctrlPressed = true),
                kittyKeyboardFlags = KittyKeyboardProtocol.DISAMBIGUATE_ESCAPE_CODES,
            ),
        )
    }

    @Test
    fun `meta chords are passed through unhandled`() {
        val keys = listOf(Key.C, Key.Tab, Key.DirectionLeft, Key.Enter, Key.F1)
        for (key in keys) {
            val emitted = mutableListOf<String>()
            val arrows = mutableListOf<Pair<ArrowDirection, Boolean>>()
            val handled = processHardwareKey(
                key = key,
                isShift = false,
                isAlt = false,
                isCtrl = false,
                onInput = { emitted.add(it) },
                onArrowKey = { d, s -> arrows.add(d to s) },
                isMeta = true,
            )
            assertFalse(handled, "Meta+$key should return false")
            assertTrue(emitted.isEmpty(), "Meta+$key must not emit text")
            assertTrue(arrows.isEmpty(), "Meta+$key must not emit arrow events")
        }
    }

    @Test
    fun `isAltGrComposedChar detects right alt glyph composition only`() {
        // Right Alt producing '@' vs base '2'
        assertTrue(
            isAltGrComposedChar(
                isRightAltOnly = true,
                unicodeChar = '@'.code,
                unmodifiedAltUnicodeChar = '2'.code,
            ),
        )
        // Left Alt does not qualify as AltGr
        assertFalse(
            isAltGrComposedChar(
                isRightAltOnly = false,
                unicodeChar = '@'.code,
                unmodifiedAltUnicodeChar = '2'.code,
            ),
        )
        // Same character as unmodified base -> not AltGr composed
        assertFalse(
            isAltGrComposedChar(
                isRightAltOnly = true,
                unicodeChar = '2'.code,
                unmodifiedAltUnicodeChar = '2'.code,
            ),
        )
        // Control characters or zero codepoint -> false
        assertFalse(
            isAltGrComposedChar(
                isRightAltOnly = true,
                unicodeChar = 0,
                unmodifiedAltUnicodeChar = '2'.code,
            ),
        )
        assertFalse(
            isAltGrComposedChar(
                isRightAltOnly = true,
                unicodeChar = 0x1B,
                unmodifiedAltUnicodeChar = '2'.code,
            ),
        )
    }

    @Test
    fun `shouldEmitFallbackUnicodeChar blocks modified shortcut leaks while allowing plain and AltGr chars`() {
        // Ctrl+Shift+C fallback must be blocked
        assertFalse(
            shouldEmitFallbackUnicodeChar(
                isCtrl = true,
                isMeta = false,
                isAlt = false,
                isAltGrComposed = false,
                unicodeChar = 'C'.code,
            ),
        )
        // Meta+C fallback must be blocked
        assertFalse(
            shouldEmitFallbackUnicodeChar(
                isCtrl = false,
                isMeta = true,
                isAlt = false,
                isAltGrComposed = false,
                unicodeChar = 'c'.code,
            ),
        )
        // Left Alt+C fallback must be blocked
        assertFalse(
            shouldEmitFallbackUnicodeChar(
                isCtrl = false,
                isMeta = false,
                isAlt = true,
                isAltGrComposed = false,
                unicodeChar = 'c'.code,
            ),
        )
        // ISO control characters (ETX, DEL, C1) must be blocked
        assertFalse(
            shouldEmitFallbackUnicodeChar(
                isCtrl = false,
                isMeta = false,
                isAlt = false,
                isAltGrComposed = false,
                unicodeChar = 0x03,
            ),
        )
        assertFalse(
            shouldEmitFallbackUnicodeChar(
                isCtrl = false,
                isMeta = false,
                isAlt = false,
                isAltGrComposed = false,
                unicodeChar = 0x7F,
            ),
        )
        // Plain printable char and AltGr composed char are allowed
        assertTrue(
            shouldEmitFallbackUnicodeChar(
                isCtrl = false,
                isMeta = false,
                isAlt = false,
                isAltGrComposed = false,
                unicodeChar = 'a'.code,
            ),
        )
        assertTrue(
            shouldEmitFallbackUnicodeChar(
                isCtrl = false,
                isMeta = false,
                isAlt = true,
                isAltGrComposed = true,
                unicodeChar = '@'.code,
            ),
        )
    }
}
