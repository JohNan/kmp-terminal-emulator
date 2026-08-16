package com.sshclient.composeapp.presentation.screens.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.sshclient.composeapp.presentation.components.repeatingClickable
import com.sshclient.presentation.screens.terminal.KeyBarUiItem
import com.sshclient.presentation.screens.terminal.KeyBehavior

/**
 * Modifier key state data class
 * Tracks which modifier keys are currently active
 */
data class ModifierKeyState(
    val ctrlPressed: Boolean = false,
    val altPressed: Boolean = false,
    val shiftPressed: Boolean = false,
)

/**
 * Customizable Terminal Key Bar
 */
@Composable
fun TerminalKeyBar(
    items: List<KeyBarUiItem>,
    modifierState: ModifierKeyState,
    onItemClick: (KeyBarUiItem) -> Unit,
    onShowKeyboard: () -> Unit,
    onConfigureKeyBar: () -> Unit,
    modifier: Modifier = Modifier,
    hapticFeedbackEnabled: Boolean = true,
    onLog: ((String) -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(2.dp)
                    .height(84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Split items into left and right groups without allocating an Iterator
            val (leftItems, rightItems) = remember(items) {
                val left = ArrayList<KeyBarUiItem>(items.size)
                val right = ArrayList<KeyBarUiItem>(items.size)
                var foundSpacer = false
                for (i in 0 until items.size) {
                    val item = items[i]
                    val isSpacer = item.id.equals("spacer", ignoreCase = true) ||
                        item.originalItem?.toString()?.contains("Spacer") == true
                    if (isSpacer) {
                        foundSpacer = true
                    } else {
                        if (foundSpacer) {
                            right.add(item)
                        } else {
                            left.add(item)
                        }
                    }
                }
                left to right
            }

            KeyBarLayout(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                leftContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        for (i in 0 until leftItems.size step 2) {
                            val item1 = leftItems[i]
                            val item2 = if (i + 1 < leftItems.size) leftItems[i + 1] else null
                            KeyColumn(item1, item2, modifierState, onItemClick, hapticFeedbackEnabled, onLog)
                        }
                    }
                },
                rightContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        for (i in 0 until rightItems.size step 2) {
                            val item1 = rightItems[i]
                            val item2 = if (i + 1 < rightItems.size) rightItems[i + 1] else null
                            KeyColumn(item1, item2, modifierState, onItemClick, hapticFeedbackEnabled, onLog)
                        }
                    }
                },
            )

            // Fixed Right: Configuration & Keyboard Toggle
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(start = 2.dp)
                    .width(IntrinsicSize.Max),
            ) {
                TerminalKey(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Settings,
                    contentDescription = "Configure Key Bar",
                    onClick = onConfigureKeyBar,
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                )
                TerminalKey(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Keyboard,
                    contentDescription = "Toggle Keyboard",
                    onClick = onShowKeyboard,
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                )
            }
        }
    }
}

internal fun resolveIcon(name: String?): ImageVector? {
    return when (name) {
        "ArrowUpward" -> Icons.Default.KeyboardArrowUp // Consistent with Left/Right
        "ArrowDownward" -> Icons.Default.KeyboardArrowDown
        "ArrowBack" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft // Mapped from LEFT
        "ArrowForward" -> Icons.AutoMirrored.Filled.KeyboardArrowRight // Mapped from RIGHT
        "Keyboard" -> Icons.Default.Keyboard
        "Search" -> Icons.Default.Search
        "Home" -> Icons.Default.Home
        "ContentCopy" -> Icons.Default.ContentCopy
        "ContentPaste" -> Icons.Default.ContentPaste
        "Clear" -> Icons.Default.Clear
        "History" -> Icons.Default.History
        "Menu" -> Icons.Default.Menu
        else -> null
    }
}

@Composable
private fun KeyItem(
    item: KeyBarUiItem,
    modifierState: ModifierKeyState,
    onItemClick: (KeyBarUiItem) -> Unit,
    hapticFeedbackEnabled: Boolean,
    onLog: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val icon = resolveIcon(item.iconName)
    val isPressed =
        when (item.id) {
            "CTRL" -> modifierState.ctrlPressed
            "ALT" -> modifierState.altPressed
            "SHIFT" -> modifierState.shiftPressed
            else -> false
        }

    val a11yDescription = when (item.label) {
        "Up" -> "Up Arrow"
        "Down" -> "Down Arrow"
        "Left" -> "Left Arrow"
        "Right" -> "Right Arrow"
        "|" -> "Pipe"
        "/" -> "Slash"
        "-" -> "Dash"
        "~" -> "Tilde"
        "_" -> "Underscore"
        "." -> "Dot"
        else -> item.label
    }

    val children = item.children
    if (!children.isNullOrEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = modifier) {
            TerminalKey(
                modifier = Modifier.fillMaxWidth(),
                label = item.label,
                icon = icon,
                contentDescription = a11yDescription,
                isToggle = item.behavior == KeyBehavior.MODIFIER,
                isPressed = isPressed,
                repeatable = item.behavior == KeyBehavior.REPEATABLE,
                onClick = {
                    if (item.hasPrimaryAction) {
                        onItemClick(item)
                    } else {
                        expanded = true
                    }
                },
                onLongClick = {
                    if (item.hasPrimaryAction) {
                        expanded = true
                    }
                },
                hasSubMenu = true,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                onLog = onLog,
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = false),
            ) {
                children.forEach { child ->
                    DropdownMenuItem(
                        text = { Text(child.label) },
                        leadingIcon = resolveIcon(child.iconName)?.let { { Icon(it, contentDescription = null) } },
                        onClick = {
                            expanded = false
                            onItemClick(child)
                        },
                    )
                }
            }
        }
    } else {
        TerminalKey(
            modifier = modifier,
            label = item.label,
            icon = icon,
            contentDescription = a11yDescription,
            isToggle = item.behavior == KeyBehavior.MODIFIER,
            isPressed = isPressed,
            repeatable = item.behavior == KeyBehavior.REPEATABLE,
            onClick = { onItemClick(item) },
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            onLog = onLog,
        )
    }
}

@Composable
private fun KeyColumn(
    item1: KeyBarUiItem,
    item2: KeyBarUiItem?,
    modifierState: ModifierKeyState,
    onItemClick: (KeyBarUiItem) -> Unit,
    hapticFeedbackEnabled: Boolean,
    onLog: ((String) -> Unit)?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.width(IntrinsicSize.Max),
    ) {
        KeyItem(
            item = item1,
            modifierState = modifierState,
            onItemClick = onItemClick,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            onLog = onLog,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        )

        if (item2 != null) {
            KeyItem(
                item = item2,
                modifierState = modifierState,
                onItemClick = onItemClick,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                onLog = onLog,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            )
        }
    }
}

@Composable
internal fun TerminalKey(
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = label,
    isToggle: Boolean = false,
    isPressed: Boolean = false,
    repeatable: Boolean = false,
    hasSubMenu: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    hapticFeedbackEnabled: Boolean = true,
    onLog: ((String) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val hapticOnClick = {
        if (hapticFeedbackEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onClick()
    }

    val buttonModifier =
        if (repeatable) {
            modifier.repeatingClickable(
                onClick = {
                    onLog?.invoke("[ui] Repeating click for: $contentDescription")
                    hapticOnClick()
                },
            )
        } else {
            modifier
        }

    val containerColor = if (isToggle && isPressed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isToggle && isPressed) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier =
            buttonModifier
                .height(40.dp)
                .defaultMinSize(minWidth = 32.dp)
                .width(45.dp)
                .semantics {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                    if (isToggle) {
                        stateDescription = if (isPressed) "On" else "Off"
                    }
                },
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .then(
                    if (repeatable) {
                        Modifier // handled by repeatingClickable
                    } else {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = hapticOnClick,
                            onLongClick = onLongClick?.let { action ->
                                {
                                    if (hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    action()
                                }
                            },
                        )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hasSubMenu) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(5.dp)
                        .background(
                            color = if (isToggle && isPressed) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape,
                        ),
                )
            }
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null)
            } else if (label != null) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun previewKeyBarItems(): List<KeyBarUiItem> {
    return listOf(
        KeyBarUiItem(id = "ctrl", label = "Ctrl", behavior = KeyBehavior.MODIFIER),
        KeyBarUiItem(id = "alt", label = "Alt", behavior = KeyBehavior.MODIFIER),
        KeyBarUiItem(id = "esc", label = "Esc", behavior = KeyBehavior.ONE_SHOT),
        KeyBarUiItem(id = "tab", label = "Tab", behavior = KeyBehavior.ONE_SHOT),
        KeyBarUiItem(id = "up", label = "Up", behavior = KeyBehavior.REPEATABLE),
        KeyBarUiItem(id = "down", label = "Down", behavior = KeyBehavior.REPEATABLE),
        KeyBarUiItem(id = "left", label = "Left", behavior = KeyBehavior.REPEATABLE),
        KeyBarUiItem(id = "right", label = "Right", behavior = KeyBehavior.REPEATABLE),
    )
}
