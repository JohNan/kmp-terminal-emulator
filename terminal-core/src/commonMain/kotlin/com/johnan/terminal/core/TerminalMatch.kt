package com.johnan.terminal.core

/**
 * Text match coordinate boundaries within the terminal buffer (0-indexed).
 */
data class TerminalMatch(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
)
