package com.johnan.terminal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

@Composable
fun KeyBarLayout(
    modifier: Modifier = Modifier,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()

    // Use BoxWithConstraints to capture the actual available width before horizontalScroll removes constraints.
    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = constraints.maxWidth

        Layout(
            contents = listOf(leftContent, rightContent),
            modifier = Modifier.horizontalScroll(scrollState)
        ) { (leftMeasurables, rightMeasurables), layoutConstraints ->
            // Use infinite width for children to let them take their natural size.
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

            // Layout width expands to available screen width (if content is smaller) to allow right alignment.
            // If content is larger, layout width is the total content width, enabling scrolling.
            val computedLayoutWidth = maxOf(totalContentWidth, availableWidth)

            layout(computedLayoutWidth, maxHeight) {
                var xPosition = 0
                for (i in 0 until leftPlaceables.size) {
                    val placeable = leftPlaceables[i]
                    placeable.placeRelative(xPosition, (maxHeight - placeable.height) / 2)
                    xPosition += placeable.width
                }

                // If content is smaller than available width, push right items to the far edge.
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
