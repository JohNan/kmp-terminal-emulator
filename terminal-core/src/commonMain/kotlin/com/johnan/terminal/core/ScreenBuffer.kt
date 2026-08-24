package com.johnan.terminal.core

/**
 * 2D terminal screen grid managing primary/alternate buffers, cursor state, scroll margins, and scrollback.
 */
class ScreenBuffer(
    initialRows: Int,
    initialCols: Int,
    private val maxScrollback: Int = 1000,
) {
    var rows: Int = initialRows
        private set
    var cols: Int = initialCols
        private set

    private var primaryBuffer: Array<TerminalRow> = Array(rows) { TerminalRow(cols) }
    private var alternateBuffer: Array<TerminalRow> = Array(rows) { TerminalRow(cols) }
    private var buffer: Array<TerminalRow> = primaryBuffer
    private var isAlternateScreen: Boolean = false

    private var scrollTop: Int = 0
    private var scrollBottom: Int = rows - 1

    private val scrollback: ArrayDeque<Array<TerminalCell>> = ArrayDeque()

    var scrollbackVersion: Long = 0
        private set

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    var cursorVisible: Boolean = true

    private var autoWrapMode: Boolean = true
    private var pendingWrap: Boolean = false

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
     * Returns the cell at the given row and column index, or [TerminalCell.EMPTY] if out of bounds.
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
     * Returns a copy of the cell row at the given row index.
     */
    fun getRow(row: Int): Array<TerminalCell> {
        if (row < 0 || row >= rows) {
            return Array(cols) { TerminalCell.EMPTY }
        }
        return buffer[row].copyOf()
    }

    /**
     * Returns a snapshot list containing copies of all active visible rows.
     */
    fun getAllRows(): List<Array<TerminalCell>> {
        val list = ArrayList<Array<TerminalCell>>(buffer.size)
        for (i in buffer.indices) {
            list.add(buffer[i].copyOf())
        }
        return list
    }

    internal fun getTerminalRow(row: Int): TerminalRow = buffer[row]

    /**
     * Returns a snapshot list of rows currently in scrollback history.
     */
    fun getScrollback(): List<Array<TerminalCell>> {
        val size = scrollback.size
        if (size == 0) return emptyList()
        val list = ArrayList<Array<TerminalCell>>(size)
        for (i in 0 until size) {
            list.add(scrollback[i])
        }
        return list
    }

    /**
     * Writes a single character at the current cursor position with active attributes, handling DECAWM auto-wrap.
     */
    fun writeChar(char: Char) {
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

        if (cursorRow in 0 until rows && cursorCol in 0 until cols) {
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

        cursorCol++

        if (cursorCol >= cols) {
            if (autoWrapMode) {
                pendingWrap = true
                cursorCol = cols - 1
            } else {
                cursorCol = cols - 1
            }
        }
    }

    /**
     * Writes a batch of characters sequentially starting at cursor position with active attributes.
     */
    fun writeText(text: CharSequence) {
        var i = 0
        val len = text.length

        while (i < len) {
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

            if (cursorRow < 0 || cursorRow >= rows) {
                cursorRow = cursorRow.coerceIn(0, rows - 1)
            }

            if (cursorCol >= cols) cursorCol = cols - 1
            if (cursorCol < 0) cursorCol = 0

            val space = cols - cursorCol
            val remaining = len - i
            val count = minOf(space, remaining)

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

            val isDefault =
                fg === TerminalColor.Default &&
                    bg === TerminalColor.Default &&
                    !b &&
                    !it &&
                    !u &&
                    !r &&
                    !d &&
                    !bl &&
                    !st &&
                    !ov &&
                    !co

            val currentRow = buffer[cursorRow]
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

            if (cursorCol >= cols) {
                if (autoWrapMode) {
                    pendingWrap = true
                    cursorCol = cols - 1
                } else {
                    cursorCol = cols - 1
                }
            }
        }
    }

    /**
     * Sets absolute cursor position. If [originMode] is true, position is relative to scrolling margins.
     */
    fun setCursorPosition(
        row: Int,
        col: Int,
        originMode: Boolean = false,
    ) {
        pendingWrap = false
        val finalRow =
            if (originMode) {
                (scrollTop + row).coerceIn(scrollTop, scrollBottom)
            } else {
                row.coerceIn(0, rows - 1)
            }
        cursorRow = finalRow
        cursorCol = col.coerceIn(0, cols - 1)
    }

    fun moveCursor(
        deltaRow: Int,
        deltaCol: Int,
    ) {
        pendingWrap = false
        cursorRow = (cursorRow + deltaRow).coerceIn(0, rows - 1)
        cursorCol = (cursorCol + deltaCol).coerceIn(0, cols - 1)
    }

    fun carriageReturn() {
        pendingWrap = false
        cursorCol = 0
    }

    fun lineFeed() {
        pendingWrap = false
        cursorRow++
        if (cursorRow >= scrollTop && cursorRow > scrollBottom) {
            scrollUp()
            cursorRow = scrollBottom
        } else if (cursorRow >= rows) {
            cursorRow = rows - 1
        }
    }

    fun cursorUp(count: Int = 1) {
        pendingWrap = false
        val newRow = cursorRow - count
        if (cursorRow >= scrollTop && newRow < scrollTop) {
            scrollDown()
            cursorRow = scrollTop
        } else {
            cursorRow = newRow.coerceAtLeast(0)
        }
    }

    fun cursorDown(count: Int = 1) {
        pendingWrap = false
        cursorRow = (cursorRow + count).coerceAtMost(rows - 1)
    }

    fun cursorForward(count: Int = 1) {
        pendingWrap = false
        cursorCol = (cursorCol + count).coerceAtMost(cols - 1)
    }

    fun cursorBackward(count: Int = 1) {
        pendingWrap = false
        cursorCol = (cursorCol - count).coerceAtLeast(0)
    }

    fun clearScreen() {
        pendingWrap = false
        for (row in 0 until rows) {
            val terminalRow = buffer[row]
            if (terminalRow.clearCells(0, cols)) {
                terminalRow.incrementVersion()
            }
        }
    }

    fun clearToEndOfScreen() {
        pendingWrap = false
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(cursorCol, cols)) {
            currentRow.incrementVersion()
        }

        for (row in (cursorRow + 1) until rows) {
            val terminalRow = buffer[row]
            if (terminalRow.clearCells(0, cols)) {
                terminalRow.incrementVersion()
            }
        }
    }

    fun clearToStartOfScreen() {
        pendingWrap = false
        for (row in 0 until cursorRow) {
            val terminalRow = buffer[row]
            if (terminalRow.clearCells(0, cols)) {
                terminalRow.incrementVersion()
            }
        }
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(0, cursorCol + 1)) {
            currentRow.incrementVersion()
        }
    }

    fun clearLine() {
        pendingWrap = false
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(0, cols)) {
            currentRow.incrementVersion()
        }
    }

    fun clearToEndOfLine() {
        pendingWrap = false
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(cursorCol, cols)) {
            currentRow.incrementVersion()
        }
    }

    fun clearToStartOfLine() {
        pendingWrap = false
        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(0, cursorCol + 1)) {
            currentRow.incrementVersion()
        }
    }

    fun scrollUp(lines: Int) {
        val count = lines.coerceAtMost(scrollBottom - scrollTop + 1)
        repeat(count) { scrollUp() }
    }

    private fun scrollUp() {
        if (scrollTop == 0 && scrollBottom == rows - 1) {
            if (scrollback.size >= maxScrollback) {
                scrollback.removeFirst()
            }
            scrollback.addLast(buffer[scrollTop].copyOf())
            scrollbackVersion++
        }

        val topRow = buffer[scrollTop]
        for (row in scrollTop until scrollBottom) {
            buffer[row] = buffer[row + 1]
        }

        if (topRow.clearCells(0, cols)) {
            topRow.incrementVersion()
        }
        buffer[scrollBottom] = topRow
    }

    fun scrollDown(lines: Int) {
        val count = lines.coerceAtMost(scrollBottom - scrollTop + 1)
        repeat(count) { scrollDown() }
    }

    fun scrollDown() {
        val bottomRow = buffer[scrollBottom]
        for (row in scrollBottom downTo scrollTop + 1) {
            buffer[row] = buffer[row - 1]
        }

        if (bottomRow.clearCells(0, cols)) {
            bottomRow.incrementVersion()
        }
        buffer[scrollTop] = bottomRow
    }

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

    fun setAutoWrapMode(enabled: Boolean) {
        autoWrapMode = enabled
        if (!enabled) {
            pendingWrap = false
        }
    }

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

    fun useAlternateScreen() {
        if (!isAlternateScreen) {
            saveCursor()
            buffer = alternateBuffer
            isAlternateScreen = true
            clearScreen()
            cursorRow = 0
            cursorCol = 0
            resetScrollingRegion()
        }
    }

    fun usePrimaryScreen() {
        if (isAlternateScreen) {
            buffer = primaryBuffer
            isAlternateScreen = false
            restoreCursor()
            resetScrollingRegion()
        }
    }

    fun isUsingAlternateScreen(): Boolean = isAlternateScreen

    fun insertLines(count: Int = 1) {
        pendingWrap = false
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return

        val linesToInsert = count.coerceIn(1, scrollBottom - cursorRow + 1)
        for (i in 0 until linesToInsert) {
            val bottomRow = buffer[scrollBottom]
            for (row in scrollBottom downTo cursorRow + 1) {
                buffer[row] = buffer[row - 1]
            }
            if (bottomRow.clearCells(0, cols)) {
                bottomRow.incrementVersion()
            }
            buffer[cursorRow] = bottomRow
        }
    }

    fun deleteLines(count: Int = 1) {
        pendingWrap = false
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return

        val linesToDelete = count.coerceIn(1, scrollBottom - cursorRow + 1)
        for (i in 0 until linesToDelete) {
            val cursorRowRef = buffer[cursorRow]
            for (row in cursorRow until scrollBottom) {
                buffer[row] = buffer[row + 1]
            }
            if (cursorRowRef.clearCells(0, cols)) {
                cursorRowRef.incrementVersion()
            }
            buffer[scrollBottom] = cursorRowRef
        }
    }

    fun deleteCharacters(count: Int) {
        pendingWrap = false
        val deleteCount = count.coerceIn(0, cols - cursorCol)
        if (deleteCount <= 0) return

        val currentRow = buffer[cursorRow]
        var changed = false

        for (col in cursorCol until (cols - deleteCount)) {
            if (currentRow.setCell(col, currentRow[col + deleteCount])) {
                changed = true
            }
        }

        if (currentRow.clearCells(cols - deleteCount, cols)) {
            changed = true
        }

        if (changed) {
            currentRow.incrementVersion()
        }
    }

    fun insertCharacters(count: Int) {
        pendingWrap = false
        val insertCount = count.coerceIn(0, cols - cursorCol)
        if (insertCount <= 0) return

        val currentRow = buffer[cursorRow]
        var changed = false

        for (col in (cols - 1) downTo (cursorCol + insertCount)) {
            if (currentRow.setCell(col, currentRow[col - insertCount])) {
                changed = true
            }
        }

        if (currentRow.clearCells(cursorCol, cursorCol + insertCount)) {
            changed = true
        }

        if (changed) {
            currentRow.incrementVersion()
        }
    }

    fun eraseCharacters(count: Int) {
        pendingWrap = false
        val eraseCount = count.coerceIn(0, cols - cursorCol)
        if (eraseCount <= 0) return

        val currentRow = buffer[cursorRow]
        if (currentRow.clearCells(cursorCol, cursorCol + eraseCount)) {
            currentRow.incrementVersion()
        }
    }

    fun setScrollingRegion(
        top: Int,
        bottom: Int,
    ) {
        scrollTop = top.coerceIn(0, rows - 1)
        scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
    }

    fun resetScrollingRegion() {
        scrollTop = 0
        scrollBottom = rows - 1
    }

    companion object {
        private val urlPattern = Regex("(https?://[\\w\\-\\._~:/?#\\[\\]@!$&'()*+,;=]+)")

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
         * Finds URL match boundaries within a row of terminal cells.
         */
        fun getUrlRanges(row: Array<TerminalCell>): List<UrlRange> = UrlCache.getOrPut(row) {
            val ranges = mutableListOf<UrlRange>()
            val text = TerminalRowCharSequence(row)
            val matches = urlPattern.findAll(text)

            for (match in matches) {
                val start = match.range.first
                val end = match.range.last + 1
                ranges.add(UrlRange(start, end))
            }

            if (ranges.isEmpty()) emptyList() else ranges.toList()
        }

        /**
         * Extracts plain text URL string from a cell row given a [UrlRange].
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
            return TerminalRowCharSequence(row, start + startIndex, start + endIndex)
        }

        override fun toString(): String {
            val len = length
            if (len <= 0) return ""
            val chars = CharArray(len)
            for (i in 0 until len) {
                chars[i] = row[start + i].char
            }
            return chars.concatToString()
        }
    }

    /**
     * Searches all visible and scrollback rows for the specified query string.
     */
    fun search(
        query: String,
        ignoreCase: Boolean = true,
    ): List<TerminalMatch> {
        if (query.isEmpty()) return emptyList()

        val matches = mutableListOf<TerminalMatch>()

        for (i in 0 until scrollback.size) {
            searchRow(i, scrollback[i], query, ignoreCase, matches)
        }

        val scrollbackSize = scrollback.size
        val isQueryOnlySpaces = query.all { it == ' ' }
        for (i in 0 until rows) {
            val terminalRow = buffer[i]
            if (terminalRow.isEmpty() && !isQueryOnlySpaces) continue
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
            searchStart = foundAt + 1
        }
    }

    private fun Array<TerminalCell>.indexOf(query: String, startIndex: Int, ignoreCase: Boolean): Int {
        val maxStart = size - query.length
        if (startIndex > maxStart) return -1

        val firstChar = query[0]
        val queryLen = query.length

        for (i in startIndex..maxStart) {
            if (this[i].char.equals(firstChar, ignoreCase)) {
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
     * Resizes screen buffer dimensions while preserving existing text content.
     *
     * @return true if dimensions changed, false if unchanged.
     */
    fun resize(
        newRows: Int,
        newCols: Int,
    ): Boolean {
        if (newRows == rows && newCols == cols) return false
        if (newRows <= 0 || newCols <= 0) return false

        val newPrimaryBuffer: Array<TerminalRow>
        val newAlternateBuffer: Array<TerminalRow>

        if (newCols == cols) {
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
            val rowsToCopy = minOf(rows, newRows)
            val colsToCopy = minOf(cols, newCols)

            newPrimaryBuffer =
                Array(newRows) { i ->
                    val newRow = TerminalRow(newCols)
                    if (i < rowsToCopy) {
                        val oldRow = primaryBuffer[i]
                        if (!oldRow.isEmpty()) {
                            oldRow.copyInto(newRow.cells, 0, 0, colsToCopy)
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
                        if (!oldRow.isEmpty()) {
                            oldRow.copyInto(newRow.cells, 0, 0, colsToCopy)
                            newRow.recalculateNonDefaultCells()
                        }
                    }
                    newRow
                }
        }

        rows = newRows
        cols = newCols
        primaryBuffer = newPrimaryBuffer
        alternateBuffer = newAlternateBuffer
        buffer = if (isAlternateScreen) alternateBuffer else primaryBuffer

        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorCol = cursorCol.coerceIn(0, newCols - 1)
        pendingWrap = false
        scrollTop = 0
        scrollBottom = newRows - 1

        return true
    }
}

/**
 * Character column range representing a detected URL.
 */
data class UrlRange(
    val startCol: Int,
    val endCol: Int
)
