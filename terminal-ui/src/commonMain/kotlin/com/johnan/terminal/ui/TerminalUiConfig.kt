package com.johnan.terminal.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnan.terminal.core.KeyBarUiItem
import com.johnan.terminal.core.TerminalColorScheme

/**
 * Visual cursor rendering style.
 */
enum class TerminalCursorStyle {
    BLOCK,
    UNDERLINE,
    BEAM,
}

/**
 * Font, size, and character spacing configuration.
 */
@Immutable
data class TerminalTypographyConfig(
    val fontSize: TextUnit = 14.sp,
    val fontFamily: FontFamily = FontFamily.Monospace,
    val lineHeightMultiplier: Float = 1.0f,
    val letterSpacing: TextUnit = 0.sp,
)

/**
 * Cursor shape, animation, and color customization.
 */
@Immutable
data class TerminalCursorConfig(
    val style: TerminalCursorStyle = TerminalCursorStyle.BLOCK,
    val blink: Boolean = true,
    val blinkRateMs: Long = 500L,
    val overrideColor: Color? = null,
)

/**
 * Touch, gesture, and mouse interaction configuration.
 */
@Immutable
data class TerminalGestureConfig(
    val enableTouchToFocus: Boolean = true,
    val enableSelection: Boolean = true,
    val enableUrlClick: Boolean = true,
    val wheelScrollMultiplier: Float = 1.0f,
    val longPressTimeoutMs: Long = 400L,
    val touchScrollSendsWheelOnly: Boolean = false,
)


/**
 * Virtual soft key toolbar appearance and item list.
 */
@Immutable
data class TerminalKeyBarConfig(
    val isVisible: Boolean = true,
    val height: Dp = 48.dp,
    val items: List<KeyBarUiItem> = emptyList(),
)

/**
 * Rendering pipeline cache and layout padding options.
 */
@Immutable
data class TerminalRenderOptimizationConfig(
    val cacheRowBatchCapacity: Int = 200,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
)

/**
 * Composite UI and rendering configuration for terminal Compose components.
 */
@Immutable
data class TerminalUiConfig(
    val typography: TerminalTypographyConfig = TerminalTypographyConfig(),
    val colorScheme: TerminalColorScheme = TerminalColorScheme.DEFAULT,
    val cursor: TerminalCursorConfig = TerminalCursorConfig(),
    val gestures: TerminalGestureConfig = TerminalGestureConfig(),
    val keyBar: TerminalKeyBarConfig = TerminalKeyBarConfig(),
    val render: TerminalRenderOptimizationConfig = TerminalRenderOptimizationConfig(),
) {
    companion object {
        val DEFAULT = TerminalUiConfig()
    }
}

/**
 * Ambient CompositionLocal providing active [TerminalUiConfig].
 */
val LocalTerminalUiConfig = staticCompositionLocalOf { TerminalUiConfig.DEFAULT }

/**
 * DSL builder for constructing [TerminalUiConfig].
 */
class TerminalUiConfigBuilder {
    var typography: TerminalTypographyConfig = TerminalTypographyConfig()
    var colorScheme: TerminalColorScheme = TerminalColorScheme.DEFAULT
    var cursor: TerminalCursorConfig = TerminalCursorConfig()
    var gestures: TerminalGestureConfig = TerminalGestureConfig()
    var keyBar: TerminalKeyBarConfig = TerminalKeyBarConfig()
    var render: TerminalRenderOptimizationConfig = TerminalRenderOptimizationConfig()

    fun typography(init: TerminalTypographyConfigBuilder.() -> Unit) {
        val builder = TerminalTypographyConfigBuilder().apply {
            fontSize = typography.fontSize
            fontFamily = typography.fontFamily
            lineHeightMultiplier = typography.lineHeightMultiplier
            letterSpacing = typography.letterSpacing
        }
        builder.init()
        typography = builder.build()
    }

    fun cursor(init: TerminalCursorConfigBuilder.() -> Unit) {
        val builder = TerminalCursorConfigBuilder().apply {
            style = cursor.style
            blink = cursor.blink
            blinkRateMs = cursor.blinkRateMs
            overrideColor = cursor.overrideColor
        }
        builder.init()
        cursor = builder.build()
    }

    fun gestures(init: TerminalGestureConfigBuilder.() -> Unit) {
        val builder = TerminalGestureConfigBuilder().apply {
            enableTouchToFocus = gestures.enableTouchToFocus
            enableSelection = gestures.enableSelection
            enableUrlClick = gestures.enableUrlClick
            wheelScrollMultiplier = gestures.wheelScrollMultiplier
            longPressTimeoutMs = gestures.longPressTimeoutMs
            touchScrollSendsWheelOnly = gestures.touchScrollSendsWheelOnly
        }
        builder.init()
        gestures = builder.build()
    }


    fun keyBar(init: TerminalKeyBarConfigBuilder.() -> Unit) {
        val builder = TerminalKeyBarConfigBuilder().apply {
            isVisible = keyBar.isVisible
            height = keyBar.height
            items = keyBar.items
        }
        builder.init()
        keyBar = builder.build()
    }

    fun render(init: TerminalRenderOptimizationConfigBuilder.() -> Unit) {
        val builder = TerminalRenderOptimizationConfigBuilder().apply {
            cacheRowBatchCapacity = render.cacheRowBatchCapacity
            contentPadding = render.contentPadding
        }
        builder.init()
        render = builder.build()
    }

    fun build(): TerminalUiConfig =
        TerminalUiConfig(
            typography = typography,
            colorScheme = colorScheme,
            cursor = cursor,
            gestures = gestures,
            keyBar = keyBar,
            render = render,
        )
}

class TerminalTypographyConfigBuilder {
    var fontSize: TextUnit = 14.sp
    var fontFamily: FontFamily = FontFamily.Monospace
    var lineHeightMultiplier: Float = 1.0f
    var letterSpacing: TextUnit = 0.sp

    fun build() = TerminalTypographyConfig(fontSize, fontFamily, lineHeightMultiplier, letterSpacing)
}

class TerminalCursorConfigBuilder {
    var style: TerminalCursorStyle = TerminalCursorStyle.BLOCK
    var blink: Boolean = true
    var blinkRateMs: Long = 500L
    var overrideColor: Color? = null

    fun build() = TerminalCursorConfig(style, blink, blinkRateMs, overrideColor)
}

class TerminalGestureConfigBuilder {
    var enableTouchToFocus: Boolean = true
    var enableSelection: Boolean = true
    var enableUrlClick: Boolean = true
    var wheelScrollMultiplier: Float = 1.0f
    var longPressTimeoutMs: Long = 400L
    var touchScrollSendsWheelOnly: Boolean = false

    fun build() =
        TerminalGestureConfig(
            enableTouchToFocus = enableTouchToFocus,
            enableSelection = enableSelection,
            enableUrlClick = enableUrlClick,
            wheelScrollMultiplier = wheelScrollMultiplier,
            longPressTimeoutMs = longPressTimeoutMs,
            touchScrollSendsWheelOnly = touchScrollSendsWheelOnly,
        )
}


class TerminalKeyBarConfigBuilder {
    var isVisible: Boolean = true
    var height: Dp = 48.dp
    var items: List<KeyBarUiItem> = emptyList()

    fun build() = TerminalKeyBarConfig(isVisible, height, items)
}

class TerminalRenderOptimizationConfigBuilder {
    var cacheRowBatchCapacity: Int = 200
    var contentPadding: PaddingValues = PaddingValues(0.dp)

    fun build() = TerminalRenderOptimizationConfig(cacheRowBatchCapacity, contentPadding)
}

/**
 * Creates a [TerminalUiConfig] instance using DSL syntax.
 */
inline fun terminalUiConfig(init: TerminalUiConfigBuilder.() -> Unit): TerminalUiConfig {
    val builder = TerminalUiConfigBuilder()
    builder.init()
    return builder.build()
}
