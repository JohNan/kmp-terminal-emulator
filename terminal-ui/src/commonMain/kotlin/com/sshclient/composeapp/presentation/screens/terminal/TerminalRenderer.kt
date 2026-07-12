package com.sshclient.composeapp.presentation.screens.terminal

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.sshclient.data.terminal.TerminalCell
import com.sshclient.data.terminal.TerminalScreenState
import com.sshclient.presentation.screens.terminal.ArrowDirection
import com.sshclient.presentation.screens.terminal.SearchState
import com.sshclient.presentation.screens.terminal.SelectionState

/**
 * Renders the terminal screen state as a grid of characters with integrated keyboard input
 *
 * Features:
 * - Dynamic terminal dimensions based on view size
 * - Proper line wrapping at terminal column boundary
 * - Keyboard input with disabled suggestions/autocorrect
 * - ANSI color and formatting support
 * - Modifier key integration (Ctrl/Alt) with soft keyboard
 * - Text selection and copy support
 *
 * @param terminalState The terminal screen state to render
 * @param onInput Callback for handling keyboard input
 * @param enabled Whether the terminal should accept input
 * @param modifierKeyState Current state of modifier keys (Ctrl/Alt)
 * @param onModifierStateChange Callback when modifier state should change
 * @param focusRequester Focus requester for controlling keyboard visibility
 * @param selectionState Current text selection state
 * @param onPlaceStartCursor Callback when start cursor is placed (tap in copy mode)
 * @param onStartDraggingStartCursor Callback when start cursor drag begins
 * @param onUpdateStartCursor Callback when start cursor position updates (drag)
 * @param onFinalizeStartCursor Callback when start cursor drag ends
 * @param onStartDraggingEndCursor Callback when end cursor drag begins (long-press)
 * @param onUpdateEndCursor Callback when end cursor position updates (drag)
 * @param onFinalizeEndCursorAndCopy Callback when end cursor drag ends (auto-copy)
 * @param onExitCopyMode Callback to exit copy mode
 * @param modifier Modifier for the terminal
 */
@Composable
fun TerminalRenderer(
    terminalState: TerminalScreenState,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    onLog: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    modifierKeyState: ModifierKeyState = ModifierKeyState(),
    onModifierStateChange: (ModifierKeyState) -> Unit = {},
    focusRequester: FocusRequester? = null,
    colorScheme: com.sshclient.domain.model.TerminalColorScheme? = null,
    fontSize: Float = 14f,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    selectionState: SelectionState = SelectionState.None,
    searchState: SearchState = SearchState(),
    onSingleTap: (row: Int, col: Int) -> Unit = { _, _ -> },
    onStartDraggingStartCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    onUpdateStartCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    onFinalizeStartCursor: () -> Unit = {},
    onStartDraggingEndCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    onUpdateEndCursor: (row: Int, col: Int) -> Unit = { _, _ -> },
    onFinalizeEndCursorAndCopy: () -> Unit = {},
    onExitCopyMode: () -> Unit = {},
    onLongPress: (offset: Offset, row: Int, col: Int) -> Unit = { _, _, _ -> },
    onUrlClick: (String) -> Unit = {},
    onKeyboardToggleAvailable: (() -> Unit) -> Unit = {},
    onMouseEvent: (com.sshclient.data.terminal.MouseEvent, Int, Int) -> Unit = { _, _, _ -> },
) {
    val textMeasurer = rememberTextMeasurer()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Use color scheme for cursor or fall back to MaterialTheme
    val cursorColor =
        colorScheme?.cursor?.copy(alpha = 0.5f)
            ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    val density = LocalDensity.current

    // Software keyboard controller for explicit keyboard show/hide
    val keyboardController = LocalSoftwareKeyboardController.current

    // Base terminal styling - using monospace font for proper alignment
    val baseFontSize = fontSize.sp
    val baseTextStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = baseFontSize,
            color = colorScheme?.foreground ?: (if (isDark) Color(0xFFE0E0E0) else Color(0xFF000000)),
        )

    // Measure cell dimensions for wrapping calculations
    // Using any character as reference since monospace fonts ensure consistent width
    val cellMeasure = textMeasurer.measure("W", baseTextStyle)
    val cellWidth = cellMeasure.size.width.toFloat()
    val cellHeight = cellMeasure.size.height.toFloat()

    // Combine scrollback and visible rows for full terminal history display.
    // Optimization: only recalculate allRows when rows or scrollback change.
    val allRows =
        remember(
            terminalState.rows,
            terminalState.scrollback,
        ) {
            val rows = terminalState.rows

            // Optimization: Use VirtualTerminalList to avoid creating a new ArrayList
            // and copying all elements (which can be thousands) on every frame.
            VirtualTerminalList(terminalState.scrollback, rows)
        }
    val scrollbackLineCount = terminalState.scrollback.size

    // Wrap in BoxWithConstraints to ensure totalHeight is recalculated when keyboard hides
    // and available space (maxHeight) changes.
    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(modifier = modifier) {
        // Calculate total height needed for the canvas (scrollback + visible rows)
        // Ensure it is at least as tall as the viewport to handle pointer events and background properly.
        val totalHeight =
            with(density) {
                maxOf((allRows.size * cellHeight).toDp(), maxHeight)
            }

        // Focus requester for the hidden text field - use provided one or create new
        val internalFocusRequester = focusRequester ?: remember { FocusRequester() }

        val keyboardController = LocalSoftwareKeyboardController.current

        val onRequestFocus = {
            internalFocusRequester.requestFocus()
            keyboardController?.show()
            Unit
        }

        // Request focus when the terminal becomes enabled
        LaunchedEffect(enabled) {
            if (enabled) {
                onRequestFocus()
            }
        }

        // Expose keyboard toggle capability
        LaunchedEffect(onRequestFocus) {
            onKeyboardToggleAvailable(onRequestFocus)
        }

        // Track if user recently typed to force scroll to bottom
        var shouldForceScrollToBottom by remember { mutableStateOf(false) }

        // Track the last known max scroll to detect if we were at bottom before an update
        var lastKnownMaxScroll by remember { mutableIntStateOf(0) }

        // Auto-scroll logic: triggered whenever maxValue changes (new content or resize)
        // or when user input forces a scroll to bottom.
        LaunchedEffect(scrollState.maxValue, shouldForceScrollToBottom) {
            val currentMax = scrollState.maxValue
            val threshold = if (cellHeight > 0) 10 * cellHeight else 100f

            // Check if we were at the bottom of the previous content
            val wasAtBottom = scrollState.value >= lastKnownMaxScroll - threshold || lastKnownMaxScroll == 0

            if (wasAtBottom || shouldForceScrollToBottom) {
                if (currentMax > 0) {
                    scrollState.scrollTo(currentMax)
                }
                shouldForceScrollToBottom = false
            }

            // Always keep lastKnownMaxScroll in sync with current maxValue
            lastKnownMaxScroll = currentMax
        }

        // Create input handler callback that applies modifier transformations
        val handleSoftInput: (String) -> Unit =
            remember(onInput, modifierKeyState, onModifierStateChange) {
                { input ->
                    // Force scroll to bottom on next state update when typing
                    shouldForceScrollToBottom = true

                    // Apply modifier key transformations if active
                    if (modifierKeyState.ctrlPressed || modifierKeyState.altPressed) {
                        // Transform all characters with active modifiers and batch them
                        // Bolt: Eliminate the intermediate List<Char> allocation from `.map` on hot path
                        val transformed = buildString(input.length) {
                            for (i in 0 until input.length) {
                                append(applyModifierToChar(input[i], modifierKeyState))
                            }
                        }
                        onInput(transformed)
                        // Reset modifiers after applying them
                        onModifierStateChange(ModifierKeyState())
                    } else {
                        // No modifiers - send characters as-is
                        onInput(input)
                    }
                }
            }

        // Capture soft and hardware keyboard input natively on each platform
        if (enabled && selectionState is SelectionState.None) {
            TerminalInputCapture(
                enabled = enabled,
                focusRequester = internalFocusRequester,
                onInput = handleSoftInput,
                onArrowKey = onArrowKey,
                onLog = onLog,
                modifier = Modifier.fillMaxSize()
            )
        }

        TerminalCanvas(
            modifier = Modifier.fillMaxSize(),
            terminalState = terminalState,
            allRows = allRows,
            scrollbackLineCount = scrollbackLineCount,
            scrollState = scrollState,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            totalHeight = totalHeight,
            isDark = isDark,
            cursorColor = cursorColor,
            baseTextStyle = baseTextStyle,
            textMeasurer = textMeasurer,
            enabled = enabled,
            onRequestFocus = onRequestFocus,
            colorScheme = colorScheme,
            selectionState = selectionState,
            searchState = searchState,
            onSingleTap = onSingleTap,
            onStartDraggingStartCursor = onStartDraggingStartCursor,
            onUpdateStartCursor = onUpdateStartCursor,
            onFinalizeStartCursor = onFinalizeStartCursor,
            onStartDraggingEndCursor = onStartDraggingEndCursor,
            onUpdateEndCursor = onUpdateEndCursor,
            onFinalizeEndCursorAndCopy = onFinalizeEndCursorAndCopy,
            onExitCopyMode = onExitCopyMode,
            onLongPress = onLongPress,
            onUrlClick = onUrlClick,
            onMouseEvent = onMouseEvent,
            onLog = onLog,
        )
    }
}

/**
 * Virtual list that concatenates scrollback and visible rows without copying/allocation.
 */
private class VirtualTerminalList(
    private val scrollback: List<Array<TerminalCell>>,
    private val visibleRows: List<Array<TerminalCell>>,
) : AbstractList<Array<TerminalCell>>() {
    override val size: Int
        get() = scrollback.size + visibleRows.size

    override fun get(index: Int): Array<TerminalCell> {
        if (index < scrollback.size) {
            return scrollback[index]
        }
        val visibleIndex = index - scrollback.size
        if (visibleIndex >= visibleRows.size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
        return visibleRows[visibleIndex]
    }
}
