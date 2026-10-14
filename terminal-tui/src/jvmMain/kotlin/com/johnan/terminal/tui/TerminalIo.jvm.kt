package com.johnan.terminal.tui

import java.io.InputStream

class JvmTerminalIo : TerminalIo {
    private var inRawMode = false

    override fun enterRawTuiMode() {
        if (!inRawMode) {
            inRawMode = true
            try {
                val os = System.getProperty("os.name")?.lowercase() ?: ""
                if (!os.contains("win")) {
                    ProcessBuilder("sh", "-c", "stty raw -echo </dev/tty").inheritIO().start().waitFor()
                }
            } catch (_: Exception) {
            }
            writeStdout("\u001b[?1049h\u001b[?1000h\u001b[?1006h\u001b[?2004h")
        }
    }

    override fun exitRawTuiMode() {
        if (inRawMode) {
            inRawMode = false
            writeStdout("\u001b[?2004l\u001b[?1006l\u001b[?1000l\u001b[?25h\u001b[0m\u001b[?1049l")
            try {
                val os = System.getProperty("os.name")?.lowercase() ?: ""
                if (!os.contains("win")) {
                    ProcessBuilder("sh", "-c", "stty sane </dev/tty").inheritIO().start().waitFor()
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun getTerminalSize(): TerminalSize {
        val envCols = System.getenv("COLUMNS")?.toIntOrNull()
        val envRows = System.getenv("LINES")?.toIntOrNull()
        if (envCols != null && envRows != null && envCols > 0 && envRows > 0) {
            return TerminalSize(envCols, envRows)
        }

        try {
            val process = ProcessBuilder("sh", "-c", "stty size </dev/tty").start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val parts = output.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val r = parts[0].toIntOrNull() ?: 24
                val c = parts[1].toIntOrNull() ?: 80
                return TerminalSize(cols = c, rows = r)
            }
        } catch (_: Exception) {
        }

        return TerminalSize(cols = 80, rows = 24)
    }

    override fun readInput(buffer: ByteArray, timeoutMs: Int): Int {
        val input: InputStream = System.`in`
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val available = try {
                input.available()
            } catch (_: Exception) {
                0
            }
            if (available > 0) {
                val toRead = minOf(buffer.size, available)
                return input.read(buffer, 0, toRead)
            }
            try {
                Thread.sleep(2)
            } catch (_: InterruptedException) {
                break
            }
        }
        return 0
    }

    override fun writeStdout(data: String) {
        print(data)
        System.out.flush()
    }
}

actual fun createPlatformTerminalIo(): TerminalIo = JvmTerminalIo()
