package com.johnan.terminal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Split scrollable layout aligning leading items to the left and trailing items to the right.
 */
@Composable
fun KeyBarLayout(
    modifier: Modifier = Modifier,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()

    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = constraints.maxWidth

        Layout(
            contents = listOf(leftContent, rightContent),
            modifier = Modifier.horizontalScroll(scrollState),
        ) { (leftMeasurables, rightMeasurables), layoutConstraints ->
            val infiniteConstraints = layoutConstraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)

            val leftPlaceables = ArrayList<androidx.compose.ui.layout.Placeable>(leftMeasurables.size)
            var leftWidth = 0
            var maxHeight = layoutConstraints.minHeight

            for (i in 0 until leftMeasurables.size) {
                val placeable = leftMeasurables[i].measure(infiniteConstraints)
                leftPlaceables.add(placeable)
                leftWidth += placeable.width
                if (placeable.height > maxHeight) {
                    maxHeight = placeable.height
                }
            }

            val rightPlaceables = ArrayList<androidx.compose.ui.layout.Placeable>(rightMeasurables.size)
            var rightWidth = 0

            for (i in 0 until rightMeasurables.size) {
                val placeable = rightMeasurables[i].measure(infiniteConstraints)
                rightPlaceables.add(placeable)
                rightWidth += placeable.width
                if (placeable.height > maxHeight) {
                    maxHeight = placeable.height
                }
            }

            val totalContentWidth = leftWidth + rightWidth
            val computedLayoutWidth = maxOf(totalContentWidth, availableWidth)

            layout(computedLayoutWidth, maxHeight) {
                var xPosition = 0
                for (i in 0 until leftPlaceables.size) {
                    val placeable = leftPlaceables[i]
                    placeable.placeRelative(xPosition, (maxHeight - placeable.height) / 2)
                    xPosition += placeable.width
                }

                var rightXPosition = maxOf(xPosition, computedLayoutWidth - rightWidth)
                for (i in 0 until rightPlaceables.size) {
                    val placeable = rightPlaceables[i]
                    placeable.placeRelative(rightXPosition, (maxHeight - placeable.height) / 2)
                    rightXPosition += placeable.width
                }
            }
        }
    }
}
