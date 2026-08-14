package com.antigravity.agy.android.ui

import com.sshclient.data.terminal.ScreenBuffer
import com.sshclient.data.terminal.TerminalEmulator
import com.sshclient.data.terminal.TerminalScreenState
import com.sshclient.domain.model.TerminalColorScheme
import com.sshclient.presentation.screens.terminal.ArrowDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Supported Workspace Agent CLIs
 */
enum class AgentType(val id: String, val displayName: String, val badgeText: String) {
    AGY("agy", "Antigravity (agy)", "AGY"),
    JUNIE("junie", "JetBrains (junie)", "JUNIE");

    companion object {
        fun fromId(id: String): AgentType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: AGY
    }
}

/**
 * Supported Terminal Theme Presets
 */
enum class ThemeOption(val id: String, val displayName: String) {
    GITHUB_DARK("github-dark", "GitHub Dark"),
    DRACULA("dracula", "Dracula"),
    NORD("nord", "Nord"),
    ONE_DARK("one-dark", "One Dark");

    fun toTerminalColorScheme(): TerminalColorScheme {
        return when (this) {
            NORD -> TerminalColorScheme.NORD
            DRACULA -> TerminalColorScheme.MONOKAI // or custom
            ONE_DARK -> TerminalColorScheme.SOLARIZED_DARK // or custom
            GITHUB_DARK -> TerminalColorScheme.DEFAULT
        }
    }

    companion object {
        fun fromId(id: String): ThemeOption =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: GITHUB_DARK
    }
}

/**
 * Mobile Keyboard IME Input Modes
 */
enum class ImeMode(val id: String, val displayName: String) {
    URL("url", "URL Mode (Recommended - Lowercase, No Autocorrect)"),
    EMAIL("email", "Email Mode (Lowercase, No Autocorrect)"),
    TEXT("text", "Standard Text Mode"),
    NONE("none", "Disabled / Off");

    companion object {
        fun fromId(id: String): ImeMode =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: URL
    }
}

/**
 * Connection status for a workspace session
 */
enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    RECONNECTING,
    DISCONNECTED,
    FAILED
}

/**
 * Represents a workspace tab in the top tab bar
 */
data class WorkspaceTab(
    val id: String,
    val name: String,
    val agentType: AgentType = AgentType.AGY,
    val status: ConnectionStatus = ConnectionStatus.CONNECTED,
    val terminalState: TerminalScreenState = TerminalScreenState.from(ScreenBuffer(24, 80)),
    val isDefault: Boolean = false,
)

/**
 * Application Settings state
 */
data class AgyUiSettings(
    val serverUrl: String = "ws://10.0.2.2:7681",
    val workspaceApiUrl: String = "http://10.0.2.2:7682",
    val agentType: AgentType = AgentType.AGY,
    val theme: ThemeOption = ThemeOption.GITHUB_DARK,
    val fontSize: Float = 13f,
    val imeMode: ImeMode = ImeMode.URL,
)

/**
 * Main UI State for the Agy Android Application
 */
data class AgyUiState(
    val workspaces: List<WorkspaceTab> = emptyList(),
    val activeWorkspaceId: String? = null,
    val settings: AgyUiSettings = AgyUiSettings(),
    val isSettingsOpen: Boolean = false,
    val isAddWorkspaceOpen: Boolean = false,
    val isSidebarOpen: Boolean = false,
    val availableWorkspaces: List<String> = emptyList(),
    val isLoadingWorkspaces: Boolean = false,
    val statusMessage: String? = null,
) {
    val activeWorkspace: WorkspaceTab?
        get() = workspaces.find { it.id == activeWorkspaceId } ?: workspaces.firstOrNull()
}

/**
 * Interface contract for the Agy ViewModel.
 * Another agent is implementing the full ViewModel and networking logic in parallel.
 */
interface IAgyViewModel {
    val uiState: StateFlow<AgyUiState>

    fun selectWorkspace(id: String)

    fun openWorkspace(name: String, agentType: AgentType = AgentType.AGY)

    fun closeWorkspace(id: String)

    fun reconnectWorkspace(id: String)

    fun sendInput(workspaceId: String, text: String)

    fun sendArrowKey(workspaceId: String, direction: ArrowDirection, isShift: Boolean = false)

    fun updateServerUrl(url: String)

    fun updateWorkspaceApiUrl(url: String)

    fun updateAgentType(agentType: AgentType)

    fun updateTheme(theme: ThemeOption)

    fun updateFontSize(fontSize: Float)

    fun updateImeMode(mode: ImeMode)

    fun setSettingsOpen(isOpen: Boolean)

    fun setAddWorkspaceOpen(isOpen: Boolean)

    fun setSidebarOpen(isOpen: Boolean)

    fun refreshAvailableWorkspaces()
}

/**
 * Mock ViewModel implementation to satisfy UI previews and compile-time requirements.
 */
class MockAgyViewModel : IAgyViewModel {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val emulatorMap = mutableMapOf<String, TerminalEmulator>()

    private val _uiState = MutableStateFlow(createInitialMockState())
    override val uiState: StateFlow<AgyUiState> = _uiState.asStateFlow()

    private fun createInitialMockState(): AgyUiState {
        val emu1 = TerminalEmulator(24, 80)
        val emu2 = TerminalEmulator(24, 80)
        emulatorMap["ws-root"] = emu1
        emulatorMap["ws-backend"] = emu2

        // Write mock welcome text
        scope.launch {
            emu1.processOutput("\u001B[1;36m=== Antigravity (agy) Android Client ===\u001B[0m\r\n")
            emu1.processOutput("Connected to workspace: \u001B[32mroot\u001B[0m\r\n\r\n")
            emu1.processOutput("Type commands or use the quick toolbar below.\r\n")
            emu1.processOutput("\u001B[33magy@workspace:~$\u001B[0m ")

            emu2.processOutput("\u001B[1;35m=== JetBrains (junie) Agent Session ===\u001B[0m\r\n")
            emu2.processOutput("Connected to workspace: \u001B[32mbackend-service\u001B[0m\r\n\r\n")
            emu2.processOutput("\u001B[33mjunie@backend:~$\u001B[0m ")
        }

        val tab1 = WorkspaceTab(
            id = "ws-root",
            name = "workspace (root)",
            agentType = AgentType.AGY,
            status = ConnectionStatus.CONNECTED,
            terminalState = emu1.screenState.value,
            isDefault = true
        )
        val tab2 = WorkspaceTab(
            id = "ws-backend",
            name = "backend-service",
            agentType = AgentType.JUNIE,
            status = ConnectionStatus.CONNECTED,
            terminalState = emu2.screenState.value,
            isDefault = false
        )

        return AgyUiState(
            workspaces = listOf(tab1, tab2),
            activeWorkspaceId = "ws-root",
            availableWorkspaces = listOf("backend-service", "frontend-app", "mobile-core", "shared-libs")
        )
    }

    override fun selectWorkspace(id: String) {
        _uiState.update { it.copy(activeWorkspaceId = id, isSidebarOpen = false) }
    }

    override fun openWorkspace(name: String, agentType: AgentType) {
        val newId = "ws-" + System.currentTimeMillis()
        val emu = TerminalEmulator(24, 80)
        emulatorMap[newId] = emu

        scope.launch {
            emu.processOutput("\u001B[1;36m[Workspace: $name]\u001B[0m Agent: ${agentType.displayName}\r\n")
            emu.processOutput("Connected.\r\n\r\n$ ")
            updateTabTerminalState(newId, emu.screenState.value)
        }

        val newTab = WorkspaceTab(
            id = newId,
            name = name,
            agentType = agentType,
            status = ConnectionStatus.CONNECTED,
            terminalState = emu.screenState.value
        )

        _uiState.update { state ->
            state.copy(
                workspaces = state.workspaces + newTab,
                activeWorkspaceId = newId,
                isAddWorkspaceOpen = false
            )
        }
    }

    override fun closeWorkspace(id: String) {
        _uiState.update { state ->
            val updated = state.workspaces.filterNot { it.id == id }
            val newActive = if (state.activeWorkspaceId == id) {
                updated.firstOrNull()?.id
            } else {
                state.activeWorkspaceId
            }
            state.copy(workspaces = updated, activeWorkspaceId = newActive)
        }
        emulatorMap.remove(id)
    }

    override fun reconnectWorkspace(id: String) {
        _uiState.update { state ->
            state.copy(
                workspaces = state.workspaces.map { tab ->
                    if (tab.id == id) tab.copy(status = ConnectionStatus.CONNECTING) else tab
                }
            )
        }
    }

    override fun sendInput(workspaceId: String, text: String) {
        val emu = emulatorMap[workspaceId] ?: return
        scope.launch {
            for (char in text) {
                when (char) {
                    '\r', '\n' -> emu.processOutput("\r\n$ ")
                    '\u007f', '\b' -> emu.processOutput("\b \b")
                    else -> emu.processOutput(char.toString())
                }
            }
            updateTabTerminalState(workspaceId, emu.screenState.value)
        }
    }

    override fun sendArrowKey(workspaceId: String, direction: ArrowDirection, isShift: Boolean) {
        val emu = emulatorMap[workspaceId] ?: return
        val seq = when (direction) {
            ArrowDirection.UP -> "\u001B[A"
            ArrowDirection.DOWN -> "\u001B[B"
            ArrowDirection.RIGHT -> "\u001B[C"
            ArrowDirection.LEFT -> "\u001B[D"
        }
        scope.launch {
            emu.processOutput(seq)
            updateTabTerminalState(workspaceId, emu.screenState.value)
        }
    }

    override fun updateServerUrl(url: String) {
        _uiState.update { it.copy(settings = it.settings.copy(serverUrl = url)) }
    }

    override fun updateWorkspaceApiUrl(url: String) {
        _uiState.update { it.copy(settings = it.settings.copy(workspaceApiUrl = url)) }
    }

    override fun updateAgentType(agentType: AgentType) {
        _uiState.update { it.copy(settings = it.settings.copy(agentType = agentType)) }
    }

    override fun updateTheme(theme: ThemeOption) {
        _uiState.update { it.copy(settings = it.settings.copy(theme = theme)) }
    }

    override fun updateFontSize(fontSize: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(fontSize = fontSize.coerceIn(8f, 24f))) }
    }

    override fun updateImeMode(mode: ImeMode) {
        _uiState.update { it.copy(settings = it.settings.copy(imeMode = mode)) }
    }

    override fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    override fun setAddWorkspaceOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isAddWorkspaceOpen = isOpen) }
    }

    override fun setSidebarOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSidebarOpen = isOpen) }
    }

    override fun refreshAvailableWorkspaces() {
        _uiState.update { it.copy(isLoadingWorkspaces = true) }
        scope.launch {
            _uiState.update {
                it.copy(
                    availableWorkspaces = listOf(
                        "backend-service",
                        "frontend-app",
                        "mobile-core",
                        "shared-libs",
                        "docs"
                    ),
                    isLoadingWorkspaces = false
                )
            }
        }
    }

    private fun updateTabTerminalState(workspaceId: String, screenState: TerminalScreenState) {
        _uiState.update { state ->
            state.copy(
                workspaces = state.workspaces.map { tab ->
                    if (tab.id == workspaceId) tab.copy(terminalState = screenState) else tab
                }
            )
        }
    }
}
