package com.johnan.terminal.core

/**
 * Behavior when the terminal receives a bell (BEL / ASCII 7) signal.
 */
enum class BellBehavior {
    IGNORE,
    VISUAL,
    CALLBACK,
}

/**
 * Immutable configuration for terminal emulation engine and screen buffer dimensions.
 */
data class TerminalConfig(
    val initialRows: Int = 24,
    val initialCols: Int = 80,
    val maxScrollback: Int = 1000,
    val osc52Policy: Osc52Policy = Osc52Policy.ASK,
    val tabStopInterval: Int = 8,
    val autoWrapDefault: Boolean = true,
    val cursorBlinkingDefault: Boolean = false,
    val mouseTrackingEnabled: Boolean = true,
    val bracketedPasteEnabled: Boolean = true,
    val bellBehavior: BellBehavior = BellBehavior.VISUAL,
) {
    init {
        require(initialRows > 0) { "initialRows must be positive: $initialRows" }
        require(initialCols > 0) { "initialCols must be positive: $initialCols" }
        require(maxScrollback >= 0) { "maxScrollback cannot be negative: $maxScrollback" }
        require(tabStopInterval > 0) { "tabStopInterval must be positive: $tabStopInterval" }
    }

    companion object {
        val DEFAULT = TerminalConfig()
    }
}

/**
 * Builder for configuring [TerminalConfig] via DSL.
 */
class TerminalConfigBuilder {
    var initialRows: Int = 24
    var initialCols: Int = 80
    var maxScrollback: Int = 1000
    var osc52Policy: Osc52Policy = Osc52Policy.ASK
    var tabStopInterval: Int = 8
    var autoWrapDefault: Boolean = true
    var cursorBlinkingDefault: Boolean = false
    var mouseTrackingEnabled: Boolean = true
    var bracketedPasteEnabled: Boolean = true
    var bellBehavior: BellBehavior = BellBehavior.VISUAL

    fun build(): TerminalConfig =
        TerminalConfig(
            initialRows = initialRows,
            initialCols = initialCols,
            maxScrollback = maxScrollback,
            osc52Policy = osc52Policy,
            tabStopInterval = tabStopInterval,
            autoWrapDefault = autoWrapDefault,
            cursorBlinkingDefault = cursorBlinkingDefault,
            mouseTrackingEnabled = mouseTrackingEnabled,
            bracketedPasteEnabled = bracketedPasteEnabled,
            bellBehavior = bellBehavior,
        )
}

/**
 * Creates a [TerminalConfig] instance using DSL configuration syntax.
 */
inline fun terminalConfig(init: TerminalConfigBuilder.() -> Unit): TerminalConfig {
    val builder = TerminalConfigBuilder()
    builder.init()
    return builder.build()
}
