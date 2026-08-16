package com.johnan.terminal.core

/**
 * Terminal screen buffer that holds the current state of the terminal display
 *
 * This class manages:
 * - A 2D grid of terminal cells (rows x columns)
 * - Cursor position
 * - Current text attributes for new characters
 * - Scrollback buffer for history
 * - Alternate screen buffer for full-screen applications (vim, htop, etc.)
 */
class ScreenBuffer(
    initialRows: Int,
    initialCols: Int,
    private val maxScrollback: Int = 1000,
) {
    // Current dimensions (can be changed via resize)
    var rows: Int = initialRows
        private set
    var cols: Int = initialCols
        private set

    // Main (primary) screen buffer
    private var primaryBuffer: Array<TerminalRow> = Array(rows) { TerminalRow(cols) }

    // Alternate screen buffer (used by vim, htop, less, etc.)
    private var alternateBuffer: Array<TerminalRow> = Array(rows) { TerminalRow(cols) }

    // Current active buffer (points to either primary or alternate)
    private var buffer: Array<TerminalRow> = primaryBuffer

    // Track which buffer is active
    private var isAlternateScreen: Boolean = false

    // Scrolling region (for DECSTBM - Set Top and Bottom Margins)
    private var scrollTop: Int = 0
    private var scrollBottom: Int = rows - 1

    // Scrollback buffer (lines that have scrolled off the top)
    // Optimization: Using ArrayDeque for O(1) removals from the front when buffer is full
    private val scrollback: ArrayDeque<Array<TerminalCell>> = ArrayDeque()

    // Version tracking for scrollback optimization
    var scrollbackVersion: Long = 0
        private set

    // Cursor position (0-indexed)
    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    // Cursor visibility
    var cursorVisible: Boolean = true

    // Auto-wrap mode (DECAWM) - controls whether writing at column edge wraps to next line
    // When true, writing past the last column wraps to the next line
    // When false, cursor stays at the last column
    private var autoWrapMode: Boolean = true

    // Pending wrap state - cursor is at column cols (off-screen, waiting to wrap)
    // This is set when a character is written at the last column (cols-1)
    // The actual wrap happens when the NEXT character is written
    // Explicit cursor movement cancels the pending wrap
    private var pendingWrap: Boolean = false

    // Current text attributes for new characters
    private var currentForeground: TerminalColor = TerminalColor.Default
    private var currentBackground: TerminalColor = TerminalColor.Default
    private var currentBold: Boolean = false
    private var currentItalic: Boolean = false
    private var currentUnderline: Boolean = false
    private var currentReverse: Boolean = false
    private var currentDim: Boolean = false
    private var currentBlink: Boolean = false
    private var currentStrikethrough: Boolean = false
    private var currentOverline: Boolean = false
    private var currentConceal: Boolean = false

    /**
     * Get a cell at the specified position
     */
    fun getCell(
        row: Int,
        col: Int,
    ): TerminalCell {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return TerminalCell.EMPTY
        }
        return buffer[row][col]
    }

    /**
     * Get an entire row of cells
     */
    fun getRow(row: Int): Array<TerminalCell> {
        if (row < 0 || row >= rows) {
            return Array(cols) { TerminalCell.EMPTY }
        }
        return buffer[row].copyOf()
    }

    /**
     * Get all rows (for rendering)
     */
    fun getAllRows(): List<Array<TerminalCell>> {
        // Optimization: Avoid .map to eliminate Iterator allocation in hot path
        val list = ArrayList<Array<TerminalCell>>(buffer.size)
        for (i in 0 until buffer.size) {
            list.add(buffer[i].copyOf())
        }
        return list
    }

    /**
     * Get internal row object (internal use only for optimization)
     */
    internal fun getTerminalRow(row: Int): TerminalRow {
        return buffer[row]
    }

    /**
     * Get scrollback lines
     */
    fun getScrollback(): List<Array<TerminalCell>> {
        val size = scrollback.size
        if (size == 0) return emptyList()
        // Optimization: Avoid .toList() on ArrayDeque to eliminate double array allocation
        // (Collection.toArray() + Arrays.copyOf()) under the hood, significantly reducing GC pressure.
        val list = ArrayList<Array<TerminalCell>>(size)
        for (i in 0 until size) {
            list.add(scrollback[i])
        }
        return list
    }

    /**
     * Write a character at the current cursor position
     *
     * Implements proper xterm auto-wrap behavior:
     * - When auto-wrap mode is enabled and a character is written at the last column,
     *   the cursor enters "pending wrap" state (at column cols, off-screen)
     * - Writing another character while in pending wrap triggers the actual wrap
     * - Explicit cursor movement cancels pending wrap
     */
    fun writeChar(char: Char) {
        // If we're in pending wrap state, perform the wrap now
        if (pendingWrap) {
            cursorCol = 0
            cursorRow++
            // Check if we've gone past the scrolling region bottom
            if (cursorRow > scrollBottom) {
                scrollUp()
                cursorRow = scrollBottom
            } else if (cursorRow >= rows) {
                // Fallback: if we're somehow past the screen entirely (shouldn't happen
                // in normal operation since scrollBottom should be rows-1 for full screen),
                // clamp to last row
                cursorRow = rows - 1
            }
            pendingWrap = false
        }

        // Write the character at current cursor position
        if (cursorRow >= 0 && cursorRow < rows && cursorCol >= 0 && cursorCol < cols) {
            buffer[cursorRow][cursorCol] =
                TerminalCell.create(
                    char = char,
                    foregroundColor = currentForeground,
                    backgroundColor = currentBackground,
                    bold = currentBold,
                    italic = currentItalic,
                    underline = currentUnderline,
                    reverse = currentReverse,
                    dim = currentDim,
                    blink = currentBlink,
                )
        }

        // Advance cursor
        cursorCol++

        // Check if we've reached the edge
        if (cursorCol >= cols) {
            if (autoWrapMode) {
                // Enter pending wrap state instead of wrapping immediately
                // The wrap will happen when the next character is written
                pendingWrap = true
                // Keep cursor at last column visually (xterm behavior)
                // Logically, the cursor is at column cols (off-screen), but we display it
                // at the last column. The pendingWrap flag tracks the true state.
                cursorCol = cols - 1
            } else {
                // No auto-wrap: cursor stays at last column
                cursorCol = cols - 1
            }
        }
    }

    /**
     * Write multiple characters at once.
     * Optimized to reduce bounds checking and attribute access for contiguous text.
     */
    fun writeText(text: CharSequence) {
        var i = 0
        val len = text.length

        while (i < len) {
            // Handle pending wrap
            if (pendingWrap) {
                cursorCol = 0
                cursorRow++
                if (cursorRow > scrollBottom) {
                    scrollUp()
                    cursorRow = scrollBottom
                } else if (cursorRow >= rows) {
                    cursorRow = rows - 1
                }
                pendingWrap = false
            }

            // Safety check
            if (cursorRow < 0 || cursorRow >= rows) {
                cursorRow = cursorRow.coerceIn(0, rows - 1)
            }

            // Ensure cursorCol is within bounds for calculation
            if (cursorCol >= cols) cursorCol = cols - 1
            if (cursorCol < 0) cursorCol = 0

            val space = cols - cursorCol
            val remaining = len - i
            val count = minOf(space, remaining)

            // Optimization: capture attributes once per batch
            val fg = currentForeground
            val bg = currentBackground
            val b = currentBold
            val it = currentItalic
            val u = currentUnderline
            val r = currentReverse
            val d = currentDim
            val bl = currentBlink
            val st = currentStrikethrough
            val ov = currentOverline
            val co = currentConceal

            // Fast path check: verify if all attributes are default
            val isDefault =
                fg === TerminalColor.Default &&
                    bg === TerminalColor.Default &&
                    !b && !it && !u && !r && !d && !bl && !st && !ov && !co

            val currentRow = buffer[cursorRow]

            // Write loop
            var changed = false
            for (k in 0 until count) {
                val c = text[i + k]
                val cell =
                    if (isDefault && c.code in 0..127) {
                        TerminalCell.getCachedAscii(c.code)
                    } else {
                        TerminalCell.create(c, fg, bg, b, it, u, r, d, bl, st, ov, co)
                    }
                if (currentRow.setCell(cursorCol + k, cell)) {
                    changed = true
                }
            }
            if (changed) {
                currentRow.incrementVersion()
            }

            cursorCol += count
            i += count

            // Check wrap
            if (cursorCol >= cols) {
                if (autoWrapMode) {
                    pendingWrap = true
                    cursorCol = cols - 1 // Visual position
                } else {
                    cursorCol = cols - 1
                }
            }
        }
    }

    /**
     * Move cursor to absolute position.
     * Cancels pending wrap.
     * If originMode is true, position is relative to scrolling region.
     */
    fun setCursorPosition(
        row: Int,
        col: Int,
        originMode: Boolean = false,
    ) {
        pendingWrap = false // Cancel pending wrap on explicit cursor movement
        val finalRow =
            if (originMode) {
                (scrollTop + row).coerceIn(scrollTop, scrollBottom)
            } else {
                row.coerceIn(0, rows - 1)
            }
        cursorRow = finalRow
        cursorCol = col.coerceIn(0, cols - 1)
    }

    /**
     * Move cursor relative to current position
     * Cancels pending wrap.
     */
    fun moveCursor(
        deltaRow: Int,
        deltaCol: Int,
    ) {
        pendingWrap = false // Cancel pending wrap on explicit cursor movement
        cursorRow = (cursorRow + deltaRow).coerceIn(0, rows - 1)
        cursorCol = (cursorCol + deltaCol).coerceIn(0, cols - 1)
    }

    /**
     * Move cursor to beginning of line
     * Cancels pending wrap.
     */
    fun carriageReturn() {
        pendingWrap = false // Cancel pending wrap
        cursorCol = 0
    }

    /**
     * Move cursor to next line
     * Cancels pending wrap.
     */
    fun lineFeed() {
        pendingWrap = false // Cancel pending wrap
        cursorRow++
        // If cursor is within scrolling region and goes past bottom, scroll
        if (cursorRow >= scrollTop && cursorRow > scrollBottom) {
            scrollUp()
            cursorRow = scrollBottom
        } else if (cursorRow >= rows) {
            // If outside region and past screen, clamp to bottom
            cursorRow = rows - 1
        }
    }

    /**
     * Move cursor up one line
     * Cancels pending wrap.
     */
    fun cursorUp(count: Int = 1) {
        pendingWrap = false // Cancel pending wrap
        val newRow = cursorRow - count
        // If cursor is within scrolling region and moves past top, scroll down
        if (cursorRow >= scrollTop && newRow < scrollTop) {
            scrollDown()
            cursorRow = scrollTop
        } else {
            cursorRow = newRow.coerceAtLeast(0)
        }
    }

    /**
     * Move cursor down one line
     * Cancels pending wrap.
     */
    fun cursorDown(count: Int = 1) {
        pendingWrap = false // Cancel pending wrap
        cursorRow = (cursorRow + count).coerceAtMost(rows - 1)
    }

    /**
     * Move cursor forward (right)
     * Cancels pending wrap.
     */
    fun cursorForward(count: Int = 1) {
        pendingWrap = false // Cancel pending wrap
        cursorCol = (cursorCol + count).coerceAtMost(cols - 1)
    }

    /**
     * Move cursor backward (left)
     * Cancels pending wrap.
     */
    fun cursorBackward(count: Int = 1) {
        pendingWrap = false // Cancel pending wrap
        cursorCol = (cursorCol - count).coerceAtLeast(0)
    }

    /**
     * Clear entire screen
     */
    fun clearScreen() {
        pendingWrap = false
        for (row in 0 until rows) {
            val terminalRow = buffer[row]
            if (terminalRow.clearCells(0, cols)) {
                terminalRow.incrementVersion()
            }
        }
    }

    /**
     * Clear from cursor to end of screen
     */
    fun clearToEndOfScreen() {
        pendingWrap = false
        // Clear from cursor to end of current line
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(cursorCol, cols)) {
            currentRow.incrementVersion()
        }

        // Clear all lines below
        for (row in (cursorRow + 1) until rows) {
            val terminalRow = buffer[row]
            if (terminalRow.clearCells(0, cols)) {
                terminalRow.incrementVersion()
            }
        }
    }

    /**
     * Clear from start of screen to cursor
     */
    fun clearToStartOfScreen() {
        pendingWrap = false
        // Clear all lines above
        for (row in 0 until cursorRow) {
            val terminalRow = buffer[row]
            if (terminalRow.clearCells(0, cols)) {
                terminalRow.incrementVersion()
            }
        }
        // Clear from start of current line to cursor
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(0, cursorCol + 1)) {
            currentRow.incrementVersion()
        }
    }

    /**
     * Clear entire line
     */
    fun clearLine() {
        pendingWrap = false
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(0, cols)) {
            currentRow.incrementVersion()
        }
    }

    /**
     * Clear from cursor to end of line
     */
    fun clearToEndOfLine() {
        pendingWrap = false
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(cursorCol, cols)) {
            currentRow.incrementVersion()
        }
    }

    /**
     * Clear from start of line to cursor
     */
    fun clearToStartOfLine() {
        pendingWrap = false
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(0, cursorCol + 1)) {
            currentRow.incrementVersion()
        }
    }

    /**
     * Scroll up by multiple lines (move text up, new blank lines at bottom)
     */
    fun scrollUp(lines: Int) {
        val count = lines.coerceAtMost(scrollBottom - scrollTop + 1)
        repeat(count) { scrollUp() }
    }

    /**
     * Scroll the screen up by one line (respecting scrolling region)
     */
    private fun scrollUp() {
        // Only save to scrollback if scrolling the entire screen
        if (scrollTop == 0 && scrollBottom == rows - 1) {
            // Save top line to scrollback
            if (scrollback.size >= maxScrollback) {
                scrollback.removeFirst()
            }
            scrollback.addLast(buffer[scrollTop].copyOf())
            scrollbackVersion++
        }

        // Optimization: Save the top row to reuse it at the bottom to avoid TerminalRow allocation
        val topRow = buffer[scrollTop]

        // Shift lines up within the scrolling region
        for (row in scrollTop until scrollBottom) {
            buffer[row] = buffer[row + 1]
        }

        // Clear bottom line of scrolling region and reuse the old top row
        if (topRow.clearCells(0, cols)) {
            topRow.incrementVersion()
        }
        buffer[scrollBottom] = topRow
    }

    /**
     * Scroll down by multiple lines (move text down, new blank lines at top)
     */
    fun scrollDown(lines: Int) {
        val count = lines.coerceAtMost(scrollBottom - scrollTop + 1)
        repeat(count) { scrollDown() }
    }

    /**
     * Scroll the screen down by one line (reverse index - RI)
     * Used when cursor moves up past the top of scrolling region
     */
    fun scrollDown() {
        // Optimization: Save the bottom row to reuse it at the top to avoid TerminalRow allocation
        val bottomRow = buffer[scrollBottom]

        // Shift lines down within the scrolling region
        for (row in scrollBottom downTo scrollTop + 1) {
            buffer[row] = buffer[row - 1]
        }

        // Clear top line of scrolling region and reuse the old bottom row
        if (bottomRow.clearCells(0, cols)) {
            bottomRow.incrementVersion()
        }
        buffer[scrollTop] = bottomRow
    }

    /**
     * Set current text attributes
     */
    fun setTextAttributes(
        foreground: TerminalColor? = null,
        background: TerminalColor? = null,
        bold: Boolean? = null,
        italic: Boolean? = null,
        underline: Boolean? = null,
        reverse: Boolean? = null,
        dim: Boolean? = null,
        blink: Boolean? = null,
        strikethrough: Boolean? = null,
        overline: Boolean? = null,
        conceal: Boolean? = null,
    ) {
        foreground?.let { currentForeground = it }
        background?.let { currentBackground = it }
        bold?.let { currentBold = it }
        italic?.let { currentItalic = it }
        underline?.let { currentUnderline = it }
        reverse?.let { currentReverse = it }
        dim?.let { currentDim = it }
        blink?.let { currentBlink = it }
        strikethrough?.let { currentStrikethrough = it }
        overline?.let { currentOverline = it }
        conceal?.let { currentConceal = it }
    }

    /**
     * Reset all text attributes to defaults
     */
    fun resetTextAttributes() {
        currentForeground = TerminalColor.Default
        currentBackground = TerminalColor.Default
        currentBold = false
        currentItalic = false
        currentUnderline = false
        currentReverse = false
        currentDim = false
        currentBlink = false
        currentStrikethrough = false
        currentOverline = false
        currentConceal = false
    }

    /**
     * Set auto-wrap mode (DECAWM)
     * When enabled (default), writing past the last column wraps to the next line
     * When disabled, cursor stays at the last column
     */
    fun setAutoWrapMode(enabled: Boolean) {
        autoWrapMode = enabled
        // Changing auto-wrap mode should cancel any pending wrap
        if (!enabled) {
            pendingWrap = false
        }
    }

    /**
     * Save cursor position (for later restore)
     */
    private var savedCursorRow: Int = 0
    private var savedCursorCol: Int = 0

    fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
    }

    fun restoreCursor() {
        cursorRow = savedCursorRow
        cursorCol = savedCursorCol
    }

    /**
     * Switch to alternate screen buffer
     * Used by full-screen applications like vim, htop, less
     */
    fun useAlternateScreen() {
        if (!isAlternateScreen) {
            // Save cursor position before switching
            saveCursor()

            // Switch to alternate buffer
            buffer = alternateBuffer
            isAlternateScreen = true

            // Clear alternate buffer
            clearScreen()
            cursorRow = 0
            cursorCol = 0

            // Reset scrolling region to full screen
            resetScrollingRegion()
        }
    }

    /**
     * Switch back to primary screen buffer
     */
    fun usePrimaryScreen() {
        if (isAlternateScreen) {
            // Switch to primary buffer
            buffer = primaryBuffer
            isAlternateScreen = false

            // Restore cursor position
            restoreCursor()

            // Reset scrolling region to full screen
            resetScrollingRegion()
        }
    }

    /**
     * Check if alternate screen is active
     */
    fun isUsingAlternateScreen(): Boolean = isAlternateScreen

    /**
     * Insert blank lines at cursor position (IL)
     * Scrolls lines below down, losing lines at bottom of scrolling region
     */
    fun insertLines(count: Int = 1) {
        pendingWrap = false
        // Only operate if cursor is within scrolling region
        if (cursorRow < scrollTop || cursorRow > scrollBottom) {
            return
        }

        val linesToInsert = count.coerceIn(1, scrollBottom - cursorRow + 1)

        // Shift lines down within scrolling region
        for (i in 0 until linesToInsert) {
            // Optimization: Save the bottom row to reuse it at the cursor to avoid TerminalRow allocation
            val bottomRow = buffer[scrollBottom]

            // Move lines down from cursor to bottom of region
            for (row in scrollBottom downTo cursorRow + 1) {
                buffer[row] = buffer[row - 1]
            }

            // Clear the cursor line and reuse the old bottom row
            if (bottomRow.clearCells(0, cols)) {
                bottomRow.incrementVersion()
            }
            buffer[cursorRow] = bottomRow
        }
    }

    /**
     * Delete lines at cursor position (DL)
     * Scrolls lines below up, creating blank lines at bottom of scrolling region
     */
    fun deleteLines(count: Int = 1) {
        pendingWrap = false
        // Only operate if cursor is within scrolling region
        if (cursorRow < scrollTop || cursorRow > scrollBottom) {
            return
        }

        val linesToDelete = count.coerceIn(1, scrollBottom - cursorRow + 1)

        // Shift lines up within scrolling region
        for (i in 0 until linesToDelete) {
            // Optimization: Save the cursor row to reuse it at the bottom to avoid TerminalRow allocation
            val cursorRowRef = buffer[cursorRow]

            for (row in cursorRow until scrollBottom) {
                buffer[row] = buffer[row + 1]
            }

            // Clear the bottom line of region and reuse the old cursor row
            if (cursorRowRef.clearCells(0, cols)) {
                cursorRowRef.incrementVersion()
            }
            buffer[scrollBottom] = cursorRowRef
        }
    }

    /**
     * Delete characters starting at cursor position (DCH)
     * Shifts remaining characters on the line to the left
     */
    fun deleteCharacters(count: Int) {
        pendingWrap = false
        val deleteCount = count.coerceIn(0, cols - cursorCol)
        if (deleteCount <= 0) return

        val currentRow = buffer[cursorRow]
        var changed = false

        // Shift characters left
        for (col in cursorCol until (cols - deleteCount)) {
            if (currentRow.setCell(col, currentRow[col + deleteCount])) {
                changed = true
            }
        }

        // Fill the end with empty cells
        if (currentRow.clearCells(cols - deleteCount, cols)) {
            changed = true
        }

        if (changed) {
            currentRow.incrementVersion()
        }
    }

    /**
     * Insert blank characters at cursor position (ICH)
     * Shifts existing characters to the right, losing those at the end of the line
     */
    fun insertCharacters(count: Int) {
        pendingWrap = false
        val insertCount = count.coerceIn(0, cols - cursorCol)
        if (insertCount <= 0) return

        val currentRow = buffer[cursorRow]
        var changed = false

        // Shift characters right (iterate backwards to avoid overwriting)
        for (col in (cols - 1) downTo (cursorCol + insertCount)) {
            if (currentRow.setCell(col, currentRow[col - insertCount])) {
                changed = true
            }
        }

        // Fill inserted space with empty cells
        if (currentRow.clearCells(cursorCol, cursorCol + insertCount)) {
            changed = true
        }

        if (changed) {
            currentRow.incrementVersion()
        }
    }

    /**
     * Erase characters starting at cursor position (ECH)
     * Replaces characters with empty cells, does not shift
     */
    fun eraseCharacters(count: Int) {
        pendingWrap = false
        val eraseCount = count.coerceIn(0, cols - cursorCol)
        if (eraseCount <= 0) return

        val currentRow = buffer[cursorRow]

        if (currentRow.clearCells(cursorCol, cursorCol + eraseCount)) {
            currentRow.incrementVersion()
        }
    }

    /**
     * Set scrolling region (DECSTBM)
     * @param top Top margin (0-indexed)
     * @param bottom Bottom margin (0-indexed)
     */
    fun setScrollingRegion(
        top: Int,
        bottom: Int,
    ) {
        scrollTop = top.coerceIn(0, rows - 1)
        scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
    }

    /**
     * Reset scrolling region to full screen
     */
    fun resetScrollingRegion() {
        scrollTop = 0
        scrollBottom = rows - 1
    }

    companion object {
        // URL detection pattern
        private val urlPattern = Regex("(https?://[\\w\\-\\._~:/?#\\[\\]@!$&'()*+,;=]+)")

        /**
         * Bounded LRU Cache for URL matches in rows.
         * Key: The Array<TerminalCell> instance (identity-based).
         * Value: List of relative URL positions (col indices only).
         */
        private object UrlCache {
            private const val MAX_ENTRIES = 1000
            private val cache = LinkedHashMap<Array<TerminalCell>, List<UrlRange>>()

            fun getOrPut(row: Array<TerminalCell>, compute: () -> List<UrlRange>): List<UrlRange> {
                var value = cache[row]
                if (value == null) {
                    value = compute()
                    cache[row] = value
                    if (cache.size > MAX_ENTRIES) {
                        val iterator = cache.entries.iterator()
                        if (iterator.hasNext()) {
                            iterator.next()
                            iterator.remove()
                        }
                    }
                }
                return value
            }
        }

        /**
         * Get URLs in a specific row as efficient UrlRange objects.
         * Returns cached result if available.
         */
        fun getUrlRanges(row: Array<TerminalCell>): List<UrlRange> {
            return UrlCache.getOrPut(row) {
                val ranges = mutableListOf<UrlRange>()

                // Optimization: Wrap array in CharSequence to avoid String allocation and char copy
                val text = TerminalRowCharSequence(row)
                val matches = urlPattern.findAll(text)

                for (match in matches) {
                    val start = match.range.first
                    val end = match.range.last + 1 // exclusive
                    ranges.add(UrlRange(start, end))
                }

                if (ranges.isEmpty()) emptyList() else ranges.toList()
            }
        }

        /**
         * Extract URL string from a row given a UrlRange.
         * Creates a new String only when needed (e.g. on click).
         */
        fun extractUrl(row: Array<TerminalCell>, range: UrlRange): String {
            val len = range.endCol - range.startCol
            if (len <= 0) return ""
            val chars = CharArray(len)
            for (i in 0 until len) {
                chars[i] = row[range.startCol + i].char
            }
            return chars.concatToString()
        }
    }

    /**
     * CharSequence wrapper for Array<TerminalCell> to avoid string allocation/copying during regex matching.
     */
    private class TerminalRowCharSequence(
        private val row: Array<TerminalCell>,
        private val start: Int = 0,
        private val end: Int = row.size,
    ) : CharSequence {
        override val length: Int get() = end - start

        override fun get(index: Int): Char {
            if (index < 0 || index >= length) throw IndexOutOfBoundsException("Index: $index, Length: $length")
            return row[start + index].char
        }

        override fun subSequence(
            startIndex: Int,
            endIndex: Int,
        ): CharSequence {
            if (startIndex < 0 || endIndex > length || startIndex > endIndex) {
                throw IndexOutOfBoundsException()
            }
            // Optimization: Return a lightweight view instead of a String copy
            // to eliminate allocation overhead when Matcher internally uses subSequence.
            return TerminalRowCharSequence(row, start + startIndex, start + endIndex)
        }

        override fun toString(): String {
            val len = length
            if (len <= 0) return ""
            // Only allocate String when explicitly requested (e.g. for final extraction)
            val chars = CharArray(len)
            for (i in 0 until len) {
                chars[i] = row[start + i].char
            }
            return chars.concatToString()
        }
    }

    /**
     * Search for text in the buffer (including scrollback).
     *
     * @param query The text to search for.
     * @param ignoreCase Whether to ignore case.
     * @return List of matches with absolute coordinates (row index in scrollback + buffer).
     */
    fun search(
        query: String,
        ignoreCase: Boolean = true,
    ): List<TerminalMatch> {
        if (query.isEmpty()) return emptyList()

        val matches = mutableListOf<TerminalMatch>()

        // Optimization: search directly in arrays without converting to String or copying lists
        // Search scrollback
        for (i in 0 until scrollback.size) {
            searchRow(i, scrollback[i], query, ignoreCase, matches)
        }

        // Search buffer
        val scrollbackSize = scrollback.size
        val isQueryOnlySpaces = query.all { it == ' ' }
        for (i in 0 until rows) {
            val terminalRow = buffer[i]
            // Optimization: Skip empty rows in O(1) to avoid expensive array string matching
            // We must not skip if the user is explicitly searching for only spaces
            if (terminalRow.isEmpty() && !isQueryOnlySpaces) continue

            // Pass the underlying array
            searchRow(scrollbackSize + i, terminalRow.cells, query, ignoreCase, matches)
        }

        return matches
    }

    private fun searchRow(
        rowIndex: Int,
        row: Array<TerminalCell>,
        query: String,
        ignoreCase: Boolean,
        matches: MutableList<TerminalMatch>,
    ) {
        if (row.isEmpty()) return

        val queryLen = query.length
        var searchStart = 0

        while (true) {
            // Use specialized indexOf logic optimized for TerminalCell arrays to avoid allocation
            val foundAt = row.indexOf(query, searchStart, ignoreCase)
            if (foundAt < 0) break

            matches.add(
                TerminalMatch(
                    startRow = rowIndex,
                    startCol = foundAt,
                    endRow = rowIndex,
                    endCol = foundAt + queryLen,
                ),
            )
            // Move past current position
            searchStart = foundAt + 1
        }
    }

    /**
     * Specialized indexOf for Array<TerminalCell> to avoid allocations.
     */
    private fun Array<TerminalCell>.indexOf(query: String, startIndex: Int, ignoreCase: Boolean): Int {
        val maxStart = size - query.length
        if (startIndex > maxStart) return -1

        val firstChar = query[0]
        val queryLen = query.length

        for (i in startIndex..maxStart) {
            // Check first char (hot loop)
            if (this[i].char.equals(firstChar, ignoreCase)) {
                // Check rest
                var match = true
                for (j in 1 until queryLen) {
                    if (!this[i + j].char.equals(query[j], ignoreCase)) {
                        match = false
                        break
                    }
                }
                if (match) return i
            }
        }
        return -1
    }

    /**
     * Resize the terminal buffer
     *
     * This method dynamically resizes the terminal buffer to new dimensions.
     * Part of Chunk 1.1.3: TerminalEmulator Resize.
     *
     * Strategy:
     * - Creates new buffers with new dimensions
     * - Preserves existing content (copies what fits)
     * - Truncates content if new dimensions are smaller
     * - Fills with empty cells if new dimensions are larger
     * - Adjusts cursor position to stay within bounds
     * - Updates scrolling region
     * - Preserves scrollback buffer (unchanged)
     *
     * @param newRows New number of rows
     * @param newCols New number of columns
     * @return true if resize occurred, false if dimensions unchanged
     */
    fun resize(
        newRows: Int,
        newCols: Int,
    ): Boolean {
        // Check if dimensions actually changed
        if (newRows == rows && newCols == cols) {
            return false
        }

        // Validate new dimensions
        if (newRows <= 0 || newCols <= 0) {
            return false
        }

        val newPrimaryBuffer: Array<TerminalRow>
        val newAlternateBuffer: Array<TerminalRow>

        if (newCols == cols) {
            // Optimized path: Reuse existing row arrays
            // This avoids massive object allocation for cells and arrays
            val commonRows = minOf(rows, newRows)
            newPrimaryBuffer =
                Array(newRows) { i ->
                    if (i < commonRows) primaryBuffer[i] else TerminalRow(newCols)
                }
            newAlternateBuffer =
                Array(newRows) { i ->
                    if (i < commonRows) alternateBuffer[i] else TerminalRow(newCols)
                }
        } else {
            // Standard path: Must reallocate rows, but use faster array copy
            val rowsToCopy = minOf(rows, newRows)
            val colsToCopy = minOf(cols, newCols)

            newPrimaryBuffer =
                Array(newRows) { i ->
                    val newRow = TerminalRow(newCols)
                    if (i < rowsToCopy) {
                        val oldRow = primaryBuffer[i]
                        // Optimization: Bypass O(Cols) copy and recalculate overhead for empty rows
                        if (!oldRow.isEmpty()) {
                            oldRow.copyInto(newRow.cells, 0, 0, colsToCopy)
                            // Recalculate non-default count since we bypassed setCell
                            newRow.recalculateNonDefaultCells()
                        }
                    }
                    newRow
                }

            newAlternateBuffer =
                Array(newRows) { i ->
                    val newRow = TerminalRow(newCols)
                    if (i < rowsToCopy) {
                        val oldRow = alternateBuffer[i]
                        // Optimization: Bypass O(Cols) copy and recalculate overhead for empty rows
                        if (!oldRow.isEmpty()) {
                            oldRow.copyInto(newRow.cells, 0, 0, colsToCopy)
                            // Recalculate non-default count since we bypassed setCell
                            newRow.recalculateNonDefaultCells()
                        }
                    }
                    newRow
                }
        }

        // Update dimensions
        rows = newRows
        cols = newCols

        // Replace old buffers with new ones
        primaryBuffer = newPrimaryBuffer
        alternateBuffer = newAlternateBuffer

        // Update buffer reference to point to the correct buffer
        buffer = if (isAlternateScreen) alternateBuffer else primaryBuffer

        // Adjust cursor position to stay within bounds
        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorCol = cursorCol.coerceIn(0, newCols - 1)

        // Cancel any pending wrap since dimensions changed
        pendingWrap = false

        // Update scrolling region to match new dimensions
        scrollTop = 0
        scrollBottom = newRows - 1

        return true
    }
}

data class UrlRange(val startCol: Int, val endCol: Int)
