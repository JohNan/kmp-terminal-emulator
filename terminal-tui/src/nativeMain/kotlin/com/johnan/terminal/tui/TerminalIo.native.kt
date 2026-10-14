package com.johnan.terminal.tui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.BRKINT
import platform.posix.CS8
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.ICRNL
import platform.posix.IEXTEN
import platform.posix.INPCK
import platform.posix.ISIG
import platform.posix.ISTRIP
import platform.posix.IXON
import platform.posix.OPOST
import platform.posix.POLLIN
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.TCSAFLUSH
import platform.posix.VMIN
import platform.posix.VTIME
import platform.posix.fflush
import platform.posix.fputs
import platform.posix.getenv
import platform.posix.ioctl
import platform.posix.memcpy
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.read
import platform.posix.stdout
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios
import platform.posix.winsize
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
class NativeTerminalIo : TerminalIo {
    private val origTermios = nativeHeap.alloc<termios>()
    private var inRawMode = false

    override fun enterRawTuiMode() {
        if (!inRawMode) {
            inRawMode = true
            tcgetattr(STDIN_FILENO, origTermios.ptr)

            memScoped {
                val raw = alloc<termios>()
                memcpy(raw.ptr, origTermios.ptr, kotlinx.cinterop.sizeOf<termios>().convert())
                raw.c_iflag =
                    (raw.c_iflag.toLong() and (BRKINT or ICRNL or INPCK or ISTRIP or IXON).toLong().inv()).convert()
                raw.c_oflag = (raw.c_oflag.toLong() and OPOST.toLong().inv()).convert()
                raw.c_cflag = (raw.c_cflag.toLong() or CS8.toLong()).convert()
                raw.c_lflag = (raw.c_lflag.toLong() and (ECHO or ICANON or IEXTEN or ISIG).toLong().inv()).convert()
                raw.c_cc[VMIN.convert()] = 0u
                raw.c_cc[VTIME.convert()] = 0u
                tcsetattr(STDIN_FILENO, TCSAFLUSH, raw.ptr)
            }

            writeStdout("\u001b[?1049h\u001b[?1000h\u001b[?1006h\u001b[?2004h")
        }
    }

    override fun exitRawTuiMode() {
        if (inRawMode) {
            inRawMode = false
            writeStdout("\u001b[?2004l\u001b[?1006l\u001b[?1000l\u001b[?25h\u001b[0m\u001b[?1049l")
            tcsetattr(STDIN_FILENO, TCSAFLUSH, origTermios.ptr)
        }
    }

    override fun getTerminalSize(): TerminalSize {
        memScoped {
            val ws = alloc<winsize>()
            val tiocgwinsz = if (Platform.osFamily == OsFamily.MACOSX || Platform.osFamily == OsFamily.IOS) {
                0x40087468UL
            } else {
                0x5413UL
            }

            if (ioctl(STDOUT_FILENO, tiocgwinsz.convert(), ws.ptr) == 0) {
                val c = ws.ws_col.toInt()
                val r = ws.ws_row.toInt()
                if (c > 0 && r > 0) {
                    return TerminalSize(cols = c, rows = r)
                }
            }
        }

        val envCols = getenv("COLUMNS")?.toKString()?.toIntOrNull()
        val envRows = getenv("LINES")?.toKString()?.toIntOrNull()
        if (envCols != null && envRows != null && envCols > 0 && envRows > 0) {
            return TerminalSize(cols = envCols, rows = envRows)
        }

        return TerminalSize(cols = 80, rows = 24)
    }

    override fun readInput(buffer: ByteArray, timeoutMs: Int): Int {
        if (buffer.isEmpty()) return 0
        memScoped {
            val pfd = alloc<pollfd>()
            pfd.fd = STDIN_FILENO
            pfd.events = POLLIN.convert()
            pfd.revents = 0

            val ret = poll(pfd.ptr, 1.convert(), timeoutMs)
            if (ret > 0 && (pfd.revents.toInt() and POLLIN) != 0) {
                val bytesRead = buffer.usePinned { pinned ->
                    read(STDIN_FILENO, pinned.addressOf(0), buffer.size.convert())
                }
                return if (bytesRead > 0) bytesRead.toInt() else 0
            }
        }
        return 0
    }

    override fun writeStdout(data: String) {
        if (data.isNotEmpty()) {
            fputs(data, stdout)
            fflush(stdout)
        }
    }
}

actual fun createPlatformTerminalIo(): TerminalIo = NativeTerminalIo()
