package com.sshclient.composeapp.presentation.screens.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
                    if (item.id == "spacer" || item.label == "Spacer") {
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
                        modifier = Modifier.fillMaxHeight()
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
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        for (i in 0 until rightItems.size step 2) {
                            val item1 = rightItems[i]
                            val item2 = if (i + 1 < rightItems.size) rightItems[i + 1] else null
                            KeyColumn(item1, item2, modifierState, onItemClick, hapticFeedbackEnabled, onLog)
                        }
                    }
                }
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
        val icon1 = resolveIcon(item1.iconName)
        val isPressed1 =
            when (item1.id) {
                "CTRL" -> modifierState.ctrlPressed
                "ALT" -> modifierState.altPressed
                "SHIFT" -> modifierState.shiftPressed
                else -> false
            }

        val a11yDescription1 = when (item1.label) {
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
            else -> item1.label
        }

        TerminalKey(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            label = item1.label,
            icon = icon1,
            contentDescription = a11yDescription1,
            isToggle = item1.behavior == KeyBehavior.MODIFIER,
            isPressed = isPressed1,
            repeatable = item1.behavior == KeyBehavior.REPEATABLE,
            onClick = { onItemClick(item1) },
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            onLog = onLog,
        )

        if (item2 != null) {
            val icon2 = resolveIcon(item2.iconName)
            val isPressed2 =
                when (item2.id) {
                    "CTRL" -> modifierState.ctrlPressed
                    "ALT" -> modifierState.altPressed
                    "SHIFT" -> modifierState.shiftPressed
                    else -> false
                }

            val a11yDescription2 = when (item2.label) {
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
                else -> item2.label
            }

            TerminalKey(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                label = item2.label,
                icon = icon2,
                contentDescription = a11yDescription2,
                isToggle = item2.behavior == KeyBehavior.MODIFIER,
                isPressed = isPressed2,
                repeatable = item2.behavior == KeyBehavior.REPEATABLE,
                onClick = { onItemClick(item2) },
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                onLog = onLog,
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
    onClick: () -> Unit,
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

    Button(
        onClick =
            if (repeatable) {
                {}
            } else {
                hapticOnClick
            },
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
        interactionSource = interactionSource,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (isToggle && isPressed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (isToggle && isPressed) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
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

private fun previewKeyBarItems(): List<KeyBarUiItem> {
    return listOf(
        KeyBarUiItem(id = "ctrl", label = "Ctrl", behavior = KeyBehavior.MODIFIER),
        KeyBarUiItem(id = "alt", label = "Alt", behavior = KeyBehavior.MODIFIER),
        KeyBarUiItem(id = "esc", label = "Esc", behavior = KeyBehavior.ONE_SHOT),
        KeyBarUiItem(id = "tab", label = "Tab", behavior = KeyBehavior.ONE_SHOT),
        KeyBarUiItem(id = "up", label = "Up", behavior = KeyBehavior.REPEATABLE),
        KeyBarUiItem(id = "down", label = "Down", behavior = KeyBehavior.REPEATABLE),
        KeyBarUiItem(id = "left", label = "Left", behavior = KeyBehavior.REPEATABLE),
        KeyBarUiItem(id = "right", label = "Right", behavior = KeyBehavior.REPEATABLE)
    )
}
