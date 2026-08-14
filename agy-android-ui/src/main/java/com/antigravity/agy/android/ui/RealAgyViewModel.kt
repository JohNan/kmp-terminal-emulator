package com.antigravity.agy.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.agy.android.network.ConnectionState
import com.antigravity.agy.android.state.SettingsRepository
import com.antigravity.agy.android.state.TerminalViewModel
import com.sshclient.presentation.screens.terminal.ArrowDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class RealAgyViewModel(
    private val terminalViewModel: TerminalViewModel,
    private val settingsRepository: SettingsRepository
) : ViewModel(), IAgyViewModel {
    private val _uiState = MutableStateFlow(
        AgyUiState(
            isSettingsOpen = false,
            isAddWorkspaceOpen = false,
            isSidebarOpen = false,
            availableWorkspaces = emptyList(),
            isLoadingWorkspaces = false
        )
    )

    override val uiState: StateFlow<AgyUiState> = combine(
        _uiState,
        terminalViewModel.workspaces,
        terminalViewModel.activeWorkspaceId,
        terminalViewModel.settings
    ) { control, workspacesMap, activeId, termSettings ->
        val tabs = workspacesMap.values.map { session ->
            val connStatus = when (session.connectionState.value) {
                is ConnectionState.Connected -> ConnectionStatus.CONNECTED
                is ConnectionState.Connecting -> ConnectionStatus.CONNECTING
                is ConnectionState.Disconnected -> ConnectionStatus.DISCONNECTED
                is ConnectionState.Error -> ConnectionStatus.FAILED
            }
            WorkspaceTab(
                id = session.workspaceId,
                name = session.title.value ?: session.workspaceId,
                agentType = AgentType.AGY,
                status = connStatus,
                terminalState = session.screenState.value,
                isDefault = session.workspaceId == "default"
            )
        }

        control.copy(
            workspaces = tabs,
            activeWorkspaceId = activeId,
            settings = control.settings.copy(
                serverUrl = termSettings.proxyHostUrl,
                fontSize = termSettings.fontSize,
                theme = ThemeOption.fromId(termSettings.terminalTheme)
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgyUiState())

    override fun selectWorkspace(id: String) {
        terminalViewModel.switchWorkspace(id)
        _uiState.update { it.copy(isSidebarOpen = false) }
    }

    override fun openWorkspace(name: String, agentType: AgentType) {
        terminalViewModel.openWorkspace(name)
        _uiState.update { it.copy(isAddWorkspaceOpen = false) }
    }

    override fun closeWorkspace(id: String) {
        terminalViewModel.closeWorkspace(id)
    }

    override fun reconnectWorkspace(id: String) {
        terminalViewModel.reconnect(id)
    }

    override fun sendInput(workspaceId: String, text: String) {
        terminalViewModel.sendInput(text, workspaceId)
    }

    override fun sendArrowKey(workspaceId: String, direction: ArrowDirection, isShift: Boolean) {
        terminalViewModel.onArrowKey(direction, ctrl = isShift, workspaceId = workspaceId)
    }

    override fun updateServerUrl(url: String) {
        terminalViewModel.updateProxyHostUrl(url)
    }

    override fun updateWorkspaceApiUrl(url: String) {
        // Not implemented in TerminalViewModel
    }

    override fun updateAgentType(agentType: AgentType) {
        // Not implemented in Settings yet
    }

    override fun updateTheme(theme: ThemeOption) {
        terminalViewModel.updateTerminalTheme(theme.id)
    }

    override fun updateFontSize(fontSize: Float) {
        terminalViewModel.updateFontSize(fontSize)
    }

    override fun updateImeMode(mode: ImeMode) {
        // Not implemented
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
        // Not implemented real fetch yet
    }
}
