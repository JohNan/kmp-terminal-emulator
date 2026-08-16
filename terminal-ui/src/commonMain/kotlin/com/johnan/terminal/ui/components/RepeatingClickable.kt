package com.johnan.terminal.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A modifier that invokes [onClick] repeatedly while the component is pressed.
 *
 * @param enabled Controls the enabled state of this modifier. When `false`, this component will not respond to user input.
 * @param initialDelay The delay in milliseconds before the first repeat click after the initial click.
 * @param repeatDelay The delay in milliseconds between subsequent repeat clicks.
 * @param onClick The callback to be invoked.
 */
fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelay: Long = 400,
    repeatDelay: Long = 100,
    onClick: () -> Unit,
): Modifier =
    composed {
        val currentOnClick = rememberUpdatedState(onClick)
        val scope = rememberCoroutineScope()

        if (!enabled) return@composed this

        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                // Wait for the first down event.
                // requireUnconsumed = false allowing it to work with other gesture detectors (like clickable for ripple)
                awaitFirstDown(requireUnconsumed = false)

                // Invoke the initial click
                currentOnClick.value()

                val job =
                    scope.launch {
                        delay(initialDelay)
                        while (true) {
                            currentOnClick.value()
                            delay(repeatDelay)
                        }
                    }

                // Wait for the gesture to finish (up or cancel)
                waitForUpOrCancellation()

                // Cancel the repeating job
                job.cancel()
            }
        }
    }
