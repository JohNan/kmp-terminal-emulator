package com.johnan.terminal.ui

/**
 * Line entry model representing command output or system messages.
 */
sealed interface TerminalOutput {
    val text: String

    data class CommandOutput(
        override val text: String
    ) : TerminalOutput

    data class SystemLog(
        override val text: String
    ) : TerminalOutput
}
