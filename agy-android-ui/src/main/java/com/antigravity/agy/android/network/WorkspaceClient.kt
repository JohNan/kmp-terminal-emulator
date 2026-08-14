package com.antigravity.agy.android.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Commands received from the server.
 */
object ServerCommand {
    const val OUTPUT: Byte = 0 // Also ASCII '0' (48)
    const val SET_WINDOW_TITLE: Byte = 1 // Also ASCII '1' (49)
    const val SET_PREFERENCES: Byte = 2 // Also ASCII '2' (50)
}

/**
 * Commands sent by the client.
 */
object ClientCommand {
    const val INPUT = "0"
    const val RESIZE_TERMINAL = "1"
    const val PAUSE = "2"
    const val RESUME = "3"
}

/**
 * Connection states for [WorkspaceClient].
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data object Connected : ConnectionState

    data class Error(
        val throwable: Throwable,
        val message: String = throwable.message ?: "Unknown error"
    ) : ConnectionState
}

/**
 * Ktor WebSocket client for connecting to an agy workspace proxy.
 *
 * Connects to `wss://<proxy-host>/ws?arg=<workspace>&arg=agy`.
 * Implements a 30-second ping/heartbeat loop and parses binary messages
 * (0 = OUTPUT, 1 = SET_WINDOW_TITLE).
 *
 * @param workspaceId The target workspace ID.
 * @param proxyHostUrl The base URL or host of the proxy server.
 * @param client Optional pre-configured [HttpClient] with OkHttp engine and WebSockets plugin.
 * @param coroutineScope Coroutine scope for connection and heartbeat tasks.
 */
class WorkspaceClient(
    val workspaceId: String,
    val proxyHostUrl: String,
    private val client: HttpClient = createDefaultHttpClient(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    companion object {
        const val HEARTBEAT_INTERVAL_MS: Long = 30_000L // 30 seconds

        /**
         * Creates a default Ktor [HttpClient] using the OkHttp engine and WebSockets plugin.
         */
        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                install(WebSockets) {
                    pingIntervalMillis = HEARTBEAT_INTERVAL_MS
                    maxFrameSize = Long.MAX_VALUE
                }
            }
        }

        /**
         * Constructs the full WebSocket URL targeting the workspace proxy.
         * Format: `wss://<proxy-host>/ws?arg=<workspace>&arg=agy`
         */
        fun buildWebSocketUrl(
            proxyHostUrl: String,
            workspaceId: String,
            cliType: String = "agy",
        ): String {
            val trimmed = proxyHostUrl.trim()
            val (scheme, rest) = when {
                trimmed.startsWith("wss://", ignoreCase = true) -> "wss://" to trimmed.substring(6)
                trimmed.startsWith("ws://", ignoreCase = true) -> "ws://" to trimmed.substring(5)
                trimmed.startsWith("https://", ignoreCase = true) -> "wss://" to trimmed.substring(8)
                trimmed.startsWith("http://", ignoreCase = true) -> "ws://" to trimmed.substring(7)
                else -> "wss://" to trimmed
            }

            val cleanRest = rest.trimEnd('/')
            val pathWithoutQuery = if (cleanRest.contains("?")) cleanRest.substringBefore("?") else cleanRest
            val fullPath = if (pathWithoutQuery.contains("/")) {
                pathWithoutQuery
            } else {
                "$pathWithoutQuery/ws"
            }

            val encodedWorkspace = URLEncoder.encode(workspaceId, StandardCharsets.UTF_8.name())
            val encodedCli = URLEncoder.encode(cliType, StandardCharsets.UTF_8.name())
            return "$scheme$fullPath?arg=$encodedWorkspace&arg=$encodedCli"
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _outputFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val outputFlow: SharedFlow<ByteArray> = _outputFlow.asSharedFlow()

    private val _titleFlow = MutableStateFlow<String?>(null)
    val titleFlow: StateFlow<String?> = _titleFlow.asStateFlow()

    /**
     * Connects to the workspace WebSocket server.
     */
    fun connect() {
        val st = _connectionState.value
        if (st == ConnectionState.Connecting || st == ConnectionState.Connected) {
            return
        }

        connectionJob?.cancel()
        connectionJob = coroutineScope.launch {
            _connectionState.value = ConnectionState.Connecting
            val fullUrl = buildWebSocketUrl(proxyHostUrl, workspaceId)

            try {
                val wsSession = client.webSocketSession {
                    url(fullUrl)
                }
                session = wsSession
                _connectionState.value = ConnectionState.Connected

                // Start 30-second heartbeat loop
                startHeartbeatLoop(wsSession)

                // Message processing loop
                try {
                    for (frame in wsSession.incoming) {
                        handleIncomingFrame(frame)
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Normal channel closure
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _connectionState.value = ConnectionState.Error(e, "Receive error: ${e.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _connectionState.value = ConnectionState.Error(e, "Connection failed: ${e.message}")
            } finally {
                stopHeartbeatLoop()
                session = null
                if (_connectionState.value !is ConnectionState.Error) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    /**
     * Disconnects the WebSocket session gracefully.
     */
    fun disconnect() {
        stopHeartbeatLoop()
        connectionJob?.cancel()
        connectionJob = null
        coroutineScope.launch {
            try {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            } catch (_: Exception) {
            } finally {
                session = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    /**
     * Parses incoming WebSocket frames (binary and text).
     */
    private suspend fun handleIncomingFrame(frame: Frame) {
        when (frame) {
            is Frame.Binary -> {
                val bytes = frame.readBytes()
                if (bytes.isEmpty()) return

                val commandByte = bytes[0]
                val payload = if (bytes.size > 1) bytes.copyOfRange(1, bytes.size) else ByteArray(0)

                when (commandByte) {
                    ServerCommand.OUTPUT, '0'.code.toByte() -> {
                        _outputFlow.emit(payload)
                    }
                    ServerCommand.SET_WINDOW_TITLE, '1'.code.toByte() -> {
                        val title = payload.decodeToString()
                        _titleFlow.value = title
                    }
                    ServerCommand.SET_PREFERENCES, '2'.code.toByte() -> {
                        // Optional preferences JSON from server
                    }
                }
            }
            is Frame.Text -> {
                val text = frame.readText()
                if (text.isEmpty()) return

                val commandChar = text[0]
                val payload = if (text.length > 1) text.substring(1) else ""

                when (commandChar) {
                    '0' -> {
                        _outputFlow.emit(payload.toByteArray(StandardCharsets.UTF_8))
                    }
                    '1' -> {
                        _titleFlow.value = payload
                    }
                }
            }
            else -> {
                // Ping/Pong/Close frames handled by Ktor
            }
        }
    }

    /**
     * Starts the 30-second ping/heartbeat loop.
     */
    private fun startHeartbeatLoop(wsSession: DefaultClientWebSocketSession) {
        stopHeartbeatLoop()
        heartbeatJob = coroutineScope.launch {
            while (isActive && _connectionState.value == ConnectionState.Connected) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    // Send ClientCommand.INPUT with empty payload to act as keepalive
                    wsSession.send(Frame.Binary(true, ClientCommand.INPUT.toByteArray(StandardCharsets.UTF_8)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (isActive) {
                        _connectionState.value = ConnectionState.Error(e, "Heartbeat failed: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    /**
     * Stops the heartbeat loop.
     */
    private fun stopHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Sends keyboard input string to the terminal.
     */
    suspend fun sendInput(data: String) {
        val wsSession = session ?: return
        if (_connectionState.value != ConnectionState.Connected) return
        try {
            val payload = (ClientCommand.INPUT + data).toByteArray(StandardCharsets.UTF_8)
            wsSession.send(Frame.Binary(true, payload))
        } catch (_: Exception) {
        }
    }

    /**
     * Sends raw keyboard input bytes to the terminal.
     */
    suspend fun sendInput(bytes: ByteArray) {
        val wsSession = session ?: return
        if (_connectionState.value != ConnectionState.Connected) return
        try {
            val payload = ByteArray(1 + bytes.size)
            payload[0] = '0'.code.toByte()
            System.arraycopy(bytes, 0, payload, 1, bytes.size)
            wsSession.send(Frame.Binary(true, payload))
        } catch (_: Exception) {
        }
    }

    /**
     * Sends terminal resize command to synchronise columns and rows with the backend PTY.
     */
    suspend fun sendResize(cols: Int, rows: Int) {
        val wsSession = session ?: return
        if (_connectionState.value != ConnectionState.Connected) return
        try {
            val json = """{"columns":$cols,"rows":$rows}"""
            val payload = (ClientCommand.RESIZE_TERMINAL + json).toByteArray(StandardCharsets.UTF_8)
            wsSession.send(Frame.Binary(true, payload))
        } catch (_: Exception) {
        }
    }

    /**
     * Pauses output from the terminal PTY (flow control).
     */
    suspend fun sendPause() {
        val wsSession = session ?: return
        if (_connectionState.value != ConnectionState.Connected) return
        try {
            wsSession.send(Frame.Binary(true, ClientCommand.PAUSE.toByteArray(StandardCharsets.UTF_8)))
        } catch (_: Exception) {
        }
    }

    /**
     * Resumes output from the terminal PTY (flow control).
     */
    suspend fun sendResume() {
        val wsSession = session ?: return
        if (_connectionState.value != ConnectionState.Connected) return
        try {
            wsSession.send(Frame.Binary(true, ClientCommand.RESUME.toByteArray(StandardCharsets.UTF_8)))
        } catch (_: Exception) {
        }
    }
}
