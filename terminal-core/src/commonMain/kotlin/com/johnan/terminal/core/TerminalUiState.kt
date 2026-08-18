package com.johnan.terminal.core

data class LogEntry(
    val id: String,
    val message: String,
    val timestamp: kotlinx.datetime.Instant,
    val type: String = "info",
)

enum class VerificationResult {
    MATCHED,
    MISMATCH,
    NOT_FOUND,
}

/**
 * Top-level reactive UI state model for terminal view and session dialogs.
 */
sealed interface TerminalUiState {
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

    data object Disconnected : TerminalUiState
}

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

data class PassphraseState(
    val keyName: String,
    val keyId: String,
    val error: String? = null,
    val isLoading: Boolean = false,
    val rememberPassphrase: Boolean = false,
)

data class PasswordState(
    val username: String,
    val error: String? = null,
    val isLoading: Boolean = false,
    val savePassword: Boolean = false,
)

data class HostKeyVerificationState(
    val verificationResult: VerificationResult,
)

/**
 * Search highlight and query state for find-in-terminal.
 */
data class SearchState(
    val isVisible: Boolean = false,
    val query: String = "",
    val matches: List<TerminalMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
)

data class PasteConfirmationState(
    val textToPaste: String,
)
