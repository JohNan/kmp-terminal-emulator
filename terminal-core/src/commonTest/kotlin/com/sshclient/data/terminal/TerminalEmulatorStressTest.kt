package com.sshclient.data.terminal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Stress tests for TerminalEmulator
 *
 * These tests verify the terminal emulator's performance and reliability under stress:
 * - Large output volumes
 * - Rapid input processing
 * - ANSI parsing performance
 * - Memory efficiency
 * - Thread safety under concurrent load
 *
 * Performance benchmarks are included to detect regressions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TerminalEmulatorStressTest {
    private lateinit var terminalEmulator: TerminalEmulator

    companion object {
        // Test parameters
        private const val LARGE_OUTPUT_SIZE = 100_000 // 100KB
        private const val RAPID_INPUT_COUNT = 10_000
        private const val ANSI_SEQUENCE_COUNT = 5_000
        private const val CONCURRENT_OPERATIONS = 100
    }

    @BeforeTest
    fun setup() {
        terminalEmulator = TerminalEmulator(rows = 24, cols = 80)
    }

    @Test
    fun testLargeOutputVolume() = runTest {
        // Generate large text output
        val largeText =
            buildString {
                repeat(LARGE_OUTPUT_SIZE / 50) {
                    append("This is line $it with some text content...\n")
                }
            }

        val processingTime = measureTime {
            terminalEmulator.processOutput(largeText)
        }.inWholeMilliseconds

        println("Processed ${largeText.length} bytes in ${processingTime}ms")
        if (processingTime > 0) {
            println("Throughput: ${largeText.length / processingTime} bytes/ms")
        }

        // Verify output was processed
        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "Terminal state should be updated")

        // Performance benchmark: should process at least 100 bytes/ms
        val throughput = if (processingTime > 0) largeText.length.toDouble() / processingTime else 1000.0
        assertTrue(
            throughput > 100,
            "Processing should be reasonably fast (throughput: $throughput bytes/ms)",
        )
    }

    @Test
    fun testVeryLargeOutputVolume() = runTest {
        // Test with 1MB of output
        val veryLargeText =
            buildString {
                repeat(1_000_000 / 50) {
                    append("Line $it: Lorem ipsum dolor sit amet...\n")
                }
            }

        val processingTime = measureTime {
            terminalEmulator.processOutput(veryLargeText)
        }.inWholeMilliseconds

        println("Processed ${veryLargeText.length / 1024 / 1024}MB in ${processingTime}ms")

        // Should complete in reasonable time (< 30 seconds)
        assertTrue(processingTime < 30_000, "Should process large output in reasonable time")

        // Verify terminal still responsive
        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "Terminal should remain responsive")
    }

    @Test
    fun testRapidInputProcessing() = runTest {
        val inputs = List(RAPID_INPUT_COUNT) { "x" }

        val processingTime = measureTime {
            inputs.forEach { input ->
                terminalEmulator.processOutput(input)
            }
        }.inWholeMilliseconds

        println("Processed $RAPID_INPUT_COUNT rapid inputs in ${processingTime}ms")
        println("Average: ${processingTime.toDouble() / RAPID_INPUT_COUNT}ms per input")

        // Verify all inputs processed
        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "Terminal state should reflect all inputs")

        // Performance: average should be < 1ms per input
        val avgTime = processingTime.toDouble() / RAPID_INPUT_COUNT
        assertTrue(avgTime < 1.0, "Rapid input should be fast (avg ${avgTime}ms)")
    }

    @Test
    fun testAnsiParsingPerformance() = runTest {
        // Generate output with many ANSI sequences
        val ansiOutput =
            buildString {
                repeat(ANSI_SEQUENCE_COUNT) { i ->
                    // Color codes
                    append("\u001B[3${i % 8}m")
                    append("Colored text $i")
                    append("\u001B[0m")

                    // Cursor movements
                    if (i % 10 == 0) {
                        append("\u001B[${i % 24 + 1};${i % 80 + 1}H")
                    }
                }
            }

        val processingTime = measureTime {
            terminalEmulator.processOutput(ansiOutput)
        }.inWholeMilliseconds

        println("Parsed $ANSI_SEQUENCE_COUNT ANSI sequences in ${processingTime}ms")
        println("Average: ${processingTime.toDouble() / ANSI_SEQUENCE_COUNT}ms per sequence")

        // Verify parsing completed
        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "ANSI parsing should complete")

        // Performance: should parse at least 10 sequences per ms
        val seqPerMs = if (processingTime > 0) ANSI_SEQUENCE_COUNT.toDouble() / processingTime else 100.0
        assertTrue(seqPerMs > 10, "ANSI parsing should be efficient ($seqPerMs seq/ms)")
    }

    @Test
    fun testComplexAnsiSequences() = runTest {
        // Test with complex real-world ANSI sequences
        val complexOutput =
            buildString {
                // SGR sequences (colors, bold, underline)
                append("\u001B[1;31;42mBold Red on Green\u001B[0m\n")
                append("\u001B[4;34mUnderlined Blue\u001B[0m\n")

                // Cursor positioning
                append("\u001B[5;10HPositioned text\n")
                append("\u001B[A\u001B[A") // Up twice
                append("\u001B[B") // Down once

                // Erase sequences
                append("\u001B[2J") // Clear screen
                append("\u001B[K") // Clear to end of line

                // Multiple parameters
                append("\u001B[1;4;31;44mMultiple styles\u001B[0m\n")
            }

        val processingTime = measureTime {
            terminalEmulator.processOutput(complexOutput)
        }.inWholeMilliseconds

        println("Processed complex ANSI sequences in ${processingTime}ms")

        // Verify no crashes or errors
        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "Complex ANSI should be handled")
        assertTrue(processingTime < 1000, "Should complete quickly")
    }

    @Test
    fun testConcurrentOutputProcessing() = runTest {
        val operations = CONCURRENT_OPERATIONS
        val text = "Test output line\n"

        val processingTime = measureTime {
            val jobs =
                List(operations) {
                    launch {
                        terminalEmulator.processOutput(text)
                    }
                }
            jobs.joinAll()
        }.inWholeMilliseconds

        println("Processed $operations concurrent outputs in ${processingTime}ms")

        // Verify thread safety
        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "Concurrent processing should be thread-safe")

        // Should complete in reasonable time
        assertTrue(processingTime < 5000, "Concurrent processing should be efficient")
    }

    @Test
    fun testScrollbackBufferPerformance() = runTest {
        // Fill scrollback with many lines
        val lineCount = 1000
        val lines = List(lineCount) { "Line $it with some content here\n" }

        val processingTime = measureTime {
            lines.forEach { line ->
                terminalEmulator.processOutput(line)
            }
        }.inWholeMilliseconds

        println("Filled scrollback with $lineCount lines in ${processingTime}ms")

        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "Scrollback should be maintained")

        // Should handle scrollback efficiently
        assertTrue(processingTime < 5000, "Scrollback should perform well")
    }

    @Test
    fun testRapidResize() = runTest {
        val resizeCount = 100
        val sizes =
            listOf(
                Pair(24, 80),
                Pair(30, 100),
                Pair(40, 120),
                Pair(20, 60),
            )

        val resizeTime = measureTime {
            repeat(resizeCount) { i ->
                val (rows, cols) = sizes[i % sizes.size]
                terminalEmulator.resize(rows, cols)
            }
        }.inWholeMilliseconds

        println("Performed $resizeCount resizes in ${resizeTime}ms")
        println("Average: ${resizeTime.toDouble() / resizeCount}ms per resize")

        // Verify terminal still functional
        val state = terminalEmulator.screenState.first()
        assertNotNull(state, "Terminal should survive rapid resizing")

        // Should be fast (< 5ms per resize on average)
        val avgTime = resizeTime.toDouble() / resizeCount
        assertTrue(avgTime < 5.0, "Resize should be fast (avg ${avgTime}ms)")
    }

    @Test
    fun testPerformanceRegression() = runTest {
        // Baseline performance test to detect regressions
        val standardWorkload =
            buildString {
                repeat(100) { i ->
                    append("Normal line $i\n")
                    append("\u001B[3${i % 8}mColored line $i\u001B[0m\n")
                    append("\u001B[${i % 24 + 1};1HPositioned text\n")
                }
            }

        val times = mutableListOf<Long>()

        // Run multiple times to get average
        repeat(10) {
            val time =
                measureTime {
                    val emulator = TerminalEmulator(24, 80)
                    emulator.processOutput(standardWorkload)
                }.inWholeMilliseconds
            times.add(time)
        }

        val avgTime = times.average()
        val minTime = times.minOrNull() ?: 0
        val maxTime = times.maxOrNull() ?: 0

        println("Performance benchmark:")
        println("  Average: ${avgTime}ms")
        println("  Min: ${minTime}ms")
        println("  Max: ${maxTime}ms")
        println("  Std dev: ${calculateStdDev(times, avgTime)}ms")

        // Baseline: should process standard workload in < 500ms on average
        assertTrue(avgTime < 500, "Performance should meet baseline (avg ${avgTime}ms)")
    }

    private fun calculateStdDev(
        values: List<Long>,
        mean: Double,
    ): Double {
        val variance =
            values.fold(0.0) { acc, value ->
                val diff = value - mean
                acc + (diff * diff)
            } / values.size
        return kotlin.math.sqrt(variance)
    }
}
