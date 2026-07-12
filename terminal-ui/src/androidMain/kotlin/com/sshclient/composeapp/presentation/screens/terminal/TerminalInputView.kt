package com.sshclient.composeapp.presentation.screens.terminal

import android.content.Context
import android.graphics.Rect
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import com.sshclient.presentation.screens.terminal.ArrowDirection

/**
 * Custom View to handle soft keyboard input and hardware key events for the terminal.
 * This replaces BasicTextField to provide lower-level control over the InputConnection,
 * ensuring Backspace works reliably even when the local buffer is empty.
 */
class TerminalInputView(context: Context) : View(context) {
    var onInput: ((String) -> Unit)? = null
    var onArrowKey: ((ArrowDirection, Boolean) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        onLog?.invoke("onTouchEvent: action=${event.action}, x=${event.x}, y=${event.y}")
        return handled
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onCheckIsTextEditor(): Boolean {
        onLog?.invoke("onCheckIsTextEditor called, returning true")
        return true
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        onLog?.invoke("onFocusChanged: gainFocus=$gainFocus, direction=$direction")
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        onLog?.invoke("onCreateInputConnection called")
        // Use visible password type to disable auto-correct and suggestions
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions =
            EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI

        onLog?.invoke("outAttrs: inputType=${outAttrs.inputType}, imeOptions=${outAttrs.imeOptions}")
        return object : BaseInputConnection(this, false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    // Always send backspace to the terminal, regardless of local buffer state.
                    // This fixes the "Dead Backspace" issue where the keyboard thinks the field
                    // is empty and ignores the delete command.
                    onInput?.invoke(DELETE_CHAR)
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    onInput?.invoke(DELETE_CHAR)
                    return true
                }
                return super.sendKeyEvent(event)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    onInput?.invoke(text.toString())
                }
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        // Delegate hardware keys to the existing handler
        var handled = onInput?.let { input ->
            onArrowKey?.let { arrow ->
                handleHardwareKeyEvent(ComposeKeyEvent(event), input, arrow)
            }
        } ?: false

        if (!handled && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            if (unicodeChar > 0 && !Character.isISOControl(unicodeChar)) {
                onInput?.invoke(unicodeChar.toChar().toString())
                handled = true
            }
        }

        return if (handled) true else super.onKeyDown(keyCode, event)
    }
}
