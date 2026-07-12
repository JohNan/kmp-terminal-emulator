package com.sshclient.composeapp.presentation.screens.terminal

/**
 * Represents a line of terminal output
 */
sealed interface TerminalOutput {
    val text: String

    /**
     * Normal command output
     */
    data class CommandOutput(override val text: String) : TerminalOutput

    /**
     * System/log output (e.g., connection status, errors, information)
     */
    data class SystemLog(override val text: String) : TerminalOutput
}
