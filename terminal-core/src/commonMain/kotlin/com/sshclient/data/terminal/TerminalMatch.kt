package com.sshclient.data.terminal

/**
 * Represents a search match in the terminal buffer.
 * Coordinates are 0-indexed.
 * For `ScreenBuffer.search`, rows are relative to the start of scrollback + buffer.
 */
data class TerminalMatch(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
)
