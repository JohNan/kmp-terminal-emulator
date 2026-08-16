package com.sshclient.composeapp.presentation.screens.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sshclient.data.terminal.MouseEvent
import com.sshclient.data.terminal.MouseTrackingMode
import com.sshclient.data.terminal.ScreenBuffer
import com.sshclient.data.terminal.TerminalCell
import com.sshclient.data.terminal.TerminalColor
import com.sshclient.data.terminal.TerminalScreenState
import com.sshclient.data.terminal.UrlRange
import com.sshclient.presentation.screens.terminal.SearchState
import com.sshclient.presentation.screens.terminal.SelectionState

/**
 * Terminal canvas component - renders the terminal display with scrolling and input capture
 *
 * Performance Note:
 * This component uses a manual batching strategy to minimize draw calls.
 * Characters with identical attributes (color, style) are grouped and drawn in single passes.
 * Text construction uses a reused StringBuilder to reduce allocation pressure.
 */
@Composable
fun TerminalCanvas(
    modifier: Modifier = Modifier,
    terminalState: TerminalScreenState,
    allRows: List<Array<TerminalCell>>,
    scrollbackLineCount: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    cellWidth: Float,
    cellHeight: Float,
    totalHeight: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
    cursorColor: Color,
    baseTextStyle: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    enabled: Boolean,
    onRequestFocus: () -> Unit,
    colorScheme: com.sshclient.domain.model.TerminalColorScheme? = null,
    selectionState: SelectionState = SelectionState.None,
    searchState: SearchState = SearchState(),
    // Copy mode gesture callbacks
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
    onMouseEvent: (MouseEvent, Int, Int) -> Unit = { _, _, _ -> },
    onLog: (String) -> Unit = {},
) {
    // Get terminal dimensions
    val terminalCols = terminalState.cols

    // Remember callback references
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnStartDraggingStartCursor by rememberUpdatedState(onStartDraggingStartCursor)
    val currentOnUpdateStartCursor by rememberUpdatedState(onUpdateStartCursor)
    val currentOnFinalizeStartCursor by rememberUpdatedState(onFinalizeStartCursor)
    val currentOnStartDraggingEndCursor by rememberUpdatedState(onStartDraggingEndCursor)
    val currentOnUpdateEndCursor by rememberUpdatedState(onUpdateEndCursor)
    val currentOnFinalizeEndCursorAndCopy by rememberUpdatedState(onFinalizeEndCursorAndCopy)
    val currentOnExitCopyMode by rememberUpdatedState(onExitCopyMode)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnUrlClick by rememberUpdatedState(onUrlClick)
    val currentOnMouseEvent by rememberUpdatedState(onMouseEvent)

    // Auto-scroll to search match
    LaunchedEffect(searchState.currentMatchIndex, searchState.isVisible) {
        if (searchState.isVisible && searchState.matches.isNotEmpty() && searchState.currentMatchIndex >= 0) {
            val match = searchState.matches.getOrNull(searchState.currentMatchIndex) ?: return@LaunchedEffect
            val row = match.startRow

            if (scrollState.maxValue > 0) {
                // Calculate approximate viewport height based on content height and max scroll
                val totalContentHeight = allRows.size * cellHeight
                val viewportHeight = totalContentHeight - scrollState.maxValue

                val matchY = row * cellHeight
                // Center the match
                val targetScroll = (matchY - (viewportHeight / 2)).toInt()
                scrollState.animateScrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
            }
        }
    }

    // Reuse StringBuilder for batch calculation to avoid allocations on cache miss
    val stringBuilder = remember { StringBuilder() }

    // Optimization: Reuse ArrayList for batch calculation to avoid allocations on cache miss
    val batchListBuilder = remember { ArrayList<RenderBatch>() }

    // Optimization: Cache render batches and measured text layout to avoid recalculating on every draw.
    // Using a LinkedHashMap with LRU eviction prevents memory leaks while keeping recent rows ready.
    // Invalidating on colorScheme/isDark/baseTextStyle changes ensures colors and fonts are correct.
    val renderBatchCache = remember(colorScheme, isDark, baseTextStyle) {
        mutableMapOf<Array<TerminalCell>, RowRenderData>()
    }

    // Optimization: Pre-calculate standard ANSI colors to avoid expensive lookup/conversion in the draw loop
    val ansiColors = remember(colorScheme, isDark) {
        calculateAnsiColors(colorScheme, isDark)
    }

    val contentHeight = allRows.size * cellHeight
    val totalHeightPx = with(LocalDensity.current) { totalHeight.toPx() }
    val verticalOffset = 0f

    val isCopyMode = selectionState !is SelectionState.None

    // Optimization: Calculate terminal background once to use for both container background
    // and overdraw prevention check.
    val terminalBackgroundColor =
        colorScheme?.background
            ?: (if (isDark) Color(0xFF0D1117) else Color(0xFF1E1E1E))

    val currentTerminalCols by rememberUpdatedState(terminalCols)
    val currentTotalRows by rememberUpdatedState(allRows.size)
    val currentScrollbackLineCount by rememberUpdatedState(scrollbackLineCount)
    val currentCellWidth by rememberUpdatedState(cellWidth)
    val currentCellHeight by rememberUpdatedState(cellHeight)
    val currentVerticalOffset by rememberUpdatedState(verticalOffset)

    // Scrollable container with terminal background from color scheme
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .semantics { contentDescription = "Terminal canvas" }
                .verticalScroll(
                    scrollState,
                    enabled =
                        selectionState is SelectionState.None ||
                            selectionState is SelectionState.CopyModeActive ||
                            selectionState is SelectionState.SelectionComplete,
                )
                .background(terminalBackgroundColor)
                // Standard mode gestures - only active when NOT in copy mode
                .pointerInput(isCopyMode, terminalState.mouseTrackingMode) {
                    if (isCopyMode) return@pointerInput

                    if (terminalState.mouseTrackingMode != MouseTrackingMode.None) {
                        // Mouse tracking active - consume taps and drags
                        awaitPointerEventScope {
                            var lastRow = -1
                            var lastCol = -1
                            var startOffsetY = 0f
                            var accumulatedDeltaY = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val offset = change.position
                                val (row, col) =
                                    TerminalCoordinateConverter.screenToTerminal(
                                        offset = offset,
                                        cellWidth = currentCellWidth,
                                        cellHeight = currentCellHeight,
                                        scrollbackLineCount = currentScrollbackLineCount,
                                        totalRows = currentTotalRows,
                                        cols = currentTerminalCols,
                                        verticalOffset = currentVerticalOffset,
                                    )

                                val visibleRow = row - currentScrollbackLineCount
                                if (visibleRow in 0 until terminalState.rows.size) {
                                    when (event.type) {
                                        androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                            startOffsetY = offset.y
                                            accumulatedDeltaY = 0f
                                            currentOnMouseEvent(MouseEvent.Press, visibleRow, col)
                                        }
                                        androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                            lastRow = -1
                                            lastCol = -1
                                            accumulatedDeltaY = 0f
                                            currentOnMouseEvent(MouseEvent.Release, visibleRow, col)
                                        }
                                        androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                            if (change.pressed) {
                                                val deltaY = offset.y - startOffsetY
                                                startOffsetY = offset.y
                                                accumulatedDeltaY += deltaY
                                                val threshold = currentCellHeight.coerceAtLeast(10f)

                                                if (accumulatedDeltaY >= threshold) {
                                                    // Scrolled downward -> Wheel Up in terminal apps (e.g. tmux/vim/less)
                                                    val steps = (accumulatedDeltaY / threshold).toInt()
                                                    accumulatedDeltaY -= steps * threshold
                                                    repeat(steps) {
                                                        currentOnMouseEvent(MouseEvent.WheelUp, visibleRow, col)
                                                    }
                                                } else if (accumulatedDeltaY <= -threshold) {
                                                    // Scrolled upward -> Wheel Down
                                                    val steps = (-accumulatedDeltaY / threshold).toInt()
                                                    accumulatedDeltaY += steps * threshold
                                                    repeat(steps) {
                                                        currentOnMouseEvent(MouseEvent.WheelDown, visibleRow, col)
                                                    }
                                                }

                                                if (terminalState.mouseTrackingMode != MouseTrackingMode.Click) {
                                                    if (visibleRow != lastRow || col != lastCol) {
                                                        currentOnMouseEvent(MouseEvent.Drag, visibleRow, col)
                                                        lastRow = visibleRow
                                                        lastCol = col
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Consume event to prevent native actions
                                change.consume()
                            }
                        }
                    } else {
                        // Native mode gestures
                        detectTapGestures(
                            onTap = { offset ->
                                // Ensure input has focus and keyboard is visible
                                onRequestFocus()

                                val (row, col) =
                                    TerminalCoordinateConverter.screenToTerminal(
                                        offset = offset,
                                        cellWidth = currentCellWidth,
                                        cellHeight = currentCellHeight,
                                        scrollbackLineCount = currentScrollbackLineCount,
                                        totalRows = currentTotalRows,
                                        cols = currentTerminalCols,
                                        verticalOffset = currentVerticalOffset,
                                    )

                                // Check for URL click
                                val clickedRowData = allRows.getOrNull(row)
                                if (clickedRowData != null) {
                                    val ranges = ScreenBuffer.getUrlRanges(clickedRowData)
                                    val clickedRange =
                                        ranges.find { range ->
                                            col >= range.startCol && col < range.endCol
                                        }
                                    if (clickedRange != null) {
                                        val url = ScreenBuffer.extractUrl(clickedRowData, clickedRange)
                                        currentOnUrlClick(url)
                                    }
                                }
                            },
                            onLongPress = { offset ->
                                val (row, col) =
                                    TerminalCoordinateConverter.screenToTerminal(
                                        offset = offset,
                                        cellWidth = currentCellWidth,
                                        cellHeight = currentCellHeight,
                                        scrollbackLineCount = currentScrollbackLineCount,
                                        totalRows = currentTotalRows,
                                        cols = currentTerminalCols,
                                        verticalOffset = currentVerticalOffset,
                                    )
                                currentOnLongPress(offset, row, col)
                            },
                        )
                    }
                }
                // Selection gestures - only active in copy mode
                .then(
                    if (isCopyMode) {
                        Modifier.terminalGestures(
                            selectionState = selectionState,
                            terminalCols = terminalCols,
                            allRows = allRows,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            scrollbackLineCount = scrollbackLineCount,
                            verticalOffset = verticalOffset,
                            onSingleTap = currentOnSingleTap,
                            onStartDraggingStartCursor = currentOnStartDraggingStartCursor,
                            onUpdateStartCursor = currentOnUpdateStartCursor,
                            onFinalizeStartCursor = currentOnFinalizeStartCursor,
                            onStartDraggingEndCursor = currentOnStartDraggingEndCursor,
                            onUpdateEndCursor = currentOnUpdateEndCursor,
                            onFinalizeEndCursor = currentOnFinalizeEndCursorAndCopy,
                            onExitCopyMode = currentOnExitCopyMode,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        // Terminal canvas rendering
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(totalHeight),
        ) {
            // Viewport-aware rendering: only draw visible rows
            val viewportTop = scrollState.value.toFloat()
            val viewportBottom = viewportTop + size.height

            val firstVisibleRow = (viewportTop / cellHeight).toInt().coerceIn(0, allRows.size)
            val lastVisibleRow = ((viewportBottom / cellHeight).toInt() + 1).coerceIn(0, allRows.size)

            val verticalOffsetCanvas = 0f

            for (rowIndex in firstVisibleRow until lastVisibleRow) {
                val row = allRows.getOrNull(rowIndex) ?: continue
                val y = rowIndex * cellHeight + verticalOffsetCanvas

                // Get render data from cache (or calculate)
                val cachedData = renderBatchCache[row] ?: run {
                    batchListBuilder.clear()
                    calculateBatches(row, stringBuilder, batchListBuilder)
                    // Optimization: Pre-resolve colors to avoid expensive lookup/conversion in the draw loop.
                    // Bolt: Use indexed loops instead of .forEach to avoid Iterator allocations on the hot path.
                    for (i in 0 until batchListBuilder.size) {
                        resolveBatchColors(
                            batchListBuilder[i],
                            ansiColors,
                            baseTextStyle,
                            terminalBackgroundColor,
                            colorScheme,
                            isDark
                        )
                    }

                    val urls = ScreenBuffer.getUrlRanges(row)
                    RowRenderData(batchListBuilder.toList(), urls).also {
                        if (renderBatchCache.size >= 200) {
                            val oldestKey = renderBatchCache.keys.firstOrNull()
                            if (oldestKey != null) {
                                renderBatchCache.remove(oldestKey)
                            }
                        }
                        renderBatchCache[row] = it
                    }
                }

                // Draw URL underlines
                // Bolt: Use indexed loops instead of .forEach to avoid Iterator allocations on the hot path.
                for (i in 0 until cachedData.urls.size) {
                    val range = cachedData.urls[i]
                    val startX = range.startCol * cellWidth
                    val endX = range.endCol * cellWidth
                    val underlineY = y + cellHeight - 2f

                    drawLine(
                        color = Color(0xFF448AFF),
                        start = Offset(startX, underlineY),
                        end = Offset(endX, underlineY),
                        strokeWidth = 2f,
                    )
                }

                // Bolt: Use indexed loops instead of .forEach to avoid Iterator allocations on the hot path.
                for (i in 0 until cachedData.batches.size) {
                    val batch = cachedData.batches[i]
                    val x = batch.startCol * cellWidth
                    val batchWidth = batch.length * cellWidth

                    // Draw Background
                    val bgColor = batch.resolvedBg
                    // Optimization: Avoid overdraw if the batch background matches the terminal background
                    if (bgColor != Color.Transparent &&
                        bgColor != terminalBackgroundColor &&
                        bgColor != Color.Unspecified
                    ) {
                        drawRect(
                            color = bgColor,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(batchWidth, cellHeight),
                        )
                    }

                    // Draw Text
                    if (batch.text.isNotEmpty()) {
                        // Optimization: Cache TextLayoutResult to avoid expensive measurement on every frame
                        val layoutResult = batch.textLayoutResult ?: run {
                            // Optimization: Use pre-resolved colors
                            val textStyle =
                                if (batch.fgColor === TerminalColor.Default &&
                                    !batch.bold &&
                                    !batch.underline &&
                                    !batch.reverse &&
                                    !batch.strikethrough &&
                                    !batch.conceal
                                ) {
                                    baseTextStyle
                                } else {
                                    val decorations = mutableListOf<TextDecoration>()
                                    if (batch.underline) decorations.add(TextDecoration.Underline)
                                    if (batch.strikethrough) decorations.add(TextDecoration.LineThrough)
                                    val textDecoration =
                                        if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else null

                                    baseTextStyle.copy(
                                        color = if (batch.conceal) Color.Transparent else batch.resolvedFg,
                                        fontWeight = if (batch.bold) FontWeight.Bold else FontWeight.Normal,
                                        textDecoration = textDecoration,
                                    )
                                }

                            textMeasurer.measure(
                                text = batch.text,
                                style = textStyle
                            ).also { batch.textLayoutResult = it }
                        }

                        drawText(
                            textLayoutResult = layoutResult,
                            topLeft = Offset(x, y),
                        )
                    }

                    // Draw overline
                    if (batch.overline) {
                        val overlineColor = if (batch.conceal) Color.Transparent else batch.resolvedFg
                        if (overlineColor != Color.Transparent) {
                            drawRect(
                                color = overlineColor,
                                topLeft = Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(batchWidth, 1.dp.toPx()),
                            )
                        }
                    }
                }
            }

            if (terminalState.cursorVisible && enabled) {
                val cursorX = terminalState.cursorCol * cellWidth
                val cursorY = (terminalState.cursorRow + scrollbackLineCount) * cellHeight + verticalOffsetCanvas

                drawRect(
                    color = cursorColor,
                    topLeft = Offset(cursorX, cursorY),
                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                )
            }
        }

        // Selection overlay
        SelectionOverlay(
            selectionState = selectionState,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            scrollbackLineCount = scrollbackLineCount,
            totalRows = allRows.size,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(totalHeight),
        )

        // Search overlay
        SearchOverlay(
            searchState = searchState,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            scrollbackLineCount = scrollbackLineCount,
            totalRows = allRows.size,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(totalHeight),
        )
    }
}

/**
 * Pre-calculated rendering information for a group of cells.
 */
data class RenderBatch(
    val startCol: Int,
    val length: Int,
    val text: String,
    val fgColor: TerminalColor,
    val bgColor: TerminalColor,
    val bold: Boolean,
    val underline: Boolean,
    val reverse: Boolean,
    val strikethrough: Boolean,
    val overline: Boolean,
    val conceal: Boolean,
) {
    var textLayoutResult: TextLayoutResult? = null
    var resolvedFg: Color = Color.Unspecified
    var resolvedBg: Color = Color.Unspecified
}

data class RowRenderData(
    val batches: List<RenderBatch>,
    val urls: List<UrlRange>,
)

// Optimization: Pre-allocate a string of spaces to avoid repeated appends in loops
private val SPACES = " ".repeat(1024)

/**
 * Calculates render batches for a row.
 * Group cells with identical attributes (color, style) to minimize draw calls.
 *
 * Optimization: Uses a Lazy Backfill approach (Single Pass) to improve performance.
 * Pass 1: Scans row. If a non-space char is found, switches to "Build Mode".
 * If "Build Mode" is triggered, backfills preceding spaces (known to be spaces) and continues building.
 * This avoids the second pass for content-heavy rows while keeping whitespace rows efficient.
 */
private fun calculateAnsiColors(
    colorScheme: com.sshclient.domain.model.TerminalColorScheme?,
    isDark: Boolean,
): Array<Color> {
    return Array(16) { code ->
        TerminalColor.toComposeColor(
            TerminalColor.Standard(code),
            colorScheme,
            isDark,
            isBackground = false,
        )
    }
}

fun calculateBatches(row: Array<TerminalCell>, sb: StringBuilder, batches: ArrayList<RenderBatch>) {
    var colIndex = 0
    val rowSize = row.size

    while (colIndex < rowSize) {
        val startCol = colIndex
        val cell = row[colIndex]

        val currentBg = cell.backgroundColor
        val currentFg = cell.foregroundColor
        val currentBold = cell.bold
        val currentUnderline = cell.underline
        val currentReverse = cell.reverse
        val currentStrikethrough = cell.strikethrough
        val currentOverline = cell.overline
        val currentConceal = cell.conceal

        var k = colIndex + 1

        if (cell.char != ' ') {
            // Path A: Content Start - optimized loop for content-heavy batches
            sb.setLength(0)
            sb.append(cell.char)

            while (k < rowSize) {
                val nextCell = row[k]

                if (nextCell !== cell && (
                        nextCell.backgroundColor !== currentBg ||
                            nextCell.foregroundColor !== currentFg ||
                            nextCell.bold != currentBold ||
                            nextCell.underline != currentUnderline ||
                            nextCell.reverse != currentReverse ||
                            nextCell.strikethrough != currentStrikethrough ||
                            nextCell.overline != currentOverline ||
                            nextCell.conceal != currentConceal
                    )
                ) {
                    break
                }
                sb.append(nextCell.char)
                k++
            }

            batches.add(
                RenderBatch(
                    startCol = startCol,
                    length = k - startCol,
                    text = sb.toString(),
                    fgColor = currentFg,
                    bgColor = currentBg,
                    bold = currentBold,
                    underline = currentUnderline,
                    reverse = currentReverse,
                    strikethrough = currentStrikethrough,
                    overline = currentOverline,
                    conceal = currentConceal,
                )
            )
        } else {
            // Path B: Space Start - optimized scanning loop
            var switchedToBuild = false

            while (k < rowSize) {
                val nextCell = row[k]

                if (nextCell !== cell && (
                        nextCell.backgroundColor !== currentBg ||
                            nextCell.foregroundColor !== currentFg ||
                            nextCell.bold != currentBold ||
                            nextCell.underline != currentUnderline ||
                            nextCell.reverse != currentReverse ||
                            nextCell.strikethrough != currentStrikethrough ||
                            nextCell.overline != currentOverline ||
                            nextCell.conceal != currentConceal
                    )
                ) {
                    break
                }

                if (nextCell.char != ' ') {
                    switchedToBuild = true
                    break
                }
                k++
            }

            if (switchedToBuild) {
                // Found content at k, backfill spaces and switch to build mode
                sb.setLength(0)
                val count = k - startCol

                // Efficiently backfill spaces
                var remaining = count
                while (remaining > 0) {
                    val chunk = minOf(remaining, SPACES.length)
                    sb.append(SPACES, 0, chunk)
                    remaining -= chunk
                }

                // Continue building from k
                while (k < rowSize) {
                    val nextCell = row[k]
                    if (nextCell !== cell && (
                            nextCell.backgroundColor !== currentBg ||
                                nextCell.foregroundColor !== currentFg ||
                                nextCell.bold != currentBold ||
                                nextCell.underline != currentUnderline ||
                                nextCell.reverse != currentReverse ||
                                nextCell.strikethrough != currentStrikethrough ||
                                nextCell.overline != currentOverline ||
                                nextCell.conceal != currentConceal
                        )
                    ) {
                        break
                    }
                    sb.append(nextCell.char)
                    k++
                }

                batches.add(
                    RenderBatch(
                        startCol = startCol,
                        length = k - startCol,
                        text = sb.toString(),
                        fgColor = currentFg,
                        bgColor = currentBg,
                        bold = currentBold,
                        underline = currentUnderline,
                        reverse = currentReverse,
                        strikethrough = currentStrikethrough,
                        overline = currentOverline,
                        conceal = currentConceal,
                    )
                )
            } else {
                // Batch ended without content (empty batch)
                // Optimization: Skip completely invisible batches to avoid object allocation and draw overhead.
                // An empty batch with default background, no reverse video, and no underline is completely invisible.
                // Bolt: Use fast-path reference equality (===) since TerminalColor.Default is a data object.
                // Strikethrough and overline might also be visible on empty spaces depending on terminal emulator.
                if (currentBg !== TerminalColor.Default || currentReverse || currentUnderline ||
                    currentStrikethrough || currentOverline
                ) {
                    batches.add(
                        RenderBatch(
                            startCol = startCol,
                            length = k - startCol,
                            text = "",
                            fgColor = currentFg,
                            bgColor = currentBg,
                            bold = currentBold,
                            underline = currentUnderline,
                            reverse = currentReverse,
                            strikethrough = currentStrikethrough,
                            overline = currentOverline,
                            conceal = currentConceal,
                        )
                    )
                }
            }
        }
        colIndex = k
    }
}

fun resolveBatchColors(
    batch: RenderBatch,
    ansiColors: Array<Color>,
    baseTextStyle: TextStyle,
    terminalBackgroundColor: Color,
    colorScheme: com.sshclient.domain.model.TerminalColorScheme?,
    isDark: Boolean
) {
    val isReverse = batch.reverse
    val rawFg = batch.fgColor
    val rawBg = batch.bgColor

    // Determine effective colors to draw
    val effectiveFg = if (isReverse) rawBg else rawFg
    val effectiveBg = if (isReverse) rawFg else rawBg

    // Resolve Foreground
    // Bolt: Use fast-path reference equality (===) since TerminalColor.Default is a data object
    batch.resolvedFg = if (effectiveFg === TerminalColor.Default) {
        if (isReverse) terminalBackgroundColor else baseTextStyle.color
    } else if (effectiveFg is TerminalColor.Standard) {
        ansiColors[effectiveFg.code]
    } else {
        TerminalColor.toComposeColor(
            effectiveFg,
            colorScheme,
            isDark,
            isBackground = false,
        )
    }

    // Resolve Background
    // Bolt: Use fast-path reference equality (===) since TerminalColor.Default is a data object
    batch.resolvedBg = if (effectiveBg === TerminalColor.Default) {
        if (isReverse) baseTextStyle.color else Color.Transparent
    } else if (effectiveBg is TerminalColor.Standard) {
        ansiColors[effectiveBg.code]
    } else {
        TerminalColor.toComposeColor(
            effectiveBg,
            colorScheme,
            isDark,
            isBackground = true,
        )
    }
}
