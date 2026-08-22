package com.johnan.terminal.ui

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.johnan.terminal.core.MouseEvent
import com.johnan.terminal.core.MouseTrackingMode
import com.johnan.terminal.core.ScreenBuffer
import com.johnan.terminal.core.SearchState
import com.johnan.terminal.core.SelectionState
import com.johnan.terminal.core.TerminalCell
import com.johnan.terminal.core.TerminalColor
import com.johnan.terminal.core.TerminalScreenState
import com.johnan.terminal.core.UrlRange

/**
 * Renders terminal character grid cells, cursor styles, highlights, and gesture overlays via hardware-accelerated Canvas.
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
    cursorStyle: TerminalCursorStyle = TerminalCursorStyle.BLOCK,
    baseTextStyle: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    enabled: Boolean,
    onRequestFocus: () -> Unit,
    colorScheme: com.johnan.terminal.core.TerminalColorScheme? = null,
    selectionState: SelectionState = SelectionState.None,
    searchState: SearchState = SearchState(),
    touchScrollSendsWheelOnly: Boolean = false,
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
    val currentTouchScrollSendsWheelOnly by rememberUpdatedState(touchScrollSendsWheelOnly)

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
                .pointerInput(isCopyMode, terminalState.mouseTrackingMode, touchScrollSendsWheelOnly) {
                    if (isCopyMode) return@pointerInput

                    if (terminalState.mouseTrackingMode != MouseTrackingMode.None) {
                        // Mouse tracking active - consume taps and drags
                        awaitPointerEventScope {
                            var lastRow = -1
                            var lastCol = -1
                            var startOffsetY = 0f
                            var accumulatedDeltaY = 0f
                            var initialPressX = 0f
                            var initialPressY = 0f
                            var pressVisibleRow = -1
                            var pressCol = -1
                            var isScrolling = false

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
                                            initialPressX = offset.x
                                            initialPressY = offset.y
                                            accumulatedDeltaY = 0f
                                            pressVisibleRow = visibleRow
                                            pressCol = col
                                            isScrolling = false
                                            if (!currentTouchScrollSendsWheelOnly) {
                                                currentOnMouseEvent(MouseEvent.Press, visibleRow, col)
                                            }
                                        }
                                        androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                            if (currentTouchScrollSendsWheelOnly) {
                                                if (!isScrolling && pressVisibleRow >= 0 && pressCol >= 0) {
                                                    currentOnMouseEvent(MouseEvent.Press, pressVisibleRow, pressCol)
                                                    currentOnMouseEvent(MouseEvent.Release, pressVisibleRow, pressCol)
                                                }
                                                isScrolling = false
                                                pressVisibleRow = -1
                                                pressCol = -1
                                                accumulatedDeltaY = 0f
                                            } else {
                                                lastRow = -1
                                                lastCol = -1
                                                accumulatedDeltaY = 0f
                                                currentOnMouseEvent(MouseEvent.Release, visibleRow, col)
                                            }
                                        }
                                        androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                            if (change.pressed) {
                                                val deltaY = offset.y - startOffsetY
                                                startOffsetY = offset.y
                                                accumulatedDeltaY += deltaY
                                                val threshold = currentCellHeight.coerceAtLeast(10f)

                                                if (currentTouchScrollSendsWheelOnly) {
                                                    if (kotlin.math.abs(offset.y - initialPressY) >= threshold ||
                                                        kotlin.math.abs(offset.x - initialPressX) >= threshold
                                                    ) {
                                                        isScrolling = true
                                                    }

                                                    if (accumulatedDeltaY >= threshold) {
                                                        val steps = (accumulatedDeltaY / threshold).toInt()
                                                        accumulatedDeltaY -= steps * threshold
                                                        repeat(steps) {
                                                            currentOnMouseEvent(MouseEvent.WheelUp, visibleRow, col)
                                                        }
                                                    } else if (accumulatedDeltaY <= -threshold) {
                                                        val steps = (-accumulatedDeltaY / threshold).toInt()
                                                        accumulatedDeltaY += steps * threshold
                                                        repeat(steps) {
                                                            currentOnMouseEvent(MouseEvent.WheelDown, visibleRow, col)
                                                        }
                                                    }
                                                } else {
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
                    for (i in 0 until batchListBuilder.size) {
                        resolveBatchColors(
                            batchListBuilder[i],
                            ansiColors,
                            baseTextStyle,
                            terminalBackgroundColor,
                            colorScheme,
                            isDark,
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

                        // Optimization: Cache TextLayoutResult for each character to avoid expensive measurement on every frame
                        val layoutResults = batch.charLayoutResults ?: run {
                            val results = arrayOfNulls<TextLayoutResult>(batch.text.length)
                            for (j in 0 until batch.text.length) {
                                val c = batch.text[j]
                                val isBox = c.code in 0x2500..0x257F
                                val isBlock = c.code in 0x2580..0x259F
                                if (!isBox && !isBlock && c != ' ') {
                                    results[j] = textMeasurer.measure(
                                        text = c.toString(),
                                        style = textStyle
                                    )
                                }
                            }
                            batch.charLayoutResults = results
                            results
                        }

                        // Draw each character at its exact grid offset
                        val textColor = if (batch.conceal) Color.Transparent else batch.resolvedFg
                        val strokeWidthLight = 1.dp.toPx()
                        val strokeWidthHeavy = 2.5.dp.toPx()

                        for (j in 0 until batch.text.length) {
                            val c = batch.text[j]
                            val charX = x + j * cellWidth

                            val isBox = c.code in 0x2500..0x257F
                            val isBlock = c.code in 0x2580..0x259F

                            if (isBox) {
                                drawBoxDrawing(
                                    c,
                                    charX,
                                    y,
                                    cellWidth,
                                    cellHeight,
                                    textColor,
                                    strokeWidthLight,
                                    strokeWidthHeavy
                                )
                            } else if (isBlock) {
                                drawBlockElement(c, charX, y, cellWidth, cellHeight, textColor)
                            } else if (c != ' ') {
                                val charLayout = layoutResults.getOrNull(j)
                                if (charLayout != null) {
                                    drawText(
                                        textLayoutResult = charLayout,
                                        topLeft = Offset(charX, y),
                                    )
                                }
                            }
                        }
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

                when (cursorStyle) {
                    TerminalCursorStyle.BLOCK -> {
                        drawRect(
                            color = cursorColor,
                            topLeft = Offset(cursorX, cursorY),
                            size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                        )
                    }
                    TerminalCursorStyle.UNDERLINE -> {
                        val underlineHeight = 2.dp.toPx().coerceAtLeast(1f)
                        drawRect(
                            color = cursorColor,
                            topLeft = Offset(cursorX, cursorY + cellHeight - underlineHeight),
                            size = androidx.compose.ui.geometry.Size(cellWidth, underlineHeight),
                        )
                    }
                    TerminalCursorStyle.BEAM -> {
                        val beamWidth = 2.dp.toPx().coerceAtLeast(1f)
                        drawRect(
                            color = cursorColor,
                            topLeft = Offset(cursorX, cursorY),
                            size = androidx.compose.ui.geometry.Size(beamWidth, cellHeight),
                        )
                    }
                }
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
 * Batch of contiguous characters sharing identical styling and color attributes.
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
    var charLayoutResults: Array<TextLayoutResult?>? = null
    var resolvedFg: Color = Color.Unspecified
    var resolvedBg: Color = Color.Unspecified
}

data class RowRenderData(
    val batches: List<RenderBatch>,
    val urls: List<UrlRange>,
)

private val SPACES = " ".repeat(1024)

private fun calculateAnsiColors(
    colorScheme: com.johnan.terminal.core.TerminalColorScheme?,
    isDark: Boolean,
): Array<Color> = Array(16) { code ->
    TerminalColor.toComposeColor(
        TerminalColor.Standard(code),
        colorScheme,
        isDark,
        isBackground = false,
    )
}

/**
 * Groups contiguous row cells with matching visual attributes into render batches.
 */
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

                if (nextCell !== cell &&
                    (
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

                if (nextCell !== cell &&
                    (
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
                    if (nextCell !== cell &&
                        (
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
                if (currentBg !== TerminalColor.Default ||
                    currentReverse ||
                    currentUnderline ||
                    currentStrikethrough ||
                    currentOverline
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
    colorScheme: com.johnan.terminal.core.TerminalColorScheme?,
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

private enum class LineType { NONE, LIGHT, HEAVY, DOUBLE }

private fun DrawScope.drawBoxDrawing(
    c: Char,
    x: Float,
    y: Float,
    cellWidth: Float,
    cellHeight: Float,
    color: Color,
    strokeWidthLight: Float,
    strokeWidthHeavy: Float
) {
    val centerX = x + cellWidth / 2f
    val centerY = y + cellHeight / 2f

    // Determine Top, Bottom, Left, Right line styles
    var top = LineType.NONE
    var bottom = LineType.NONE
    var left = LineType.NONE
    var right = LineType.NONE

    when (c) {
        // Horizontal
        '\u2500', '─' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u2501', '━' -> {
            left = LineType.HEAVY
            right = LineType.HEAVY
        }
        // Vertical
        '\u2502', '│' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
        }
        '\u2503', '┃' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
        }
        // Dashed horizontal
        '\u2504', '┄', '\u2508', '┈', '\u254c', '╌' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u2505', '┅', '\u2509', '┉', '\u254d', '╍' -> {
            left = LineType.HEAVY
            right = LineType.HEAVY
        }
        // Dashed vertical
        '\u2506', '┆', '\u250a', '┊', '\u254e', '╎' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
        }
        '\u2507', '┇', '\u250a', '┋', '\u254e', '╏' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
        }

        // Corners: Down and Right
        '\u250c', '┌' -> {
            bottom = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u250d', '┍' -> {
            bottom = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u250e', '┎' -> {
            bottom = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u250f', '┏' -> {
            bottom = LineType.HEAVY
            right = LineType.HEAVY
        }

        // Corners: Down and Left
        '\u2510', '┐' -> {
            bottom = LineType.LIGHT
            left = LineType.LIGHT
        }
        '\u2511', '┑' -> {
            bottom = LineType.LIGHT
            left = LineType.HEAVY
        }
        '\u2512', '┒' -> {
            bottom = LineType.HEAVY
            left = LineType.LIGHT
        }
        '\u2513', '┓' -> {
            bottom = LineType.HEAVY
            left = LineType.HEAVY
        }

        // Corners: Up and Right
        '\u2514', '└' -> {
            top = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u2515', '┕' -> {
            top = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u2516', '┖' -> {
            top = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u2517', '┗' -> {
            top = LineType.HEAVY
            right = LineType.HEAVY
        }

        // Corners: Up and Left
        '\u2518', '┘' -> {
            top = LineType.LIGHT
            left = LineType.LIGHT
        }
        '\u2519', '┙' -> {
            top = LineType.LIGHT
            left = LineType.HEAVY
        }
        '\u251a', '┚' -> {
            top = LineType.HEAVY
            left = LineType.LIGHT
        }
        '\u251b', '┛' -> {
            top = LineType.HEAVY
            left = LineType.HEAVY
        }

        // Junctions: Vertical and Right
        '\u251c', '├' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u251d', '┝' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u251e', '┞' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u251f', '┟' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u2520', '┠' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u2521', '┡' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u2522', '┢' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            right = LineType.HEAVY
        }
        '\u2523', '┣' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            right = LineType.HEAVY
        }

        // Junctions: Vertical and Left
        '\u2524', '┤' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.LIGHT
        }
        '\u2525', '┥' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.HEAVY
        }
        '\u2526', '┦' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            left = LineType.LIGHT
        }
        '\u2527', '┧' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            left = LineType.LIGHT
        }
        '\u2528', '┨' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            left = LineType.LIGHT
        }
        '\u2529', '┩' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            left = LineType.HEAVY
        }
        '\u252a', '┪' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            left = LineType.HEAVY
        }
        '\u252b', '┫' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            left = LineType.HEAVY
        }

        // Junctions: Horizontal and Down
        '\u252c', '┬' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
            bottom = LineType.LIGHT
        }
        '\u252d', '┭' -> {
            left = LineType.LIGHT
            right = LineType.HEAVY
            bottom = LineType.LIGHT
        }
        '\u252e', '┮' -> {
            left = LineType.HEAVY
            right = LineType.LIGHT
            bottom = LineType.LIGHT
        }
        '\u252f', '┯' -> {
            left = LineType.HEAVY
            right = LineType.HEAVY
            bottom = LineType.LIGHT
        }
        '\u2530', '┰' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
            bottom = LineType.HEAVY
        }
        '\u2531', '┱' -> {
            left = LineType.LIGHT
            right = LineType.HEAVY
            bottom = LineType.HEAVY
        }
        '\u2532', '┲' -> {
            left = LineType.HEAVY
            right = LineType.LIGHT
            bottom = LineType.HEAVY
        }
        '\u2533', '┳' -> {
            left = LineType.HEAVY
            right = LineType.HEAVY
            bottom = LineType.HEAVY
        }

        // Junctions: Horizontal and Up
        '\u2534', '┴' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
            top = LineType.LIGHT
        }
        '\u2535', '┵' -> {
            left = LineType.LIGHT
            right = LineType.HEAVY
            top = LineType.LIGHT
        }
        '\u2536', '┶' -> {
            left = LineType.HEAVY
            right = LineType.LIGHT
            top = LineType.LIGHT
        }
        '\u2537', '┷' -> {
            left = LineType.HEAVY
            right = LineType.HEAVY
            top = LineType.LIGHT
        }
        '\u2538', '┸' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
            top = LineType.HEAVY
        }
        '\u2539', '┹' -> {
            left = LineType.LIGHT
            right = LineType.HEAVY
            top = LineType.HEAVY
        }
        '\u253a', '┺' -> {
            left = LineType.HEAVY
            right = LineType.LIGHT
            top = LineType.HEAVY
        }
        '\u253b', '┻' -> {
            left = LineType.HEAVY
            right = LineType.HEAVY
            top = LineType.HEAVY
        }

        // Junctions: Vertical and Horizontal
        '\u253c', '┼' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u253d', '┽' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u253e', '┾' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u253f', '┿' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.HEAVY
            right = LineType.HEAVY
        }
        '\u2540', '╀' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            left = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u2541', '╁' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            left = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u2542', '╂' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            left = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u2543', '╃' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            left = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u2544', '╄' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            left = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u2545', '╅' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            left = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u2546', '╆' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            left = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u2547', '╇' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            left = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u2548', '╈' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            left = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u2549', '╉' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
            left = LineType.HEAVY
            right = LineType.HEAVY
        }
        '\u254a', '╊' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
            left = LineType.HEAVY
            right = LineType.HEAVY
        }
        '\u254b', '╋' -> {
            top = LineType.HEAVY
            bottom = LineType.HEAVY
            left = LineType.HEAVY
            right = LineType.HEAVY
        }

        // Double lines and mixed double/single lines
        '\u2550', '═' -> {
            left = LineType.DOUBLE
            right = LineType.DOUBLE
        }
        '\u2551', '║' -> {
            top = LineType.DOUBLE
            bottom = LineType.DOUBLE
        }
        '\u2552', '╒' -> {
            bottom = LineType.LIGHT
            right = LineType.DOUBLE
        }
        '\u2553', '╓' -> {
            bottom = LineType.DOUBLE
            right = LineType.LIGHT
        }
        '\u2554', '╔' -> {
            bottom = LineType.DOUBLE
            right = LineType.DOUBLE
        }
        '\u2555', '╕' -> {
            bottom = LineType.LIGHT
            left = LineType.DOUBLE
        }
        '\u2556', '╖' -> {
            bottom = LineType.DOUBLE
            left = LineType.LIGHT
        }
        '\u2557', '╗' -> {
            bottom = LineType.DOUBLE
            left = LineType.DOUBLE
        }
        '\u2558', '╘' -> {
            top = LineType.LIGHT
            right = LineType.DOUBLE
        }
        '\u2559', '╙' -> {
            top = LineType.DOUBLE
            right = LineType.LIGHT
        }
        '\u255a', '╚' -> {
            top = LineType.DOUBLE
            right = LineType.DOUBLE
        }
        '\u255b', '╛' -> {
            top = LineType.LIGHT
            left = LineType.DOUBLE
        }
        '\u255c', '╜' -> {
            top = LineType.DOUBLE
            left = LineType.LIGHT
        }
        '\u255d', '╝' -> {
            top = LineType.DOUBLE
            left = LineType.DOUBLE
        }
        '\u255e', '╞' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            right = LineType.DOUBLE
        }
        '\u255f', '╟' -> {
            top = LineType.DOUBLE
            bottom = LineType.DOUBLE
            right = LineType.LIGHT
        }
        '\u2560', '╠' -> {
            top = LineType.DOUBLE
            bottom = LineType.DOUBLE
            right = LineType.DOUBLE
        }
        '\u2561', '╡' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.DOUBLE
        }
        '\u2562', '╢' -> {
            top = LineType.DOUBLE
            bottom = LineType.DOUBLE
            left = LineType.LIGHT
        }
        '\u2563', '╣' -> {
            top = LineType.DOUBLE
            bottom = LineType.DOUBLE
            left = LineType.DOUBLE
        }
        '\u2564', '╤' -> {
            left = LineType.DOUBLE
            right = LineType.DOUBLE
            bottom = LineType.LIGHT
        }
        '\u2565', '╥' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
            bottom = LineType.DOUBLE
        }
        '\u2566', '╦' -> {
            left = LineType.DOUBLE
            right = LineType.DOUBLE
            bottom = LineType.DOUBLE
        }
        '\u2567', '╧' -> {
            left = LineType.DOUBLE
            right = LineType.DOUBLE
            top = LineType.LIGHT
        }
        '\u2568', '╨' -> {
            left = LineType.LIGHT
            right = LineType.LIGHT
            top = LineType.DOUBLE
        }
        '\u2569', '╩' -> {
            left = LineType.DOUBLE
            right = LineType.DOUBLE
            top = LineType.DOUBLE
        }
        '\u256a', '╪' -> {
            top = LineType.LIGHT
            bottom = LineType.LIGHT
            left = LineType.DOUBLE
            right = LineType.DOUBLE
        }
        '\u256b', '╫' -> {
            top = LineType.DOUBLE
            bottom = LineType.DOUBLE
            left = LineType.LIGHT
            right = LineType.LIGHT
        }
        '\u256c', '╬' -> {
            top = LineType.DOUBLE
            bottom = LineType.DOUBLE
            left = LineType.DOUBLE
            right = LineType.DOUBLE
        }

        // Single lines to center
        '\u2574', '╴' -> {
            left = LineType.LIGHT
        }
        '\u2575', '╵' -> {
            top = LineType.LIGHT
        }
        '\u2576', '╶' -> {
            right = LineType.LIGHT
        }
        '\u2577', '╷' -> {
            bottom = LineType.LIGHT
        }
        '\u2578', '╸' -> {
            left = LineType.HEAVY
        }
        '\u2579', '╹' -> {
            top = LineType.HEAVY
        }
        '\u257a', '╺' -> {
            right = LineType.HEAVY
        }
        '\u257b', '╻' -> {
            bottom = LineType.HEAVY
        }
        '\u257c', '╼' -> {
            left = LineType.LIGHT
            right = LineType.HEAVY
        }
        '\u257d', '╽' -> {
            top = LineType.LIGHT
            bottom = LineType.HEAVY
        }
        '\u257e', '╾' -> {
            left = LineType.HEAVY
            right = LineType.LIGHT
        }
        '\u257f', '╿' -> {
            top = LineType.HEAVY
            bottom = LineType.LIGHT
        }
    }

    fun drawSegment(direction: String, type: LineType) {
        if (type == LineType.NONE) return

        val width = when (type) {
            LineType.LIGHT -> strokeWidthLight
            LineType.HEAVY -> strokeWidthHeavy
            LineType.DOUBLE -> strokeWidthLight
        }

        when (direction) {
            "top" -> {
                if (type == LineType.DOUBLE) {
                    val offset = strokeWidthLight * 1.5f
                    drawLine(color, Offset(centerX - offset, centerY), Offset(centerX - offset, y), strokeWidthLight)
                    drawLine(color, Offset(centerX + offset, centerY), Offset(centerX + offset, y), strokeWidthLight)
                } else {
                    drawLine(color, Offset(centerX, centerY), Offset(centerX, y), width)
                }
            }
            "bottom" -> {
                if (type == LineType.DOUBLE) {
                    val offset = strokeWidthLight * 1.5f
                    drawLine(
                        color,
                        Offset(centerX - offset, centerY),
                        Offset(centerX - offset, y + cellHeight),
                        strokeWidthLight
                    )
                    drawLine(
                        color,
                        Offset(centerX + offset, centerY),
                        Offset(centerX + offset, y + cellHeight),
                        strokeWidthLight
                    )
                } else {
                    drawLine(color, Offset(centerX, centerY), Offset(centerX, y + cellHeight), width)
                }
            }
            "left" -> {
                if (type == LineType.DOUBLE) {
                    val offset = strokeWidthLight * 1.5f
                    drawLine(color, Offset(centerX, centerY - offset), Offset(x, centerY - offset), strokeWidthLight)
                    drawLine(color, Offset(centerX, centerY + offset), Offset(x, centerY + offset), strokeWidthLight)
                } else {
                    drawLine(color, Offset(centerX, centerY), Offset(x, centerY), width)
                }
            }
            "right" -> {
                if (type == LineType.DOUBLE) {
                    val offset = strokeWidthLight * 1.5f
                    drawLine(
                        color,
                        Offset(centerX, centerY - offset),
                        Offset(x + cellWidth, centerY - offset),
                        strokeWidthLight
                    )
                    drawLine(
                        color,
                        Offset(centerX, centerY + offset),
                        Offset(x + cellWidth, centerY + offset),
                        strokeWidthLight
                    )
                } else {
                    drawLine(color, Offset(centerX, centerY), Offset(x + cellWidth, centerY), width)
                }
            }
        }
    }

    drawSegment("top", top)
    drawSegment("bottom", bottom)
    drawSegment("left", left)
    drawSegment("right", right)
}

private fun DrawScope.drawBlockElement(
    c: Char,
    x: Float,
    y: Float,
    cellWidth: Float,
    cellHeight: Float,
    color: Color
) {
    when (c) {
        '\u2588', '█' -> {
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(cellWidth, cellHeight))
        }
        '\u2581', ' ' -> {
            val h = cellHeight / 8f
            drawRect(color, Offset(x, y + cellHeight - h), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u2582', '▂' -> {
            val h = cellHeight * 2f / 8f
            drawRect(color, Offset(x, y + cellHeight - h), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u2583', '▃' -> {
            val h = cellHeight * 3f / 8f
            drawRect(color, Offset(x, y + cellHeight - h), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u2584', '▄' -> {
            val h = cellHeight * 4f / 8f
            drawRect(color, Offset(x, y + cellHeight - h), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u2585', '▅' -> {
            val h = cellHeight * 5f / 8f
            drawRect(color, Offset(x, y + cellHeight - h), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u2586', '▆' -> {
            val h = cellHeight * 6f / 8f
            drawRect(color, Offset(x, y + cellHeight - h), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u2587', '▇' -> {
            val h = cellHeight * 7f / 8f
            drawRect(color, Offset(x, y + cellHeight - h), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u2580', '▀' -> {
            val h = cellHeight / 2f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(cellWidth, h))
        }
        '\u258f', '▏' -> {
            val w = cellWidth / 8f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u258e', '▎' -> {
            val w = cellWidth * 2f / 8f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u258d', '▍' -> {
            val w = cellWidth * 3f / 8f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u258c', '▌' -> {
            val w = cellWidth * 4f / 8f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u258b', '▋' -> {
            val w = cellWidth * 5f / 8f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u258a', '▊' -> {
            val w = cellWidth * 6f / 8f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u2589', '▉' -> {
            val w = cellWidth * 7f / 8f
            drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u2595', '▕' -> {
            val w = cellWidth / 8f
            drawRect(color, Offset(x + cellWidth - w, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u2590', '▐' -> {
            val w = cellWidth / 2f
            drawRect(color, Offset(x + cellWidth - w, y), androidx.compose.ui.geometry.Size(w, cellHeight))
        }
        '\u2591', '░' -> {
            drawRect(color.copy(alpha = 0.25f), Offset(x, y), androidx.compose.ui.geometry.Size(cellWidth, cellHeight))
        }
        '\u2592', '▒' -> {
            drawRect(color.copy(alpha = 0.5f), Offset(x, y), androidx.compose.ui.geometry.Size(cellWidth, cellHeight))
        }
        '\u2593', '▓' -> {
            drawRect(color.copy(alpha = 0.75f), Offset(x, y), androidx.compose.ui.geometry.Size(cellWidth, cellHeight))
        }
    }
}
