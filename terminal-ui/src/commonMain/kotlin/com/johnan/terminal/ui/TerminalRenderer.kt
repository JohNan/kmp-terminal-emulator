package com.johnan.terminal.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.ArrowDirection
import com.johnan.terminal.core.SearchState
import com.johnan.terminal.core.SelectionState
import com.johnan.terminal.core.TerminalCell
import com.johnan.terminal.core.TerminalScreenState

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
    config: TerminalUiConfig = LocalTerminalUiConfig.current,
    enabled: Boolean = true,
    modifierKeyState: ModifierKeyState = ModifierKeyState(),
    onModifierStateChange: (ModifierKeyState) -> Unit = {},
    focusRequester: FocusRequester? = null,
    colorScheme: com.johnan.terminal.core.TerminalColorScheme? = null,
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
    showKeyboardSignal: Int = 0,
    onTerminalResize: (cols: Int, rows: Int) -> Unit = { _, _ -> },
    onResize: (rows: Int, cols: Int) -> Unit = { _, _ -> },
    onMouseEvent: ((com.johnan.terminal.core.MouseEvent, Int, Int) -> Unit)? = null,
    onCellMeasured: (Float, Float) -> Unit = { _, _ -> },
) {
    val textMeasurer = rememberTextMeasurer()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val effectiveColorScheme = colorScheme ?: config.colorScheme

    // Use color scheme or override for cursor or fall back to MaterialTheme
    val cursorColor =
        config.cursor.overrideColor
            ?: effectiveColorScheme.cursor.copy(alpha = 0.5f)

    val density = LocalDensity.current

    // Software keyboard controller for explicit keyboard show/hide
    val keyboardController = LocalSoftwareKeyboardController.current

    // Base terminal styling - using configured typography
    val baseFontSize = if (fontSize != 14f) fontSize.sp else config.typography.fontSize
    val baseTextStyle =
        TextStyle(
            fontFamily = config.typography.fontFamily,
            fontSize = baseFontSize,
            letterSpacing = config.typography.letterSpacing,
            color = effectiveColorScheme.foreground,
        )

    // Measure cell dimensions with sub-pixel precision to prevent rounding error accumulation across columns.
    // Monospace fonts ensure consistent width, so averaging over 80 characters provides the exact float cell width.
    val measurementString = "0".repeat(80)
    val cellMeasure = textMeasurer.measure(measurementString, baseTextStyle)
    val cellWidth = cellMeasure.size.width.toFloat() / 80f
    val cellHeight = cellMeasure.size.height.toFloat()

    // Debug log cell dimensions to browser console
    LaunchedEffect(cellWidth, cellHeight) {
        println("[TerminalRenderer] Measured cell dimensions: width=$cellWidth, height=$cellHeight")
        onCellMeasured(cellWidth, cellHeight)
    }

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
        val maxWidthPx = with(density) { if (maxWidth.value.isFinite()) maxWidth.toPx() else 0f }
        val maxHeightPx = with(density) { if (maxHeight.value.isFinite()) maxHeight.toPx() else 0f }

        if (cellWidth > 0f && cellHeight > 0f && maxWidthPx > 0f && maxHeightPx > 0f) {
            val calculatedCols = (maxWidthPx / cellWidth).toInt().coerceAtLeast(10)
            val calculatedRows = (maxHeightPx / cellHeight).toInt().coerceAtLeast(5)
            LaunchedEffect(calculatedCols, calculatedRows) {
                onTerminalResize(calculatedCols, calculatedRows)
                onResize(calculatedRows, calculatedCols)
            }
        }

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

        // Auto-scroll to keep cursor in view
        val cursorRow = terminalState.cursorRow
        val cursorVisible = terminalState.cursorVisible

        LaunchedEffect(
            cursorRow,
            scrollbackLineCount,
            cellHeight,
            maxHeightPx,
            cursorVisible,
            shouldForceScrollToBottom
        ) {
            if (shouldForceScrollToBottom) {
                scrollState.scrollTo(scrollState.maxValue)
                shouldForceScrollToBottom = false
                return@LaunchedEffect
            }

            if (!enabled || !cursorVisible) return@LaunchedEffect

            val cursorY = (cursorRow + scrollbackLineCount) * cellHeight
            val viewportTop = scrollState.value
            val viewportBottom = viewportTop + maxHeightPx

            if (cursorY < viewportTop) {
                scrollState.scrollTo(cursorY.toInt().coerceIn(0, scrollState.maxValue))
            } else if (cursorY + cellHeight > viewportBottom) {
                val targetScroll = (cursorY + cellHeight - maxHeightPx).toInt()
                scrollState.scrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
            }
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
                showKeyboardSignal = showKeyboardSignal,
                onInput = handleSoftInput,
                onArrowKey = onArrowKey,
                onLog = onLog,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val defaultOnMouseEvent: (com.johnan.terminal.core.MouseEvent, Int, Int) -> Unit =
            remember(onInput, terminalState.sgrMouseModeEnabled) {
                { event, row, col ->
                    val ansiRow = row + 1
                    val ansiCol = col + 1
                    val isSgr = terminalState.sgrMouseModeEnabled
                    val button =
                        when (event) {
                            com.johnan.terminal.core.MouseEvent.Press -> 0
                            com.johnan.terminal.core.MouseEvent.Release -> 3
                            com.johnan.terminal.core.MouseEvent.Drag -> 32
                            com.johnan.terminal.core.MouseEvent.WheelUp -> 64
                            com.johnan.terminal.core.MouseEvent.WheelDown -> 65
                        }

                    if (isSgr) {
                        val eventChar = if (event == com.johnan.terminal.core.MouseEvent.Release) 'm' else 'M'
                        val sgrButton = if (event == com.johnan.terminal.core.MouseEvent.Release) 0 else button
                        onInput("\u001B[<$sgrButton;$ansiCol;${ansiRow}$eventChar")
                    } else if (ansiCol <= 223 && ansiRow <= 223) {
                        val cb = (button + 32).toChar()
                        val cx = (ansiCol + 32).toChar()
                        val cy = (ansiRow + 32).toChar()
                        onInput("\u001B[M$cb$cx$cy")
                    }
                }
            }

        val effectiveOnMouseEvent = onMouseEvent ?: defaultOnMouseEvent

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
            cursorStyle = config.cursor.style,
            baseTextStyle = baseTextStyle,
            textMeasurer = textMeasurer,
            enabled = enabled,
            onRequestFocus = onRequestFocus,
            colorScheme = effectiveColorScheme,
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
            onMouseEvent = effectiveOnMouseEvent,
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
