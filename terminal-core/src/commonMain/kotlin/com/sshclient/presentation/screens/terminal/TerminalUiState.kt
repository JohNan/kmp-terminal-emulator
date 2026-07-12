package com.sshclient.presentation.screens.terminal

import com.sshclient.data.terminal.TerminalMatch
import com.sshclient.data.terminal.TerminalScreenState

// Redefine LogEntry locally to decouple from the main SSH app
data class LogEntry(
    val id: String,
    val message: String,
    val timestamp: kotlinx.datetime.Instant,
    val type: String = "info"
)

// Redefine VerificationResult locally to decouple from the main SSH app
enum class VerificationResult {
    MATCHED,
    MISMATCH,
    NOT_FOUND
}

/**
 * UI state for the terminal screen
 */
sealed interface TerminalUiState {
    /**
     * Terminal is active and showing output
     */
    data class Active(
        val hostId: String?,
        val hostName: String,
        val terminalState: TerminalScreenState? = null,
        val systemLogs: List<LogEntry> = emptyList(),
        val connecting: Boolean = false,
        val connectionError: String? = null,
        val passphraseRequired: PassphraseState? = null,
        val passwordRequired: PasswordState? = null,
        val proxyPasswordRequired: PasswordState? = null,
        val hostKeyVerification: HostKeyVerificationState? = null,
        val connectionHealth: ConnectionHealth = ConnectionHealth.Healthy,
        val showSystemLogs: Boolean = false,
        val selectionState: SelectionState = SelectionState.None,
        val searchState: SearchState = SearchState(),
        val pasteConfirmation: PasteConfirmationState? = null,
    ) : TerminalUiState

    /**
     * Disconnected
     */
    data object Disconnected : TerminalUiState
}

/**
 * Connection health status
 */
sealed interface ConnectionHealth {
    data object Healthy : ConnectionHealth
    data class Lost(
        val reason: String,
        val canReconnect: Boolean = true,
    ) : ConnectionHealth
    data class Degraded(
        val reason: String,
    ) : ConnectionHealth
}

/**
 * State for passphrase dialog
 */
data class PassphraseState(
    val keyName: String,
    val keyId: String,
    val error: String? = null,
    val isLoading: Boolean = false,
    val rememberPassphrase: Boolean = false,
)

/**
 * State for password dialog
 */
data class PasswordState(
    val username: String,
    val error: String? = null,
    val isLoading: Boolean = false,
    val savePassword: Boolean = false,
)

/**
 * State for host key verification dialog
 */
data class HostKeyVerificationState(
    val verificationResult: VerificationResult,
)

/**
 * State for Find in Terminal feature
 */
data class SearchState(
    val isVisible: Boolean = false,
    val query: String = "",
    val matches: List<TerminalMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
)

/**
 * State for paste confirmation dialog
 */
data class PasteConfirmationState(
    val textToPaste: String,
)
