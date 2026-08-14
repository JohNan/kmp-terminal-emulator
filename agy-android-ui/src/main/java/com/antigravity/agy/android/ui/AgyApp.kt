package com.antigravity.agy.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.antigravity.agy.android.ui.components.AddWorkspaceModal
import com.antigravity.agy.android.ui.components.SettingsModal
import com.antigravity.agy.android.ui.components.TerminalViewport
import com.antigravity.agy.android.ui.components.WorkspaceSidebar
import com.antigravity.agy.android.ui.components.WorkspaceTabBar
import com.antigravity.agy.android.ui.theme.AgyTheme
import com.antigravity.agy.android.ui.theme.BackgroundDark

/**
 * Main Composable Application Container for Antigravity (agy) Android UI.
 */
@Composable
fun AgyApp(
    viewModel: IAgyViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            WorkspaceTabBar(
                workspaces = uiState.workspaces,
                activeWorkspaceId = uiState.activeWorkspaceId,
                onSelectWorkspace = { viewModel.selectWorkspace(it) },
                onCloseWorkspace = { viewModel.closeWorkspace(it) },
                onReconnectWorkspace = { viewModel.reconnectWorkspace(it) },
                onAddWorkspaceClick = { viewModel.setAddWorkspaceOpen(true) },
                onToggleSidebar = { viewModel.setSidebarOpen(!uiState.isSidebarOpen) },
                onOpenSettings = { viewModel.setSettingsOpen(true) },
            )
        },
        containerColor = BackgroundDark,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Terminal Area
            TerminalViewport(
                activeTab = uiState.activeWorkspace,
                settings = uiState.settings,
                onInput = { text ->
                    uiState.activeWorkspace?.id?.let { id ->
                        viewModel.sendInput(id, text)
                    }
                },
                onArrowKey = { direction, isShift ->
                    uiState.activeWorkspace?.id?.let { id ->
                        viewModel.sendArrowKey(id, direction, isShift)
                    }
                },
                onOpenSettings = { viewModel.setSettingsOpen(true) },
                modifier = Modifier.fillMaxSize(),
            )

            // Sidebar Dimming Scrim
            if (uiState.isSidebarOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.setSidebarOpen(false) }
                        )
                )
            }

            // Slide-in Workspace Sidebar
            AnimatedVisibility(
                visible = uiState.isSidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
            ) {
                WorkspaceSidebar(
                    isOpen = true,
                    openTabs = uiState.workspaces,
                    activeWorkspaceId = uiState.activeWorkspaceId,
                    availableWorkspaces = uiState.availableWorkspaces,
                    isLoadingAvailable = uiState.isLoadingWorkspaces,
                    onSelectWorkspace = { viewModel.selectWorkspace(it) },
                    onOpenWorkspace = { name, agent -> viewModel.openWorkspace(name, agent) },
                    onCloseSidebar = { viewModel.setSidebarOpen(false) },
                    onRefreshAvailable = { viewModel.refreshAvailableWorkspaces() },
                    onAddWorkspaceClick = {
                        viewModel.setSidebarOpen(false)
                        viewModel.setAddWorkspaceOpen(true)
                    },
                )
            }
        }
    }

    // Settings Modal
    SettingsModal(
        isOpen = uiState.isSettingsOpen,
        settings = uiState.settings,
        onClose = { viewModel.setSettingsOpen(false) },
        onServerUrlChange = { viewModel.updateServerUrl(it) },
        onAgentTypeChange = { viewModel.updateAgentType(it) },
        onThemeChange = { viewModel.updateTheme(it) },
        onFontSizeChange = { viewModel.updateFontSize(it) },
        onImeModeChange = { viewModel.updateImeMode(it) },
    )

    // Add Workspace Modal
    AddWorkspaceModal(
        isOpen = uiState.isAddWorkspaceOpen,
        availableWorkspaces = uiState.availableWorkspaces,
        onClose = { viewModel.setAddWorkspaceOpen(false) },
        onOpenWorkspace = { name, agent -> viewModel.openWorkspace(name, agent) },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AgyAppPreview() {
    AgyTheme {
        AgyApp(viewModel = MockAgyViewModel())
    }
}
