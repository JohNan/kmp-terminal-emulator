package com.sshclient.data.terminal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Terminal emulator that processes SSH output and maintains terminal state
 *
 * This class:
 * - Maintains a screen buffer with terminal cells
 * - Processes ANSI escape sequences
 * - Tracks cursor position
 * - Exposes terminal state for rendering
 *
 * Thread-safe: Uses mutex to protect terminal state
 */
class TerminalEmulator(
    rows: Int = 24,
    cols: Int = 80,
    val osc52Policy: Osc52Policy = Osc52Policy.ASK,
    private val onOsc52WriteRequested: ((text: String, onConfirm: () -> Unit) -> Unit)? = null,
    private val logCallback: ((String) -> Unit)? = null,
    private val onTerminalResponse: ((String) -> Unit)? = null,
) {
    // Screen buffer
    private val screenBuffer = ScreenBuffer(rows, cols)

    // State for Application Cursor Keys mode
    var applicationCursorKeysEnabled = false
        internal set // Allow parser to change this state

    // State for Application Keypad Mode
    var applicationKeypadModeEnabled = false
        internal set // Allow parser to change this state

    // State for Origin Mode (DECOM)
    var originModeEnabled = false
        internal set // Allow parser to change this state

    // State for Bracketed Paste Mode (DECPrivate 2004)
    var bracketedPasteModeEnabled = false
        internal set // Allow parser to change this state

    // ANSI parser
    private val ansiParser = AnsiParser(this, screenBuffer, logCallback)

    // Mutex for thread-safe operations
    private val mutex = Mutex()

    // Cache for scrollback list to avoid allocation on every frame
    private var cachedScrollback: List<Array<TerminalCell>> = emptyList()
    private var cachedScrollbackVersion: Long = -1

    // Current screen state (exposed as Flow for Compose)
    private val _screenState = MutableStateFlow(TerminalScreenState.from(screenBuffer))
    val screenState: StateFlow<TerminalScreenState> = _screenState.asStateFlow()

    // Window title state (OSC 0, 2)
    private val _windowTitle = MutableStateFlow<String?>(null)
    val windowTitle: StateFlow<String?> = _windowTitle.asStateFlow()

    // Stack for saving/restoring window titles (CSI 22 t / CSI 23 t)
    private val windowTitleStack = ArrayDeque<String>()

    // Visual mode states
    var cursorBlinking = false
        internal set

    var invertScreenColors = false
        internal set

    // Mouse tracking states
    var mouseTrackingMode = MouseTrackingMode.None
        internal set

    var sgrMouseModeEnabled = false
        internal set

    // Clipboard events (OSC 52)
    private val _clipboardEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val clipboardEvents: SharedFlow<String> = _clipboardEvents.asSharedFlow()

    /**
     * Copy text to clipboard
     */
    internal fun copyToClipboard(text: String) {
        _clipboardEvents.tryEmit(text)
    }

    /**
     * Handle OSC 52 write request according to the active Osc52Policy
     */
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

    // Terminal bell events
    private val _bellEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val bellEvents: SharedFlow<Unit> = _bellEvents.asSharedFlow()

    /**
     * Trigger terminal bell event
     */
    internal fun triggerBell() {
        _bellEvents.tryEmit(Unit)
    }

    /**
     * Send response to remote host
     */
    internal fun sendResponse(response: String) {
        onTerminalResponse?.invoke(response)
    }

    /**
     * Set window title
     */
    internal fun setWindowTitle(title: String) {
        _windowTitle.value = title
    }

    /**
     * Save window title to stack
     */
    internal fun pushWindowTitle() {
        windowTitleStack.addLast(_windowTitle.value ?: "")
    }

    /**
     * Restore window title from stack
     */
    internal fun popWindowTitle() {
        val title = windowTitleStack.removeLastOrNull()
        if (title != null) {
            _windowTitle.value = if (title.isEmpty()) null else title
        }
    }

    /**
     * Process incoming data from SSH (thread-safe)
     * @param data Raw bytes from SSH output
     */
    suspend fun processOutput(data: ByteArray) {
        mutex.withLock {
            ansiParser.processBytes(data)
            updateScreenState()
        }
    }

    /**
     * Process a string of output (thread-safe)
     */
    suspend fun processOutput(text: String) {
        processOutput(text.encodeToByteArray())
    }

    /**
     * Update the screen state flow
     */
    private fun updateScreenState() {
        _screenState.value = createScreenState()
    }

    /**
     * Create screen state using structural sharing cache
     */
    private fun createScreenState(): TerminalScreenState {
        val rows = ArrayList<Array<TerminalCell>>(screenBuffer.rows)
        var contentHeight = 0

        for (i in 0 until screenBuffer.rows) {
            val terminalRow = screenBuffer.getTerminalRow(i)

            // Optimization: Track the last used row to avoid scanning in renderer
            if (!terminalRow.isEmpty()) {
                contentHeight = i + 1
            }

            // Use cached snapshot if version matches
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

        // Use cached scrollback if version matches
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
     * Resize the terminal
     *
     * Dynamically resizes the terminal emulator buffer to match new dimensions.
     * This is part of Chunk 1.1.3: TerminalEmulator Resize.
     *
     * Thread-safe: Acquires mutex lock before resizing.
     *
     * @param newRows New number of rows
     * @param newCols New number of columns
     * @return true if size changed, false if dimensions unchanged
     */
    suspend fun resize(
        newRows: Int,
        newCols: Int,
    ): Boolean {
        return mutex.withLock {
            val resized = screenBuffer.resize(newRows, newCols)
            if (resized) {
                // Update screen state to reflect new dimensions
                updateScreenState()
            }
            resized
        }
    }

    /**
     * Clear the terminal (thread-safe)
     */
    suspend fun clear() {
        mutex.withLock {
            screenBuffer.clearScreen()
            screenBuffer.setCursorPosition(0, 0)
            updateScreenState()
        }
    }

    /**
     * Reset the terminal to initial state (thread-safe)
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
     * Get the screen buffer for text selection
     * (Package-private for selection feature)
     */
    fun getScreenBuffer(): ScreenBuffer = screenBuffer

    /**
     * Search for text in the terminal buffer
     */
    suspend fun search(
        query: String,
        ignoreCase: Boolean = true,
    ): List<TerminalMatch> {
        return mutex.withLock {
            screenBuffer.search(query, ignoreCase)
        }
    }
}

/**
 * Immutable snapshot of terminal screen state for rendering
 */
data class TerminalScreenState(
    val rows: List<Array<TerminalCell>>,
    val cols: Int,
    val terminalRows: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val scrollback: List<Array<TerminalCell>>,
    // Height of content in visible rows (0-indexed index of last non-empty row + 1)
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
            // Initial calculation for from() which is used only on init
            // We iterate buffer rows manually to calculate content height
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

    /**
     * Get a specific row
     */
    fun getRow(index: Int): Array<TerminalCell>? {
        return rows.getOrNull(index)
    }

    /**
     * Get total number of rows
     */
    fun getRowCount(): Int = rows.size

    /**
     * Get number of columns
     */
    fun getColCount(): Int = cols
}
