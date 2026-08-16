package com.sshclient.data.terminal

/**
 * Security policy for terminal OSC 52 clipboard write operations.
 */
enum class Osc52Policy {
    /**
     * Always prompt user before allowing the remote terminal to copy text to local clipboard.
     */
    ASK,

    /**
     * Automatically allow all OSC 52 clipboard write operations without prompting.
     */
    ALWAYS_ALLOW,

    /**
     * Silently ignore all OSC 52 clipboard write operations.
     */
    ALWAYS_DENY,
}
