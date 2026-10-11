package com.johnan.terminal.demomobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.ArrowDirection
import com.johnan.terminal.core.KeyBarUiItem
import com.johnan.terminal.core.KeyBehavior
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.terminalConfig
import com.johnan.terminal.ui.ModifierKeyState
import com.johnan.terminal.ui.TerminalCursorStyle
import com.johnan.terminal.ui.TerminalKeyBar
import com.johnan.terminal.ui.TerminalRenderer
import com.johnan.terminal.ui.terminalUiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTerminalScreen(modifier: Modifier = Modifier) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val emulator = remember {
            TerminalEmulator(
                config = terminalConfig {
                    initialRows = 24
                    initialCols = 80
                    maxScrollback = 1000
                },
            )
        }
        val scope = rememberCoroutineScope()
        val replEngine = remember { MobileReplEngine(emulator, scope) }
        val focusRequester = remember { FocusRequester() }

        var selectedColorScheme by remember { mutableStateOf(TerminalColorScheme.DEFAULT) }
        var selectedCursorStyle by remember { mutableStateOf(TerminalCursorStyle.BLOCK) }
        var fontSizeSp by remember { mutableFloatStateOf(13f) }
        var showMenu by remember { mutableStateOf(false) }
        var showKeyboardSignal by remember { mutableIntStateOf(0) }
        var modifierState by remember { mutableStateOf(ModifierKeyState()) }

        val terminalState by emulator.screenState.collectAsState()

        LaunchedEffect(Unit) {
            replEngine.start()
        }

        val uiConfig = remember(selectedColorScheme, selectedCursorStyle, fontSizeSp) {
            terminalUiConfig {
                typography {
                    fontSize = fontSizeSp.sp
                }
                cursor {
                    style = selectedCursorStyle
                    blink = true
                }
                colorScheme = selectedColorScheme
                gestures {
                    enableTouchToFocus = true
                    enableSelection = true
                    touchScrollSendsWheelOnly = true
                    wheelScrollMultiplier = 1.2f
                }
            }
        }

        val defaultKeyBarItems = remember {
            listOf(
                KeyBarUiItem(id = "ctrl", label = "Ctrl", behavior = KeyBehavior.MODIFIER),
                KeyBarUiItem(id = "alt", label = "Alt", behavior = KeyBehavior.MODIFIER),
                KeyBarUiItem(id = "esc", label = "Esc", behavior = KeyBehavior.ONE_SHOT, content = "\u001B"),
                KeyBarUiItem(id = "tab", label = "Tab", behavior = KeyBehavior.ONE_SHOT, content = "\t"),
                KeyBarUiItem(id = "up", label = "↑", iconName = "ArrowUpward", behavior = KeyBehavior.REPEATABLE),
                KeyBarUiItem(id = "down", label = "↓", iconName = "ArrowDownward", behavior = KeyBehavior.REPEATABLE),
                KeyBarUiItem(id = "left", label = "←", iconName = "ArrowBack", behavior = KeyBehavior.REPEATABLE),
                KeyBarUiItem(id = "right", label = "→", iconName = "ArrowForward", behavior = KeyBehavior.REPEATABLE),
            )
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("KMP Terminal", fontSize = 18.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    actions = {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Theme: Default Dark") },
                                onClick = {
                                    selectedColorScheme = TerminalColorScheme.DEFAULT
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Theme: Monokai") },
                                onClick = {
                                    selectedColorScheme = TerminalColorScheme.MONOKAI
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Theme: Nord") },
                                onClick = {
                                    selectedColorScheme = TerminalColorScheme.NORD
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Theme: Solarized Dark") },
                                onClick = {
                                    selectedColorScheme = TerminalColorScheme.SOLARIZED_DARK
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Cursor: Block") },
                                onClick = {
                                    selectedCursorStyle = TerminalCursorStyle.BLOCK
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Cursor: Beam") },
                                onClick = {
                                    selectedCursorStyle = TerminalCursorStyle.BEAM
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Cursor: Underline") },
                                onClick = {
                                    selectedCursorStyle = TerminalCursorStyle.UNDERLINE
                                    showMenu = false
                                },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                TerminalKeyBar(
                    items = defaultKeyBarItems,
                    modifierState = modifierState,
                    onItemClick = { item ->
                        when (item.id) {
                            "ctrl" -> modifierState = modifierState.copy(ctrlPressed = !modifierState.ctrlPressed)
                            "alt" -> modifierState = modifierState.copy(altPressed = !modifierState.altPressed)
                            "shift" -> modifierState = modifierState.copy(shiftPressed = !modifierState.shiftPressed)
                            "esc" -> replEngine.handleInput("\u001B")
                            "tab" -> replEngine.handleInput("\t")
                            "up" -> replEngine.handleArrowKey(isUp = true)
                            "down" -> replEngine.handleArrowKey(isUp = false)
                            "left" -> replEngine.handleInput("\u001B[D")
                            "right" -> replEngine.handleInput("\u001B[C")
                            else -> if (item.content.isNotEmpty()) replEngine.handleInput(item.content)
                        }
                    },
                    onShowKeyboard = {
                        showKeyboardSignal++
                        focusRequester.requestFocus()
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Quick preset actions chip row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { replEngine.runColorsDemo() },
                        label = { Text("Colors") },
                    )
                    AssistChip(
                        onClick = { replEngine.runStylesDemo() },
                        label = { Text("Styles") },
                    )
                    AssistChip(
                        onClick = { replEngine.runUnicodeDemo() },
                        label = { Text("Unicode") },
                    )
                    AssistChip(
                        onClick = { replEngine.runMatrixDemo() },
                        label = { Text("Matrix") },
                    )
                    AssistChip(
                        onClick = { replEngine.runHtopDemo() },
                        label = { Text("Htop") },
                    )
                    AssistChip(
                        onClick = { replEngine.runProgressDemo() },
                        label = { Text("Progress") },
                    )
                    AssistChip(
                        onClick = { replEngine.stopActiveAnimation() },
                        label = { Text("Stop") },
                    )
                }

                // Terminal viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    TerminalRenderer(
                        terminalState = terminalState,
                        config = uiConfig,
                        onInput = { input -> replEngine.handleInput(input) },
                        onArrowKey = { arrow, _ ->
                            replEngine.handleArrowKey(isUp = (arrow == ArrowDirection.UP))
                        },
                        onLog = { /* log */ },
                        modifierKeyState = modifierState,
                        onModifierStateChange = { modifierState = it },
                        showKeyboardSignal = showKeyboardSignal,
                        focusRequester = focusRequester,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
