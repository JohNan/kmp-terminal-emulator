package com.johnan.terminal.core

enum class KeyBehavior {
    ONE_SHOT,
    MODIFIER,
    REPEATABLE,
}

/**
 * Visual key item descriptor displayed in the terminal virtual key bar.
 */
data class KeyBarUiItem(
    val id: String,
    val label: String,
    val iconName: String? = null,
    val behavior: KeyBehavior = KeyBehavior.ONE_SHOT,
    val content: String = "",
    val hasPrimaryAction: Boolean = true,
    val children: List<KeyBarUiItem>? = null,
    val originalItem: Any? = null,
)
