package com.johnan.terminal.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalAutoScrollTest {
    @Test
    fun testStreamingOutputFollowsBottomWhenAutoscrollEnabled() {
        val cellHeight = 30f
        val viewportHeight = 600f
        val totalRows = 100 // 100 rows * 30px = 3000px
        val maxScroll = ((totalRows * cellHeight) - viewportHeight).toInt() // 2400px
        val viewportTop = 2000 // user previously at 2000 before 400px of lines arrived
        val cursorY = (totalRows - 1) * cellHeight // cursor on bottom row: 2970px

        // When autoscroll is enabled and cursor is beyond viewportBottom (2000 + 600 = 2600):
        val target = calculateTargetScrollOffset(
            autoScrollEnabled = true,
            shouldForceScrollToBottom = false,
            cursorY = cursorY,
            cellHeight = cellHeight,
            viewportTop = viewportTop,
            viewportHeight = viewportHeight,
            maxScroll = maxScroll,
            cursorVisible = true,
            enabled = true,
        )

        assertEquals(maxScroll, target, "Streaming output must autoscroll to maxScroll when autoscroll is enabled")
    }

    @Test
    fun testLargeChunkOutputEOutputsAutoscrollsSuccessfully() {
        // Simulating cat large_file.txt: 1000 lines emitted at once
        val cellHeight = 30f
        val viewportHeight = 720f
        val initialScroll = 0
        val totalRows = 1000
        val maxScroll = ((totalRows * cellHeight) - viewportHeight).toInt() // 30,000 - 720 = 29280
        val cursorY = (totalRows - 1) * cellHeight

        val target = calculateTargetScrollOffset(
            autoScrollEnabled = true,
            shouldForceScrollToBottom = false,
            cursorY = cursorY,
            cellHeight = cellHeight,
            viewportTop = initialScroll,
            viewportHeight = viewportHeight,
            maxScroll = maxScroll,
            cursorVisible = true,
            enabled = true,
        )

        assertEquals(maxScroll, target, "Massive output chunk from cat must scroll to bottom")
    }

    @Test
    fun testUserScrollingUpDisengagesAutoscroll() {
        val thresholdPx = 60 // 2 rows of 30px
        val maxValue = 5000
        val currentValue = 2000 // user scrolled up 3000px away from bottom

        val autoscroll = resolveAutoScrollStateOnScroll(
            isScrollInProgress = true,
            isProgrammaticScroll = false,
            currentValue = currentValue,
            maxValue = maxValue,
            thresholdPx = thresholdPx,
            currentAutoScrollEnabled = true,
        )

        assertFalse(autoscroll, "Scrolling up away from bottom must disengage autoscroll")
    }

    @Test
    fun testIncomingOutputWhileScrolledUpPreservesViewportPosition() {
        val cellHeight = 30f
        val viewportHeight = 600f
        val viewportTop = 1200 // user scrolled up reading history
        val totalRows = 500 // new output pushed totalRows to 500
        val maxScroll = ((totalRows * cellHeight) - viewportHeight).toInt()
        val cursorY = (totalRows - 1) * cellHeight // cursor is way down at bottom

        // Autoscroll is disengaged because user is reading scrollback
        val target = calculateTargetScrollOffset(
            autoScrollEnabled = false,
            shouldForceScrollToBottom = false,
            cursorY = cursorY,
            cellHeight = cellHeight,
            viewportTop = viewportTop,
            viewportHeight = viewportHeight,
            maxScroll = maxScroll,
            cursorVisible = true,
            enabled = true,
        )

        assertNull(target, "Incoming output while inspecting scrollback must NOT yank the viewport")
    }

    @Test
    fun testUserScrollingBackToBottomReEngagesAutoscroll() {
        val thresholdPx = 60
        val maxValue = 5000
        val currentValue = 4980 // within 20px of bottom (<= 60px)

        val autoscroll = resolveAutoScrollStateOnScroll(
            isScrollInProgress = true,
            isProgrammaticScroll = false,
            currentValue = currentValue,
            maxValue = maxValue,
            thresholdPx = thresholdPx,
            currentAutoScrollEnabled = false,
        )

        assertTrue(autoscroll, "Scrolling within threshold of bottom must re-engage autoscroll")
    }

    @Test
    fun testUserInputForcesScrollToBottomRegardlessOfScrollback() {
        val cellHeight = 30f
        val viewportHeight = 600f
        val viewportTop = 500 // scrolled up
        val maxScroll = 4000
        val cursorY = 3900f

        // User typed a key or pressed Enter -> shouldForceScrollToBottom = true
        val target = calculateTargetScrollOffset(
            autoScrollEnabled = false,
            shouldForceScrollToBottom = true,
            cursorY = cursorY,
            cellHeight = cellHeight,
            viewportTop = viewportTop,
            viewportHeight = viewportHeight,
            maxScroll = maxScroll,
            cursorVisible = true,
            enabled = true,
        )

        assertEquals(maxScroll, target, "User input must override scrollback state and jump to bottom")
    }

    @Test
    fun testCursorAboveViewportScrollsUp() {
        val cellHeight = 30f
        val viewportHeight = 600f
        val viewportTop = 1500
        val maxScroll = 3000
        val cursorY = 1200f // cursor moved above viewport (e.g. editor cursor movement)

        val target = calculateTargetScrollOffset(
            autoScrollEnabled = false,
            shouldForceScrollToBottom = false,
            cursorY = cursorY,
            cellHeight = cellHeight,
            viewportTop = viewportTop,
            viewportHeight = viewportHeight,
            maxScroll = maxScroll,
            cursorVisible = true,
            enabled = true,
        )

        assertEquals(1200, target, "Cursor moving above viewport must scroll up to keep cursor visible")
    }

    @Test
    fun testProgrammaticScrollDoesNotCorruptAutoScrollState() {
        val thresholdPx = 60
        val maxValue = 5000
        val currentValue = 1000

        // During programmatic scroll animation, isScrollInProgress may temporarily be true
        val autoscroll = resolveAutoScrollStateOnScroll(
            isScrollInProgress = true,
            isProgrammaticScroll = true,
            currentValue = currentValue,
            maxValue = maxValue,
            thresholdPx = thresholdPx,
            currentAutoScrollEnabled = true,
        )

        assertTrue(autoscroll, "Programmatic scroll must not alter currentAutoScrollEnabled")
    }

    @Test
    fun testDisabledTerminalDoesNotScroll() {
        val target = calculateTargetScrollOffset(
            autoScrollEnabled = true,
            shouldForceScrollToBottom = false,
            cursorY = 5000f,
            cellHeight = 30f,
            viewportTop = 0,
            viewportHeight = 600f,
            maxScroll = 4000,
            cursorVisible = true,
            enabled = false,
        )

        assertNull(target, "Disabled terminal must return null")
    }
}
