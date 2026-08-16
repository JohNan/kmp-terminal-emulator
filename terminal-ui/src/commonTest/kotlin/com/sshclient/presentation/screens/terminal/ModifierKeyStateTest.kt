package com.sshclient.presentation.screens.terminal
import com.sshclient.composeapp.presentation.screens.terminal.ModifierKeyState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ModifierKeyState data class
 *
 * Tests the modifier key state management used to integrate
 * TerminalKeyBar modifiers with soft keyboard input.
 */
class ModifierKeyStateTest {
    @Test
    fun `default state has no modifiers pressed`() {
        val state = ModifierKeyState()

        assertFalse(state.ctrlPressed, "Ctrl should not be pressed by default")
        assertFalse(state.altPressed, "Alt should not be pressed by default")
    }

    @Test
    fun `can create state with ctrl pressed`() {
        val state = ModifierKeyState(ctrlPressed = true)

        assertTrue(state.ctrlPressed, "Ctrl should be pressed")
        assertFalse(state.altPressed, "Alt should not be pressed")
    }

    @Test
    fun `can create state with alt pressed`() {
        val state = ModifierKeyState(altPressed = true)

        assertFalse(state.ctrlPressed, "Ctrl should not be pressed")
        assertTrue(state.altPressed, "Alt should be pressed")
    }

    @Test
    fun `can toggle ctrl state`() {
        val initialState = ModifierKeyState()
        val withCtrl = initialState.copy(ctrlPressed = true)
        val withoutCtrl = withCtrl.copy(ctrlPressed = false)

        assertFalse(initialState.ctrlPressed, "Initial state should have ctrl off")
        assertTrue(withCtrl.ctrlPressed, "Toggled state should have ctrl on")
        assertFalse(withoutCtrl.ctrlPressed, "Toggled back state should have ctrl off")
    }

    @Test
    fun `can toggle alt state`() {
        val initialState = ModifierKeyState()
        val withAlt = initialState.copy(altPressed = true)
        val withoutAlt = withAlt.copy(altPressed = false)

        assertFalse(initialState.altPressed, "Initial state should have alt off")
        assertTrue(withAlt.altPressed, "Toggled state should have alt on")
        assertFalse(withoutAlt.altPressed, "Toggled back state should have alt off")
    }

    @Test
    fun `mutual exclusivity pattern - enabling ctrl disables alt`() {
        val withAlt = ModifierKeyState(altPressed = true)
        val withCtrl = withAlt.copy(ctrlPressed = true, altPressed = false)

        assertTrue(withAlt.altPressed, "Alt should initially be pressed")
        assertTrue(withCtrl.ctrlPressed, "Ctrl should be pressed after toggle")
        assertFalse(withCtrl.altPressed, "Alt should be disabled when ctrl enabled")
    }

    @Test
    fun `mutual exclusivity pattern - enabling alt disables ctrl`() {
        val withCtrl = ModifierKeyState(ctrlPressed = true)
        val withAlt = withCtrl.copy(altPressed = true, ctrlPressed = false)

        assertTrue(withCtrl.ctrlPressed, "Ctrl should initially be pressed")
        assertTrue(withAlt.altPressed, "Alt should be pressed after toggle")
        assertFalse(withAlt.ctrlPressed, "Ctrl should be disabled when alt enabled")
    }

    @Test
    fun `reset all modifiers`() {
        val withModifiers = ModifierKeyState(ctrlPressed = true, altPressed = false)
        val reset = ModifierKeyState()

        assertTrue(withModifiers.ctrlPressed, "Initial state should have ctrl pressed")
        assertFalse(reset.ctrlPressed, "Reset state should have no ctrl")
        assertFalse(reset.altPressed, "Reset state should have no alt")
    }
}
