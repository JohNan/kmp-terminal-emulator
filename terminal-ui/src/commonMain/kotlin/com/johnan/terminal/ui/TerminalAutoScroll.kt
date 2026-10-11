package com.johnan.terminal.ui

/**
 * Resolves whether autoscroll should remain enabled based on user scroll gestures.
 * Only modifies autoscroll state when user is actively scrolling [isScrollInProgress]
 * and not during programmatic scroll animations.
 */
fun resolveAutoScrollStateOnScroll(
    isScrollInProgress: Boolean,
    isProgrammaticScroll: Boolean,
    currentValue: Int,
    maxValue: Int,
    thresholdPx: Int,
    currentAutoScrollEnabled: Boolean,
): Boolean {
    if (!isScrollInProgress || isProgrammaticScroll) {
        return currentAutoScrollEnabled
    }
    return maxValue == 0 || (maxValue - currentValue) <= thresholdPx
}

/**
 * Computes the target vertical scroll offset based on cursor position, autoscroll engagement,
 * and user force-scroll actions.
 *
 * @return Target scroll offset in pixels, or null if no scroll adjustment is required.
 */
fun calculateTargetScrollOffset(
    autoScrollEnabled: Boolean,
    shouldForceScrollToBottom: Boolean,
    cursorY: Float,
    cellHeight: Float,
    viewportTop: Int,
    viewportHeight: Float,
    maxScroll: Int,
    cursorVisible: Boolean,
    enabled: Boolean,
): Int? {
    if (shouldForceScrollToBottom) {
        return maxScroll
    }
    if (!enabled) return null

    val viewportBottom = viewportTop + viewportHeight

    // Keep cursor visible if it moves above the viewport (e.g. fullscreen TUIs or cursor addressing)
    if (cursorVisible && cursorY < viewportTop) {
        return cursorY.toInt().coerceIn(0, maxScroll)
    }

    // When autoscroll is engaged, follow content down to the bottom
    if (autoScrollEnabled) {
        if (cursorVisible && cursorY + cellHeight > viewportBottom) {
            return (cursorY + cellHeight - viewportHeight).toInt().coerceIn(0, maxScroll)
        }
        if (maxScroll > viewportTop) {
            return maxScroll
        }
    }

    return null
}
