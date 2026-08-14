package com.antigravity.agy.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.agy.android.ui.AgentType
import com.antigravity.agy.android.ui.ConnectionStatus
import com.antigravity.agy.android.ui.WorkspaceTab
import com.antigravity.agy.android.ui.theme.AccentBlue
import com.antigravity.agy.android.ui.theme.BackgroundDark
import com.antigravity.agy.android.ui.theme.BadgeAgy
import com.antigravity.agy.android.ui.theme.BadgeJunie
import com.antigravity.agy.android.ui.theme.BorderDark
import com.antigravity.agy.android.ui.theme.StatusGreen
import com.antigravity.agy.android.ui.theme.StatusOrange
import com.antigravity.agy.android.ui.theme.StatusRed
import com.antigravity.agy.android.ui.theme.SurfaceDark
import com.antigravity.agy.android.ui.theme.SurfaceVariantDark
import com.antigravity.agy.android.ui.theme.TextSecondaryDark

/**
 * Single-row Top Tab Bar for workspace navigation.
 * Complies with mobile viewport rules: stays strictly on a single row.
 */
@Composable
fun WorkspaceTabBar(
    workspaces: List<WorkspaceTab>,
    activeWorkspaceId: String?,
    onSelectWorkspace: (String) -> Unit,
    onCloseWorkspace: (String) -> Unit,
    onReconnectWorkspace: (String) -> Unit,
    onAddWorkspaceClick: () -> Unit,
    onToggleSidebar: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = SurfaceDark,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sidebar Toggle Button (Hamburger)
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Toggle Sidebar",
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Scrollable Tab List
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                workspaces.forEach { tab ->
                    val isActive = tab.id == activeWorkspaceId
                    WorkspaceTabItem(
                        tab = tab,
                        isActive = isActive,
                        onSelect = { onSelectWorkspace(tab.id) },
                        onClose = { onCloseWorkspace(tab.id) },
                        onReconnect = { onReconnectWorkspace(tab.id) },
                    )
                }

                // Add Workspace '+' button
                IconButton(
                    onClick = onAddWorkspaceClick,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Open Workspace",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Settings gear button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkspaceTabItem(
    tab: WorkspaceTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isActive) BackgroundDark else SurfaceVariantDark
    val textColor = if (isActive) AccentBlue else TextSecondaryDark
    val borderModifier = if (isActive) {
        Modifier.border(
            width = 1.dp,
            color = BorderDark,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = Color.Transparent,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
        )
    }

    Row(
        modifier = modifier
            .height(38.dp)
            .widthIn(min = 120.dp, max = 220.dp)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(backgroundColor)
            .then(borderModifier)
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Connection status indicator dot
        ConnectionStatusDot(status = tab.status)

        Spacer(modifier = Modifier.width(6.dp))

        // Tab Title (Workspace name)
        Text(
            text = tab.name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Agent Badge (AGY / JUNIE)
        AgentBadge(agentType = tab.agentType)

        Spacer(modifier = Modifier.width(4.dp))

        // Reconnect action or Close button
        if (tab.status == ConnectionStatus.FAILED || tab.status == ConnectionStatus.DISCONNECTED) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reconnect",
                tint = StatusOrange,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onReconnect)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Tab",
                tint = TextSecondaryDark,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onClose)
            )
        }
    }
}

@Composable
fun ConnectionStatusDot(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val color = when (status) {
        ConnectionStatus.CONNECTED -> StatusGreen
        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> StatusOrange
        ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED -> StatusRed
    }

    val isConnecting = status == ConnectionStatus.CONNECTING || status == ConnectionStatus.RECONNECTING
    val currentAlpha = if (isConnecting) alpha else 1.0f
    Box(
        modifier = modifier
            .size(7.dp)
            .alpha(currentAlpha)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun AgentBadge(
    agentType: AgentType,
    modifier: Modifier = Modifier,
) {
    val badgeBg = when (agentType) {
        AgentType.AGY -> BadgeAgy
        AgentType.JUNIE -> BadgeJunie
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(badgeBg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = agentType.badgeText,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
