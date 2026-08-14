package com.antigravity.agy.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.agy.android.ui.AgyUiSettings
import com.antigravity.agy.android.ui.WorkspaceTab
import com.antigravity.agy.android.ui.theme.AccentGreen
import com.antigravity.agy.android.ui.theme.BackgroundDark
import com.antigravity.agy.android.ui.theme.BorderDark
import com.antigravity.agy.android.ui.theme.SurfaceDark
import com.antigravity.agy.android.ui.theme.SurfaceVariantDark
import com.antigravity.agy.android.ui.theme.TextPrimaryDark
import com.antigravity.agy.android.ui.theme.TextSecondaryDark
import com.sshclient.composeapp.presentation.screens.terminal.ModifierKeyState
import com.sshclient.composeapp.presentation.screens.terminal.TerminalRenderer
import com.sshclient.presentation.screens.terminal.ArrowDirection

/**
 * Terminal Viewport hosting TerminalRenderer and the Mobile Key Toolbar
 */
@Composable
fun TerminalViewport(
    activeTab: WorkspaceTab?,
    settings: AgyUiSettings,
    onInput: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeTab == null) {
        // Empty state when no workspaces are open
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No workspace open",
                    color = TextSecondaryDark,
                    fontSize = 14.sp
                )
                Text(
                    text = "Tap '+' in the header or sidebar to open a workspace",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    val focusRequester = remember { FocusRequester() }
    var modifierState by remember { mutableStateOf(ModifierKeyState()) }
    var requestKeyboardToggle by remember { mutableStateOf<(() -> Unit)?>(null) }

    val terminalColorScheme = remember(settings.theme) {
        settings.theme.toTerminalColorScheme()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Terminal Renderer Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            TerminalRenderer(
                modifier = Modifier.fillMaxSize(),
                terminalState = activeTab.terminalState,
                onInput = onInput,
                onArrowKey = onArrowKey,
                onLog = {},
                focusRequester = focusRequester,
                colorScheme = terminalColorScheme,
                fontSize = settings.fontSize,
                modifierKeyState = modifierState,
                onModifierStateChange = { modifierState = it },
                onKeyboardToggleAvailable = { toggleFn ->
                    requestKeyboardToggle = toggleFn
                }
            )
        }

        // Mobile Quick Toolbar / KeyBar
        MobileToolbar(
            modifierState = modifierState,
            onModifierChange = { modifierState = it },
            onSendKey = onInput,
            onArrowKey = onArrowKey,
            onToggleKeyboard = {
                requestKeyboardToggle?.invoke() ?: focusRequester.requestFocus()
            },
            onOpenSettings = onOpenSettings,
        )
    }
}

/**
 * Mobile Toolbar providing quick ESC, TAB, Ctrl, Alt, Arrow, and Nav keys.
 */
@Composable
private fun MobileToolbar(
    modifierState: ModifierKeyState,
    onModifierChange: (ModifierKeyState) -> Unit,
    onSendKey: (String) -> Unit,
    onArrowKey: (ArrowDirection, Boolean) -> Unit,
    onToggleKeyboard: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceDark,
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: ESC, Alt, Home, Up, End, PgUp, Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ToolKeyButton(
                    label = "ESC",
                    modifier = Modifier.weight(1f),
                    onClick = { onSendKey("\u001B") }
                )
                ToolKeyButton(
                    label = "Alt",
                    modifier = Modifier.weight(1f),
                    isToggle = true,
                    isPressed = modifierState.altPressed,
                    onClick = { onModifierChange(modifierState.copy(altPressed = !modifierState.altPressed)) }
                )
                ToolKeyButton(
                    label = "Home",
                    modifier = Modifier.weight(1f),
                    onClick = { onSendKey("\u001B[H") }
                )
                ToolKeyButton(
                    label = "▲",
                    modifier = Modifier.weight(1f),
                    onClick = { onArrowKey(ArrowDirection.UP, false) }
                )
                ToolKeyButton(
                    label = "End",
                    modifier = Modifier.weight(1f),
                    onClick = { onSendKey("\u001B[F") }
                )
                ToolKeyButton(
                    label = "PgUp",
                    modifier = Modifier.weight(1f),
                    onClick = { onSendKey("\u001B[5~") }
                )
                ToolKeyButton(
                    label = "⚙",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSettings
                )
            }

            // Row 2: TAB, Ctrl, Left, Down, Right, PgDn, Keyboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ToolKeyButton(
                    label = "TAB",
                    modifier = Modifier.weight(1f),
                    onClick = { onSendKey("\t") }
                )
                ToolKeyButton(
                    label = "Ctrl",
                    modifier = Modifier.weight(1f),
                    isToggle = true,
                    isPressed = modifierState.ctrlPressed,
                    onClick = { onModifierChange(modifierState.copy(ctrlPressed = !modifierState.ctrlPressed)) }
                )
                ToolKeyButton(
                    label = "◀",
                    modifier = Modifier.weight(1f),
                    onClick = { onArrowKey(ArrowDirection.LEFT, false) }
                )
                ToolKeyButton(
                    label = "▼",
                    modifier = Modifier.weight(1f),
                    onClick = { onArrowKey(ArrowDirection.DOWN, false) }
                )
                ToolKeyButton(
                    label = "▶",
                    modifier = Modifier.weight(1f),
                    onClick = { onArrowKey(ArrowDirection.RIGHT, false) }
                )
                ToolKeyButton(
                    label = "PgDn",
                    modifier = Modifier.weight(1f),
                    onClick = { onSendKey("\u001B[6~") }
                )
                ToolKeyButton(
                    label = "⌨",
                    modifier = Modifier.weight(1f),
                    isPrimary = true,
                    onClick = onToggleKeyboard
                )
            }
        }
    }
}

@Composable
private fun ToolKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    isToggle: Boolean = false,
    isPressed: Boolean = false,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        isToggle && isPressed -> AccentGreen
        isPrimary -> AccentGreen
        else -> SurfaceVariantDark
    }

    val contentColor = when {
        isToggle && isPressed -> Color.White
        isPrimary -> Color.White
        else -> TextPrimaryDark
    }

    val borderColor = when {
        isToggle && isPressed -> AccentGreen
        isPrimary -> AccentGreen
        else -> BorderDark
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
