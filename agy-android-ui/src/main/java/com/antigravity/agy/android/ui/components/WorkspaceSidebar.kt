package com.antigravity.agy.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.agy.android.ui.AgentType
import com.antigravity.agy.android.ui.WorkspaceTab
import com.antigravity.agy.android.ui.theme.AccentBlue
import com.antigravity.agy.android.ui.theme.BorderDark
import com.antigravity.agy.android.ui.theme.SurfaceDark
import com.antigravity.agy.android.ui.theme.SurfaceVariantDark
import com.antigravity.agy.android.ui.theme.TextPrimaryDark
import com.antigravity.agy.android.ui.theme.TextSecondaryDark

/**
 * Slide-out Sidebar for managing workspaces
 */
@Composable
fun WorkspaceSidebar(
    isOpen: Boolean,
    openTabs: List<WorkspaceTab>,
    activeWorkspaceId: String?,
    availableWorkspaces: List<String>,
    isLoadingAvailable: Boolean,
    onSelectWorkspace: (String) -> Unit,
    onOpenWorkspace: (String, AgentType) -> Unit,
    onCloseSidebar: () -> Unit,
    onRefreshAvailable: () -> Unit,
    onAddWorkspaceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    Surface(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight(),
        color = SurfaceDark,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "WORKSPACES",
                    color = TextPrimaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshAvailable,
                        modifier = Modifier.size(28.dp)
                    ) {
                        if (isLoadingAvailable) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AccentBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Workspaces",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onCloseSidebar,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sidebar",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = BorderDark,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Section 1: Active Open Tabs
                item {
                    Text(
                        text = "OPEN SESSIONS",
                        color = TextSecondaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                items(openTabs) { tab ->
                    val isActive = tab.id == activeWorkspaceId
                    val bg = if (isActive) SurfaceVariantDark else Color.Transparent
                    val textColor = if (isActive) AccentBlue else TextPrimaryDark

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .clickable { onSelectWorkspace(tab.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ConnectionStatusDot(status = tab.status)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = tab.name,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        AgentBadge(agentType = tab.agentType)
                    }
                }

                // Section 2: Discovered server workspaces
                if (availableWorkspaces.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AVAILABLE DIRECTORIES",
                            color = TextSecondaryDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    items(availableWorkspaces) { dir ->
                        val isOpen = openTabs.any { it.name == dir }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val existing = openTabs.find { it.name == dir }
                                    if (existing != null) {
                                        onSelectWorkspace(existing.id)
                                    } else {
                                        onOpenWorkspace(dir, AgentType.AGY)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = dir,
                                    color = if (isOpen) AccentBlue else TextSecondaryDark,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!isOpen) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Open",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = BorderDark,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Bottom Add Workspace Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .clickable(onClick = onAddWorkspaceClick)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New Workspace",
                        color = AccentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
