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

    private val screenBuffer = ScreenBuffer(config.initialRows, config.initialCols, config.maxScrollback)

    var applicationCursorKeysEnabled = false
        internal set

    var applicationKeypadModeEnabled = false
        internal set

    var originModeEnabled = false
        internal set

    var bracketedPasteModeEnabled = false
        internal set

    private val ansiParser = AnsiParser(this, screenBuffer, logCallback)
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

    private val _clipboardEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val clipboardEvents: SharedFlow<String> = _clipboardEvents.asSharedFlow()

    internal fun copyToClipboard(text: String) {
        _clipboardEvents.tryEmit(text)
    }

    internal fun handleOsc52Write(text: String) {
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

    internal fun triggerBell() {
        _bellEvents.tryEmit(Unit)
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
     * Parses and applies incoming terminal output bytes.
     */
    suspend fun processOutput(data: ByteArray) {
        mutex.withLock {
            ansiParser.processBytes(data)
            updateScreenState()
        }
    }

    /**
     * Parses and applies incoming terminal output text.
     */
    suspend fun processOutput(text: String) {
        processOutput(text.encodeToByteArray())
    }

    private fun updateScreenState() {
        _screenState.value = createScreenState()
    }

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
        )
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
        val resized = screenBuffer.resize(newRows, newCols)
        if (resized) {
            updateScreenState()
        }
        resized
    }

    /**
     * Clears all content on the active screen buffer and resets cursor position.
     */
    suspend fun clear() {
        mutex.withLock {
            screenBuffer.clearScreen()
            screenBuffer.setCursorPosition(0, 0)
            updateScreenState()
        }
    }

    /**
     * Resets screen buffer, cursor, and text formatting attributes to defaults.
     */
    suspend fun reset() {
        mutex.withLock {
            screenBuffer.clearScreen()
            screenBuffer.setCursorPosition(0, 0)
            screenBuffer.resetTextAttributes()
            updateScreenState()
        }
    }

    /**
     * Returns direct reference to underlying [ScreenBuffer] for selection and coordinate math.
     */
    fun getScreenBuffer(): ScreenBuffer = screenBuffer

    /**
     * Searches visible rows and scrollback buffer for matching text occurrences.
     */
    suspend fun search(
        query: String,
        ignoreCase: Boolean = true,
    ): List<TerminalMatch> = mutex.withLock {
        screenBuffer.search(query, ignoreCase)
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
) {
    companion object {
        fun from(
            buffer: ScreenBuffer,
            mouseTrackingMode: MouseTrackingMode = MouseTrackingMode.None,
            sgrMouseModeEnabled: Boolean = false,
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
            )
        }
    }

    fun getRow(index: Int): Array<TerminalCell>? = rows.getOrNull(index)

    fun getRowCount(): Int = rows.size

    fun getColCount(): Int = cols
}
