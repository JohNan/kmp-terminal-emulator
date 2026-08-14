package com.antigravity.agy.android.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.agy.android.network.ConnectionState
import com.antigravity.agy.android.network.WorkspaceClient
import com.sshclient.data.terminal.TerminalEmulator
import com.sshclient.data.terminal.TerminalScreenState
import com.sshclient.presentation.screens.terminal.ArrowDirection
import com.sshclient.presentation.screens.terminal.ConnectionHealth
import com.sshclient.presentation.screens.terminal.TerminalUiState
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Represents an active terminal workspace session containing its emulator and network client.
 */
class WorkspaceSession(
    val workspaceId: String,
    val emulator: TerminalEmulator,
    val client: WorkspaceClient,
    val title: StateFlow<String?>,
    val connectionState: StateFlow<ConnectionState>,
    val screenState: StateFlow<TerminalScreenState>,
    private val onSendInput: (String) -> Unit,
    private val onResize: (Int, Int) -> Unit,
) {
    /**
     * Sends user keyboard input to this workspace.
     */
    fun sendInput(data: String) {
        onSendInput(data)
    }

    /**
     * Resizes this workspace emulator and notifies the backend PTY.
     */
    fun resize(rows: Int, cols: Int) {
        onResize(rows, cols)
    }

    /**
     * Reconnects the underlying WebSocket client.
     */
    fun reconnect() {
        client.connect()
    }

    /**
     * Disconnects the underlying WebSocket client.
     */
    fun disconnect() {
        client.disconnect()
    }
}

/**
 * ViewModel managing active workspace sessions, settings, and connecting
 * `:terminal-core` [TerminalEmulator] state to [WorkspaceClient] network instances.
 */
class TerminalViewModel(
    private val settingsRepository: SettingsRepository,
    private val httpClient: HttpClient = WorkspaceClient.createDefaultHttpClient(),
) : ViewModel() {
    // Map of active workspace IDs to their corresponding WorkspaceSession
    private val _workspaces = MutableStateFlow<Map<String, WorkspaceSession>>(emptyMap())
    val workspaces: StateFlow<Map<String, WorkspaceSession>> = _workspaces.asStateFlow()

    // Currently focused / active workspace ID
    private val _activeWorkspaceId = MutableStateFlow<String?>("default")
    val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId.asStateFlow()

    // Combined settings state flow
    val settings: StateFlow<TerminalSettings> =
        settingsRepository.settingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TerminalSettings(),
        )

    // Active session flow
    val activeSession: StateFlow<WorkspaceSession?> =
        combine(_activeWorkspaceId, _workspaces) { activeId, workspaceMap ->
            activeId?.let { workspaceMap[it] }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    // TerminalScreenState of the active session
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeTerminalScreenState: StateFlow<TerminalScreenState?> =
        activeSession
            .flatMapLatest { session ->
                session?.screenState ?: flowOf(null)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

    // Maps the active session to TerminalUiState from :terminal-core
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TerminalUiState> =
        activeSession
            .flatMapLatest { session ->
                if (session == null) {
                    flowOf(TerminalUiState.Disconnected)
                } else {
                    combine(
                        session.screenState,
                        session.connectionState,
                        session.title,
                    ) { screenState, connState, title ->
                        val health = when (connState) {
                            is ConnectionState.Connected -> ConnectionHealth.Healthy
                            is ConnectionState.Connecting -> ConnectionHealth.Degraded("Connecting...")
                            is ConnectionState.Error -> ConnectionHealth.Lost(connState.message, canReconnect = true)
                            is ConnectionState.Disconnected -> ConnectionHealth.Lost(
                                "Disconnected",
                                canReconnect = true
                            )
                        }

                        TerminalUiState.Active(
                            hostId = session.workspaceId,
                            hostName = title ?: session.workspaceId,
                            terminalState = screenState,
                            connecting = connState is ConnectionState.Connecting,
                            connectionError = if (connState is ConnectionState.Error) connState.message else null,
                            connectionHealth = health,
                        )
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = TerminalUiState.Disconnected,
            )

    init {
        // Automatically open the default workspace on initialization
        openWorkspace("default")
    }

    /**
     * Opens and connects a workspace session for the given [workspaceId].
     */
    fun openWorkspace(workspaceId: String) {
        val cleanId = workspaceId.trim().ifEmpty { "default" }
        if (_workspaces.value.containsKey(cleanId)) {
            switchWorkspace(cleanId)
            return
        }

        val proxyUrl = settings.value.proxyHostUrl

        // Initialize terminal emulator from :terminal-core
        lateinit var client: WorkspaceClient
        val emulator = TerminalEmulator(
            rows = 24,
            cols = 80,
            onTerminalResponse = { response ->
                viewModelScope.launch {
                    client.sendInput(response)
                }
            },
        )

        // Initialize Ktor WebSocket client
        client = WorkspaceClient(
            workspaceId = cleanId,
            proxyHostUrl = proxyUrl,
            client = httpClient,
            coroutineScope = viewModelScope,
        )

        // Connect output callback: WorkspaceClient -> TerminalEmulator
        viewModelScope.launch {
            client.outputFlow.collect { outputBytes ->
                emulator.processOutput(outputBytes)
            }
        }

        val session = WorkspaceSession(
            workspaceId = cleanId,
            emulator = emulator,
            client = client,
            title = client.titleFlow,
            connectionState = client.connectionState,
            screenState = emulator.screenState,
            onSendInput = { input ->
                viewModelScope.launch {
                    client.sendInput(input)
                }
            },
            onResize = { rows, cols ->
                viewModelScope.launch {
                    emulator.resize(rows, cols)
                    client.sendResize(cols, rows)
                }
            },
        )

        // Start connection
        client.connect()

        _workspaces.value = _workspaces.value + (cleanId to session)
        switchWorkspace(cleanId)
    }

    /**
     * Switches the active workspace, pausing background PTY and resuming the foreground PTY.
     */
    fun switchWorkspace(workspaceId: String) {
        val currentId = _activeWorkspaceId.value
        if (currentId == workspaceId) return

        val currentSession = currentId?.let { _workspaces.value[it] }
        val newSession = _workspaces.value[workspaceId]

        if (newSession != null) {
            // Pause background session
            viewModelScope.launch {
                currentSession?.client?.sendPause()
                newSession.client.sendResume()
            }
            _activeWorkspaceId.value = workspaceId
        }
    }

    /**
     * Closes and cleans up a workspace session.
     */
    fun closeWorkspace(workspaceId: String) {
        val session = _workspaces.value[workspaceId] ?: return
        session.disconnect()

        val updated = _workspaces.value.toMutableMap()
        updated.remove(workspaceId)
        _workspaces.value = updated

        if (_activeWorkspaceId.value == workspaceId) {
            val nextId = updated.keys.firstOrNull()
            if (nextId != null) {
                switchWorkspace(nextId)
            } else {
                _activeWorkspaceId.value = null
            }
        }
    }

    /**
     * Sends keyboard input string to the active or specified workspace.
     */
    fun sendInput(input: String, workspaceId: String? = null) {
        val targetId = workspaceId ?: _activeWorkspaceId.value ?: return
        val session = _workspaces.value[targetId] ?: return
        session.sendInput(input)
    }

    /**
     * Resizes the terminal grid (rows and columns) for the active or specified workspace.
     */
    fun resizeTerminal(rows: Int, cols: Int, workspaceId: String? = null) {
        val targetId = workspaceId ?: _activeWorkspaceId.value ?: return
        val session = _workspaces.value[targetId] ?: return
        session.resize(rows, cols)
    }

    /**
     * Handles arrow key navigation, supporting application cursor keys mode.
     */
    fun onArrowKey(direction: ArrowDirection, ctrl: Boolean = false, workspaceId: String? = null) {
        val targetId = workspaceId ?: _activeWorkspaceId.value ?: return
        val session = _workspaces.value[targetId] ?: return

        val isAppCursor = session.emulator.applicationCursorKeysEnabled
        val escapeSequence = when (direction) {
            ArrowDirection.UP -> if (isAppCursor) "\u001bOA" else "\u001b[A"
            ArrowDirection.DOWN -> if (isAppCursor) "\u001bOB" else "\u001b[B"
            ArrowDirection.RIGHT -> if (isAppCursor) "\u001bOC" else "\u001b[C"
            ArrowDirection.LEFT -> if (isAppCursor) "\u001bOD" else "\u001b[D"
        }
        session.sendInput(escapeSequence)
    }

    /**
     * Clears the terminal screen buffer.
     */
    fun clearTerminal(workspaceId: String? = null) {
        val targetId = workspaceId ?: _activeWorkspaceId.value ?: return
        val session = _workspaces.value[targetId] ?: return
        viewModelScope.launch {
            session.emulator.clear()
        }
    }

    /**
     * Resets the terminal screen buffer and attributes.
     */
    fun resetTerminal(workspaceId: String? = null) {
        val targetId = workspaceId ?: _activeWorkspaceId.value ?: return
        val session = _workspaces.value[targetId] ?: return
        viewModelScope.launch {
            session.emulator.reset()
        }
    }

    /**
     * Triggers a manual reconnect for the active or specified workspace.
     */
    fun reconnect(workspaceId: String? = null) {
        val targetId = workspaceId ?: _activeWorkspaceId.value ?: return
        val session = _workspaces.value[targetId] ?: return
        session.reconnect()
    }

    /**
     * Updates the Proxy Host URL setting.
     */
    fun updateProxyHostUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setProxyHostUrl(url)
        }
    }

    /**
     * Updates the Terminal Theme setting.
     */
    fun updateTerminalTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setTerminalTheme(theme)
        }
    }

    /**
     * Updates the Terminal Font Size setting.
     */
    fun updateFontSize(size: Float) {
        viewModelScope.launch {
            settingsRepository.setFontSize(size)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _workspaces.value.values.forEach { it.disconnect() }
        _workspaces.value = emptyMap()
    }
}
