package com.antigravity.agy.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.antigravity.agy.android.ui.AgentType
import com.antigravity.agy.android.ui.AgyUiSettings
import com.antigravity.agy.android.ui.ImeMode
import com.antigravity.agy.android.ui.ThemeOption
import com.antigravity.agy.android.ui.theme.AccentGreen
import com.antigravity.agy.android.ui.theme.BackgroundDark
import com.antigravity.agy.android.ui.theme.BorderDark
import com.antigravity.agy.android.ui.theme.SurfaceDark
import com.antigravity.agy.android.ui.theme.SurfaceVariantDark
import com.antigravity.agy.android.ui.theme.TextPrimaryDark
import com.antigravity.agy.android.ui.theme.TextSecondaryDark

/**
 * Settings Modal Dialog
 */
@Composable
fun SettingsModal(
    isOpen: Boolean,
    settings: AgyUiSettings,
    onClose: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onAgentTypeChange: (AgentType) -> Unit,
    onThemeChange: (ThemeOption) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onImeModeChange: (ImeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    var serverUrlInput by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    var agentDropdownExpanded by remember { mutableStateOf(false) }
    var imeDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
            color = SurfaceDark,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Settings",
                        color = TextPrimaryDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Settings",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Server WebSocket URL
                Text(
                    text = "Server WebSocket URL",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = serverUrlInput,
                    onValueChange = {
                        serverUrlInput = it
                        onServerUrlChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedBorderColor = BorderDark,
                        unfocusedBorderColor = BorderDark,
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Theme Selection Row
                SettingsDropdownRow(
                    label = "Theme",
                    selectedText = settings.theme.displayName,
                    isExpanded = themeDropdownExpanded,
                    onToggleExpand = { themeDropdownExpanded = !themeDropdownExpanded },
                    onDismiss = { themeDropdownExpanded = false }
                ) {
                    ThemeOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName, color = TextPrimaryDark, fontSize = 13.sp) },
                            onClick = {
                                onThemeChange(option)
                                themeDropdownExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Workspace Agent Selection Row
                SettingsDropdownRow(
                    label = "Workspace Agent",
                    selectedText = settings.agentType.displayName,
                    isExpanded = agentDropdownExpanded,
                    onToggleExpand = { agentDropdownExpanded = !agentDropdownExpanded },
                    onDismiss = { agentDropdownExpanded = false }
                ) {
                    AgentType.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName, color = TextPrimaryDark, fontSize = 13.sp) },
                            onClick = {
                                onAgentTypeChange(option)
                                agentDropdownExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mobile Keyboard IME Mode Row
                SettingsDropdownRow(
                    label = "Keyboard IME",
                    selectedText = settings.imeMode.displayName.substringBefore(" ("),
                    isExpanded = imeDropdownExpanded,
                    onToggleExpand = { imeDropdownExpanded = !imeDropdownExpanded },
                    onDismiss = { imeDropdownExpanded = false }
                ) {
                    ImeMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName, color = TextPrimaryDark, fontSize = 13.sp) },
                            onClick = {
                                onImeModeChange(option)
                                imeDropdownExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Font Size Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Font Size",
                        color = TextPrimaryDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { onFontSizeChange(settings.fontSize - 1f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Font Size",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Text(
                            text = "${settings.fontSize.toInt()}",
                            color = TextPrimaryDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onFontSizeChange(13f) }
                                .padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { onFontSizeChange(settings.fontSize + 1f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Font Size",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Close Button
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdownRow(
    label: String,
    selectedText: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    dropdownContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextPrimaryDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BackgroundDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedText,
                    color = TextPrimaryDark,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = onDismiss,
                modifier = Modifier.background(SurfaceVariantDark)
            ) {
                dropdownContent()
            }
        }
    }
}
