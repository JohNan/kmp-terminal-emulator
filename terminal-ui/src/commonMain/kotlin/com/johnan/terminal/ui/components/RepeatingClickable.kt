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
 * Repeats invocation of [onClick] at periodic intervals while pointer remains pressed.
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
                awaitFirstDown(requireUnconsumed = false)
                currentOnClick.value()

                val job =
                    scope.launch {
                        delay(initialDelay)
                        while (true) {
                            currentOnClick.value()
                            delay(repeatDelay)
                        }
                    }

                waitForUpOrCancellation()
                job.cancel()
            }
        }
    }
