package com.johnan.terminal.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe terminal emulator engine that processes byte/string output, executes
 * ANSI/VT escape sequences, manages screen buffers, and exposes reactive UI state.
 */
class TerminalEmulator(
    val config: TerminalConfig = TerminalConfig.DEFAULT,
    private val onOsc52WriteRequested: ((text: String, onConfirm: () -> Unit) -> Unit)? = null,
    private val logCallback: ((String) -> Unit)? = null,
    private val onTerminalResponse: ((String) -> Unit)? = null,
) {
    @Deprecated(
        message = "Use primary constructor with TerminalConfig",
        replaceWith =
            ReplaceWith(
                expression =
                    "TerminalEmulator(" +
                        "TerminalConfig(initialRows = rows, initialCols = cols, osc52Policy = osc52Policy), " +
                        "onOsc52WriteRequested, logCallback, onTerminalResponse)",
                imports = ["com.johnan.terminal.core.TerminalConfig"],
            ),
    )
    constructor(
        rows: Int = 24,
        cols: Int = 80,
        osc52Policy: Osc52Policy = Osc52Policy.ASK,
        onOsc52WriteRequested: ((text: String, onConfirm: () -> Unit) -> Unit)? = null,
        logCallback: ((String) -> Unit)? = null,
        onTerminalResponse: ((String) -> Unit)? = null,
    ) : this(
        config =
            TerminalConfig(
                initialRows = rows,
                initialCols = cols,
                osc52Policy = osc52Policy,
            ),
        onOsc52WriteRequested = onOsc52WriteRequested,
        logCallback = logCallback,
        onTerminalResponse = onTerminalResponse,
    )

    val osc52Policy: Osc52Policy
        get() = config.osc52Policy

    private var screenBuffer = ScreenBuffer(config.initialRows, config.initialCols, config.maxScrollback)

    var applicationCursorKeysEnabled = false
        internal set

    var applicationKeypadModeEnabled = false
        internal set

    var originModeEnabled = false
        internal set

    var bracketedPasteModeEnabled = false
        internal set

    private var ansiParser = AnsiParser(this, screenBuffer, logCallback)
    private val mutex = Mutex()

    private var cachedScrollback: List<Array<TerminalCell>> = emptyList()
    private var cachedScrollbackVersion: Long = -1

    private val _screenState = MutableStateFlow(TerminalScreenState.from(screenBuffer))
    val screenState: StateFlow<TerminalScreenState> = _screenState.asStateFlow()

    private val _windowTitle = MutableStateFlow<String?>(null)
    val windowTitle: StateFlow<String?> = _windowTitle.asStateFlow()

    private val windowTitleStack = ArrayDeque<String>()

    var cursorBlinking = false
        internal set

    var invertScreenColors = false
        internal set

    var mouseTrackingMode = MouseTrackingMode.None
        internal set

    var sgrMouseModeEnabled = false
        internal set

    var headlessMode: Boolean = false

    private val _clipboardEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val clipboardEvents: SharedFlow<String> = _clipboardEvents.asSharedFlow()

    internal fun copyToClipboard(text: String) {
        _clipboardEvents.tryEmit(text)
    }

    /**
     * Number of OSC 52 clipboard write requests received, regardless of [Osc52Policy].
     *
     * Lets consumers diff clipboard state between snapshots (see [copy]) without collecting [clipboardEvents].
     */
    var clipboardWriteCount: Long = 0
        private set

    /**
     * Payload of the most recent OSC 52 clipboard write request, regardless of [Osc52Policy], or null if none.
     */
    var lastClipboardWrite: String? = null
        private set

    internal fun handleOsc52Write(text: String) {
        clipboardWriteCount++
        lastClipboardWrite = text
        when (osc52Policy) {
            Osc52Policy.ALWAYS_ALLOW -> {
                copyToClipboard(text)
                logCallback?.invoke("OSC 52: Copied to clipboard (Policy: ALWAYS_ALLOW)")
            }
            Osc52Policy.ALWAYS_DENY -> {
                logCallback?.invoke("OSC 52: Write request ignored (Policy: ALWAYS_DENY)")
            }
            Osc52Policy.ASK -> {
                if (onOsc52WriteRequested != null) {
                    onOsc52WriteRequested.invoke(text) {
                        copyToClipboard(text)
                    }
                    logCallback?.invoke("OSC 52: Triggered confirmation prompt for user")
                } else {
                    logCallback?.invoke("OSC 52: Write request ignored (Policy: ASK, no listener attached)")
                }
            }
        }
    }

    private val _bellEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val bellEvents: SharedFlow<Unit> = _bellEvents.asSharedFlow()

    private val _attentionEvents = MutableSharedFlow<AttentionEvent>(extraBufferCapacity = 64)
    val attentionEvents: SharedFlow<AttentionEvent> = _attentionEvents.asSharedFlow()

    /**
     * Number of bells (BEL) received so far.
     *
     * Lets consumers diff bell state between snapshots (see [copy]) without collecting [bellEvents].
     */
    var bellCount: Long = 0
        internal set

    internal fun triggerBell() {
        bellCount++
        _bellEvents.tryEmit(Unit)
        _attentionEvents.tryEmit(AttentionEvent.Bell())
    }

    internal fun triggerNotification(
        title: String,
        message: String,
    ) {
        _attentionEvents.tryEmit(AttentionEvent.Notification(title, message))
    }

    internal fun sendResponse(response: String) {
        onTerminalResponse?.invoke(response)
    }

    internal fun setWindowTitle(title: String) {
        _windowTitle.value = title
    }

    internal fun pushWindowTitle() {
        windowTitleStack.addLast(_windowTitle.value ?: "")
    }

    internal fun popWindowTitle() {
        val title = windowTitleStack.removeLastOrNull()
        if (title != null) {
            _windowTitle.value = if (title.isEmpty()) null else title
        }
    }

    /**
     * Returns a fully independent deep copy of this emulator.
     *
     * The copy carries the screen buffers (primary, alternate and scrollback), cursor and saved cursor, text
     * attributes, scroll region, all mode flags, window title and title stack, [config], [bellCount], the clipboard
     * write state and any partially parsed escape or UTF-8 sequence, so feeding the same bytes to the copy
     * and to this emulator yields the same result from this point on. Mutating one never affects the other.
     *
     * Not copied: the callbacks (pass them here, they default to none), the processing mutex, and the reactive
     * flows ([clipboardEvents], [bellEvents], [attentionEvents] start without replay). [screenState] is recomputed
     * from the copied buffer and the internal render caches start empty.
     *
     * This is not synchronised with [processOutput]; call it from the context that feeds this emulator, not
     * concurrently with it.
     */
    fun copy(
        onOsc52WriteRequested: ((String, () -> Unit) -> Unit)? = null,
        logCallback: ((String) -> Unit)? = null,
        onTerminalResponse: ((String) -> Unit)? = null,
    ): TerminalEmulator {
        // Intentionally not copied: mutex, flows, render caches (cachedScrollback*) and callbacks; see KDoc.
        val clone = TerminalEmulator(config, onOsc52WriteRequested, logCallback, onTerminalResponse)
        val bufferCopy = screenBuffer.copy()
        clone.screenBuffer = bufferCopy
        clone.ansiParser = ansiParser.copy(clone, bufferCopy, logCallback)
        clone.applicationCursorKeysEnabled = applicationCursorKeysEnabled
        clone.applicationKeypadModeEnabled = applicationKeypadModeEnabled
        clone.originModeEnabled = originModeEnabled
        clone.bracketedPasteModeEnabled = bracketedPasteModeEnabled
        clone.cursorBlinking = cursorBlinking
        clone.invertScreenColors = invertScreenColors
        clone.mouseTrackingMode = mouseTrackingMode
        clone.sgrMouseModeEnabled = sgrMouseModeEnabled
        clone.headlessMode = headlessMode
        clone._windowTitle.value = _windowTitle.value
        clone.windowTitleStack.addAll(windowTitleStack)
        clone.bellCount = bellCount
        clone.clipboardWriteCount = clipboardWriteCount
        clone.lastClipboardWrite = lastClipboardWrite
        clone.updateScreenState()
        return clone
    }

    /**
     * Parses and applies incoming terminal output bytes synchronously without acquiring [mutex].
     */
    fun processOutputSync(data: ByteArray, length: Int = data.size) {
        ansiParser.processBytes(data, length)
        updateScreenState()
    }

    /**
     * Parses and applies incoming terminal output text synchronously without acquiring [mutex].
     */
    fun processOutputSync(text: String) {
        processOutputSync(text.encodeToByteArray())
    }

    /**
     * Parses and applies incoming terminal output bytes.
     */
    suspend fun processOutput(data: ByteArray) {
        mutex.withLock {
            processOutputSync(data)
        }
    }

    /**
     * Parses and applies incoming terminal output text.
     */
    suspend fun processOutput(text: String) {
        processOutput(text.encodeToByteArray())
    }

    private fun updateScreenState() {
        if (headlessMode && _screenState.subscriptionCount.value == 0) return
        _screenState.value = createScreenState()
    }

    fun snapshotScreenState(): TerminalScreenState = createScreenState().also { _screenState.value = it }

    private fun createScreenState(): TerminalScreenState {
        val rows = ArrayList<Array<TerminalCell>>(screenBuffer.rows)
        var contentHeight = 0

        for (i in 0 until screenBuffer.rows) {
            val terminalRow = screenBuffer.getTerminalRow(i)

            if (!terminalRow.isEmpty()) {
                contentHeight = i + 1
            }

            val snapshot = if (terminalRow.cachedSnapshotVersion == terminalRow.version) {
                terminalRow.cachedSnapshot!!
            } else {
                val copy = terminalRow.copyOf()
                terminalRow.cachedSnapshot = copy
                terminalRow.cachedSnapshotVersion = terminalRow.version
                copy
            }
            rows.add(snapshot)
        }

        val currentScrollbackVersion = screenBuffer.scrollbackVersion
        val scrollback = if (currentScrollbackVersion == cachedScrollbackVersion) {
            cachedScrollback
        } else {
            val list = screenBuffer.getScrollback()
            cachedScrollback = list
            cachedScrollbackVersion = currentScrollbackVersion
            list
        }

        return TerminalScreenState(
            rows = rows,
            cols = screenBuffer.cols,
            terminalRows = screenBuffer.rows,
            cursorRow = screenBuffer.cursorRow,
            cursorCol = screenBuffer.cursorCol,
            cursorVisible = screenBuffer.cursorVisible,
            scrollback = scrollback,
            contentHeight = contentHeight,
            mouseTrackingMode = mouseTrackingMode,
            sgrMouseModeEnabled = sgrMouseModeEnabled,
            bracketedPasteModeEnabled = bracketedPasteModeEnabled,
        )
    }

    /**
     * Resizes the screen buffer dimensions synchronously and updates reactive screen state.
     *
     * @return true if dimensions changed, false if unchanged.
     */
    fun resizeSync(newRows: Int, newCols: Int): Boolean {
        val resized = screenBuffer.resize(newRows, newCols)
        if (resized) {
            updateScreenState()
        }
        return resized
    }

    /**
     * Resizes the screen buffer dimensions and updates reactive screen state.
     *
     * @return true if dimensions changed, false if unchanged.
     */
    suspend fun resize(
        newRows: Int,
        newCols: Int,
    ): Boolean = mutex.withLock {
        resizeSync(newRows, newCols)
    }

    /**
     * Clears all content on the active screen buffer and resets cursor position synchronously.
     */
    fun clearSync() {
        screenBuffer.clearScreen()
        screenBuffer.setCursorPosition(0, 0)
        updateScreenState()
    }

    /**
     * Clears all content on the active screen buffer and resets cursor position.
     */
    suspend fun clear() {
        mutex.withLock {
            clearSync()
        }
    }

    /**
     * Resets screen buffer, cursor, and text formatting attributes to defaults synchronously.
     */
    fun resetSync() {
        screenBuffer.clearScreen()
        screenBuffer.setCursorPosition(0, 0)
        screenBuffer.resetTextAttributes()
        updateScreenState()
    }

    /**
     * Resets screen buffer, cursor, and text formatting attributes to defaults.
     */
    suspend fun reset() {
        mutex.withLock {
            resetSync()
        }
    }

    /**
     * Returns direct reference to underlying [ScreenBuffer] for selection and coordinate math.
     */
    fun getScreenBuffer(): ScreenBuffer = screenBuffer

    /**
     * Searches visible rows and scrollback buffer for matching text occurrences synchronously.
     */
    fun searchSync(
        query: String,
        ignoreCase: Boolean = true,
    ): List<TerminalMatch> = screenBuffer.search(query, ignoreCase)

    /**
     * Searches visible rows and scrollback buffer for matching text occurrences.
     */
    suspend fun search(
        query: String,
        ignoreCase: Boolean = true,
    ): List<TerminalMatch> = mutex.withLock {
        searchSync(query, ignoreCase)
    }

    /**
     * Packs visible terminal grid cells into [outBuffer] using 4 integers per cell:
     * - `offset + 0`: Unicode codepoint (`cell.char.code`)
     * - `offset + 1`: Effective foreground 24-bit RGB (`0xRRGGBB`)
     * - `offset + 2`: Effective background 24-bit RGB (`0xRRGGBB` or `-1` for transparent)
     * - `offset + 3`: Attribute bitmask:
     *   bit 0 = bold, bit 1 = italic, bit 2 = underline, bit 3 = reverse,
     *   bit 4 = dim, bit 5 = blink, bit 6 = strikethrough, bit 7 = overline,
     *   bit 8 = conceal, bit 9 = row is soft-wrapped (`isRowWrapped(row)`)
     *
     * @return Number of cells written (`0` if [outBuffer] is empty).
     */
    fun packGridToBuffer(
        outBuffer: IntArray,
        palette: TerminalPalette = TerminalPalette.DEFAULT,
        isDark: Boolean = true,
    ): Int {
        val maxCells = outBuffer.size / PACKED_CELL_INTS
        return packGridCells(maxCells, palette, isDark) { outOffset, codepoint, fgRgb, bgRgb, attrs ->
            outBuffer[outOffset] = codepoint
            outBuffer[outOffset + 1] = fgRgb
            outBuffer[outOffset + 2] = bgRgb
            outBuffer[outOffset + 3] = attrs
        }
    }

    internal inline fun packGridCells(
        maxCells: Int,
        palette: TerminalPalette,
        isDark: Boolean,
        writeCell: (outOffset: Int, codepoint: Int, fgRgb: Int, bgRgb: Int, attrs: Int) -> Unit,
    ): Int = packViewportCells(
        maxCells = maxCells,
        scrollbackOffset = 0,
        palette = palette,
        isDark = isDark,
        writeCell = writeCell,
    )

    internal inline fun packViewportCells(
        maxCells: Int,
        scrollbackOffset: Int,
        palette: TerminalPalette?,
        isDark: Boolean,
        writeCell: (outOffset: Int, codepoint: Int, fgRgb: Int, bgRgb: Int, attrs: Int) -> Unit,
    ): Int {
        if (maxCells <= 0) return 0
        val totalRows = screenBuffer.rows
        val totalCols = screenBuffer.cols
        val sbSize = if (screenBuffer.isUsingAlternateScreen()) 0 else screenBuffer.scrollbackSize
        val clampedOffset = scrollbackOffset.coerceIn(0, sbSize)
        val startLine = sbSize - clampedOffset

        var cellsWritten = 0
        var outOffset = 0
        for (r in 0 until totalRows) {
            val lineIdx = startLine + r
            val rowCells: Array<TerminalCell>
            val isWrapped: Boolean
            if (lineIdx < sbSize) {
                rowCells = screenBuffer.getScrollbackRow(lineIdx)
                isWrapped = false
            } else {
                val termRow = screenBuffer.getTerminalRow(lineIdx - sbSize)
                rowCells = termRow.cells
                isWrapped = termRow.isWrapped
            }
            val wrappedBit = if (isWrapped) ATTR_ROW_WRAPPED else 0
            for (c in 0 until totalCols) {
                if (cellsWritten >= maxCells) return cellsWritten
                val cell = rowCells[c]
                val fgRgb = if (palette == null) {
                    TerminalColor.toRawColorInt(cell.foregroundColor)
                } else {
                    TerminalColor.toRgbInt(cell.getEffectiveForegroundColor(), palette, isDark, false)
                }
                val bgRgb = if (palette == null) {
                    TerminalColor.toRawColorInt(cell.backgroundColor)
                } else {
                    TerminalColor.toRgbInt(cell.getEffectiveBackgroundColor(), palette, isDark, true)
                }
                var attrs = wrappedBit
                if (cell.bold) attrs = attrs or ATTR_BOLD
                if (cell.italic) attrs = attrs or ATTR_ITALIC
                if (cell.underline) attrs = attrs or ATTR_UNDERLINE
                if (cell.reverse) attrs = attrs or ATTR_REVERSE
                if (cell.dim) attrs = attrs or ATTR_DIM
                if (cell.blink) attrs = attrs or ATTR_BLINK
                if (cell.strikethrough) attrs = attrs or ATTR_STRIKETHROUGH
                if (cell.overline) attrs = attrs or ATTR_OVERLINE
                if (cell.conceal) attrs = attrs or ATTR_CONCEAL
                if (cell.width == 2) attrs = attrs or ATTR_WIDE_CHAR
                if (cell.isWideSpacer) attrs = attrs or ATTR_WIDE_SPACER

                writeCell(outOffset, cell.codepoint, fgRgb, bgRgb, attrs)
                outOffset += PACKED_CELL_INTS
                cellsWritten++
            }
        }
        return cellsWritten
    }

    /**
     * Formats pasted text with bracketed paste escape sequences if enabled in config and requested by the terminal.
     */
    fun formatPaste(text: String): String =
        if (config.bracketedPasteEnabled && bracketedPasteModeEnabled) {
            "\u001B[200~$text\u001B[201~"
        } else {
            text
        }

    companion object {
        const val PACKED_CELL_INTS: Int = 4
        const val ATTR_BOLD: Int = 1 shl 0
        const val ATTR_ITALIC: Int = 1 shl 1
        const val ATTR_UNDERLINE: Int = 1 shl 2
        const val ATTR_REVERSE: Int = 1 shl 3
        const val ATTR_DIM: Int = 1 shl 4
        const val ATTR_BLINK: Int = 1 shl 5
        const val ATTR_STRIKETHROUGH: Int = 1 shl 6
        const val ATTR_OVERLINE: Int = 1 shl 7
        const val ATTR_CONCEAL: Int = 1 shl 8
        const val ATTR_ROW_WRAPPED: Int = 1 shl 9
        const val ATTR_WIDE_CHAR: Int = 1 shl 10
        const val ATTR_WIDE_SPACER: Int = 1 shl 11
    }
}

/**
 * Immutable snapshot of terminal screen cells, cursor, and scrollback for rendering.
 */
data class TerminalScreenState(
    val rows: List<Array<TerminalCell>>,
    val cols: Int,
    val terminalRows: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val scrollback: List<Array<TerminalCell>>,
    val contentHeight: Int = 0,
    val mouseTrackingMode: MouseTrackingMode = MouseTrackingMode.None,
    val sgrMouseModeEnabled: Boolean = false,
    val bracketedPasteModeEnabled: Boolean = false,
) {
    companion object {
        fun from(
            buffer: ScreenBuffer,
            mouseTrackingMode: MouseTrackingMode = MouseTrackingMode.None,
            sgrMouseModeEnabled: Boolean = false,
            bracketedPasteModeEnabled: Boolean = false,
        ): TerminalScreenState {
            var contentHeight = 0
            for (i in 0 until buffer.rows) {
                if (!buffer.getTerminalRow(i).isEmpty()) {
                    contentHeight = i + 1
                }
            }

            return TerminalScreenState(
                rows = buffer.getAllRows(),
                cols = buffer.cols,
                terminalRows = buffer.rows,
                cursorRow = buffer.cursorRow,
                cursorCol = buffer.cursorCol,
                cursorVisible = buffer.cursorVisible,
                scrollback = buffer.getScrollback(),
                contentHeight = contentHeight,
                mouseTrackingMode = mouseTrackingMode,
                sgrMouseModeEnabled = sgrMouseModeEnabled,
                bracketedPasteModeEnabled = bracketedPasteModeEnabled,
            )
        }
    }

    fun getRow(index: Int): Array<TerminalCell>? = rows.getOrNull(index)

    fun getRowCount(): Int = rows.size

    fun getColCount(): Int = cols
}
