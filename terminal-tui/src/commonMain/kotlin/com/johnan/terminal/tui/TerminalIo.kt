package com.johnan.terminal.tui

/**
 * Terminal window dimensions in character cells.
 */
data class TerminalSize(
    val cols: Int,
    val rows: Int
)

/**
 * Platform abstraction for raw terminal I/O, window sizing, and alternate screen lifecycle.
 */
interface TerminalIo {
    /**
     * Puts terminal into raw mode and enables alternate screen, mouse tracking, and bracketed paste.
     */
    fun enterRawTuiMode()

    /**
     * Restores terminal to original cooked mode, shows cursor, and exits alternate screen.
     */
    fun exitRawTuiMode()

    /**
     * Queries current terminal window dimensions in columns and rows.
     */
    fun getTerminalSize(): TerminalSize

    /**
     * Reads available input bytes into [buffer] waiting up to [timeoutMs] milliseconds.
     * Returns the number of bytes read, or 0 if timeout expired without input.
     */
    fun readInput(buffer: ByteArray, timeoutMs: Int = 16): Int

    /**
     * Writes formatted output data directly to terminal stdout.
     */
    fun writeStdout(data: String)
}

/**
 * Factory creating platform-specific [TerminalIo] instance.
 */
expect fun createPlatformTerminalIo(): TerminalIo
