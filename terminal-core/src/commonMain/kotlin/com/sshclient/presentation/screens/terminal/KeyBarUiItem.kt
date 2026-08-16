package com.sshclient.presentation.screens.terminal

enum class KeyBehavior {
    ONE_SHOT,
    MODIFIER, // Sticky (toggle)
    REPEATABLE,
}

data class KeyBarUiItem(
    val id: String,
    val label: String,
    val iconName: String? = null,
    val behavior: KeyBehavior = KeyBehavior.ONE_SHOT,
    val content: String = "",
    val hasPrimaryAction: Boolean = true,
    val children: List<KeyBarUiItem>? = null,
    // Keep generic reference to original for identification if needed
    val originalItem: Any? = null,
)
