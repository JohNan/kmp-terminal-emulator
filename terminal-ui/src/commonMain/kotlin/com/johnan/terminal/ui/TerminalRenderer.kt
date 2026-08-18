package com.johnan.terminal.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.core.TerminalScreenState

/**
 * Main Compose terminal emulator UI component rendering character grids, cursor, scrollback, and touch/keyboard input.
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
    colorScheme: TerminalColorScheme? = null,
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

    val cursorColor =
        config.cursor.overrideColor
            ?: effectiveColorScheme.cursor.copy(alpha = 0.5f)

    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val baseFontSize = if (fontSize != 14f) fontSize.sp else config.typography.fontSize
    val baseTextStyle =
        TextStyle(
            fontFamily = config.typography.fontFamily,
            fontSize = baseFontSize,
            letterSpacing = config.typography.letterSpacing,
            color = effectiveColorScheme.foreground,
        )

    val measurementString = "0".repeat(80)
    val cellMeasure = textMeasurer.measure(measurementString, baseTextStyle)
    val cellWidth = cellMeasure.size.width.toFloat() / 80f
    val cellHeight = cellMeasure.size.height.toFloat()

    LaunchedEffect(cellWidth, cellHeight) {
        onCellMeasured(cellWidth, cellHeight)
    }

    val allRows =
        remember(
            terminalState.rows,
            terminalState.scrollback,
        ) {
            VirtualTerminalList(terminalState.scrollback, terminalState.rows)
        }
    val scrollbackLineCount = terminalState.scrollback.size

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

        val totalHeight =
            with(density) {
                maxOf((allRows.size * cellHeight).toDp(), maxHeight)
            }

        val internalFocusRequester = focusRequester ?: remember { FocusRequester() }

        var localShowKeyboardSignal by remember { mutableIntStateOf(0) }
        val effectiveShowKeyboardSignal = showKeyboardSignal + localShowKeyboardSignal

        val onRequestFocus = {
            internalFocusRequester.requestFocus()
            keyboardController?.show()
            localShowKeyboardSignal++
            Unit
        }

        LaunchedEffect(enabled) {
            if (enabled) {
                onRequestFocus()
            }
        }

        LaunchedEffect(onRequestFocus) {
            onKeyboardToggleAvailable(onRequestFocus)
        }

        var shouldForceScrollToBottom by remember { mutableStateOf(false) }

        val cursorRow = terminalState.cursorRow
        val cursorVisible = terminalState.cursorVisible

        LaunchedEffect(
            cursorRow,
            scrollbackLineCount,
            cellHeight,
            maxHeightPx,
            cursorVisible,
            shouldForceScrollToBottom,
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

        val handleSoftInput: (String) -> Unit =
            remember(onInput, modifierKeyState, onModifierStateChange) {
                { input ->
                    shouldForceScrollToBottom = true

                    if (modifierKeyState.ctrlPressed || modifierKeyState.altPressed) {
                        val transformed = buildString(input.length) {
                            for (i in 0 until input.length) {
                                append(applyModifierToChar(input[i], modifierKeyState))
                            }
                        }
                        onInput(transformed)
                        onModifierStateChange(ModifierKeyState())
                    } else {
                        onInput(input)
                    }
                }
            }

        if (enabled && selectionState is SelectionState.None) {
            TerminalInputCapture(
                enabled = enabled,
                focusRequester = internalFocusRequester,
                showKeyboardSignal = effectiveShowKeyboardSignal,
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
 * Lightweight virtual view indexing scrollback and visible rows without ArrayList allocations.
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
