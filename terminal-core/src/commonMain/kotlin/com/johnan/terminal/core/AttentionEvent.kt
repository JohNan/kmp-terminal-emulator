package com.johnan.terminal.core

import kotlin.time.Clock

/**
 * Represents attention events emitted by the terminal emulator, such as bells and notifications.
 */
sealed interface AttentionEvent {
    /**
     * Visual or audible bell triggered by ASCII BEL (`\u0007`).
     */
    data class Bell(
        val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AttentionEvent

    /**
     * Desktop / user notification triggered by OSC sequences (e.g. OSC 9, OSC 777).
     */
    data class Notification(
        val title: String,
        val message: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AttentionEvent
}
