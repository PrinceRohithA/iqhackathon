package com.agent.mobile.agent

import com.agent.mobile.data.SettingsRepository
import com.agent.shared.AgentJson
import com.agent.shared.model.ConnectionState
import com.agent.shared.model.DeviceInfo
import com.agent.shared.model.HealthResponse
import com.agent.shared.model.PairingRequest
import com.agent.shared.model.PairingResponse
import com.agent.shared.model.ProtocolMessage
import com.agent.shared.model.ToolDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AgentClient(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = AgentJson.instance
    private val connecting = AtomicBoolean(false)

    private var socket: WebSocket? = null
    private var host: String = ""
    private var port: Int = 8787
    private var secure: Boolean = false
    private var token: String = ""
    private var shouldReconnect = false

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _messages = MutableSharedFlow<ProtocolMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<ProtocolMessage> = _messages.asSharedFlow()

    private val _health = MutableStateFlow<HealthResponse?>(null)
    val health: StateFlow<HealthResponse?> = _health.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun connect(host: String, port: Int, token: String) {
        val endpoint = parseAgentEndpoint(host, port)
        this.host = endpoint.host
        this.port = endpoint.port
        this.secure = endpoint.secure
        this.token = token
        shouldReconnect = true
        connecting.set(false)
        openSocket()
    }

    fun disconnect() {
        shouldReconnect = false
        socket?.close(1000, "client disconnect")
        socket = null
        _connection.value = ConnectionState.DISCONNECTED
    }

    fun send(message: ProtocolMessage) {
        val payload = json.encodeToString(ProtocolMessage.serializer(), message)
        socket?.send(payload)
    }

    fun sendChat(messageId: String, content: String, imageUri: String? = null) {
        send(ProtocolMessage.ChatUserMessage(messageId, content = content, imageUri = imageUri))
    }

    fun syncTools(tools: List<ToolDefinition>) {
        send(ProtocolMessage.ToolsSync(tools.map { it.copy(workflow = it.workflow) }))
    }

    suspend fun pair(host: String, port: Int, code: String, device: DeviceInfo): PairingResponse = withContext(Dispatchers.IO) {
        val endpoint = parseAgentEndpoint(host, port)
        if (endpoint.host.isBlank()) error("Enter the PC IP address")
        if (code.isBlank()) error("Enter the pairing code from the PC")
        val url = "${endpoint.httpBase}/session"
        val body = json.encodeToString(
            PairingRequest.serializer(),
            PairingRequest(code = code.trim(), device = device),
        )
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(parseError(text).ifBlank { "Pairing failed (${response.code})" })
            }
            json.decodeFromString(PairingResponse.serializer(), text)
        }
    }

    suspend fun fetchHealth(host: String, port: Int): HealthResponse = withContext(Dispatchers.IO) {
        val endpoint = parseAgentEndpoint(host, port)
        val request = Request.Builder().url("${endpoint.httpBase}/health").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Health check failed (${response.code})")
            json.decodeFromString(HealthResponse.serializer(), text)
        }
    }

    suspend fun postChat(sessionId: String, message: String): String = withContext(Dispatchers.IO) {
        val url = "${AgentEndpoint(host, port, secure).httpBase}/chat"
        val payload = json.encodeToString(
            com.agent.shared.model.ChatRequest.serializer(),
            com.agent.shared.model.ChatRequest(sessionId, message),
        )
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(parseError(text).ifBlank { "Chat failed (${response.code})" })
            text
        }
    }

    private fun openSocket() {
        if (token.isBlank() || host.isBlank()) return
        if (!connecting.compareAndSet(false, true)) return
        socket?.cancel()
        _connection.value = if (_connection.value == ConnectionState.DISCONNECTED) {
            ConnectionState.CONNECTING
        } else {
            ConnectionState.RECONNECTING
        }
        val url = "${AgentEndpoint(host, port, secure).wsBase}/ws?token=$token"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connecting.set(false)
                _connection.value = ConnectionState.CONNECTED
                _lastError.value = null
                scope.launch {
                    runCatching { _health.value = fetchHealth(host, port) }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { json.decodeFromString(ProtocolMessage.serializer(), text) }.getOrNull()
                if (msg != null) {
                    _messages.tryEmit(msg)
                    if (msg is ProtocolMessage.Health) _health.value = msg.health
                    if (msg is ProtocolMessage.ErrorMsg) _lastError.value = msg.message
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connecting.set(false)
                _lastError.value = t.message
                _connection.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connecting.set(false)
                if (shouldReconnect) {
                    _connection.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connection.value = ConnectionState.DISCONNECTED
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        scope.launch {
            delay(2_000)
            if (shouldReconnect && scope.isActive && _connection.value != ConnectionState.CONNECTED) {
                openSocket()
            }
        }
    }

    private fun parseError(text: String): String =
        runCatching {
            json.decodeFromString(ProtocolMessage.ErrorMsg.serializer(), text).message
        }.getOrElse {
            runCatching {
                json.parseToJsonElement(text).toString()
            }.getOrDefault(text)
        }
}

data class AgentEndpoint(
    val host: String,
    val port: Int,
    val secure: Boolean,
) {
    val httpBase: String get() = url(if (secure) "https" else "http", if (secure) 443 else 80)
    val wsBase: String get() = url(if (secure) "wss" else "ws", if (secure) 443 else 80)

    private fun url(scheme: String, defaultPort: Int): String =
        if (port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
}

internal fun normalizeHostAndPort(host: String, port: Int): Pair<String, Int> {
    val endpoint = parseAgentEndpoint(host, port)
    return endpoint.host to endpoint.port
}

internal fun parseAgentEndpoint(host: String, port: Int): AgentEndpoint {
    var raw = host.trim()
    var secure: Boolean? = null
    when {
        raw.startsWith("https://", ignoreCase = true) || raw.startsWith("wss://", ignoreCase = true) -> {
            secure = true
            raw = raw.substringAfter("://")
        }
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("ws://", ignoreCase = true) -> {
            secure = false
            raw = raw.substringAfter("://")
        }
    }
    raw = raw.substringBefore("/").substringBefore("?").trim()
    var parsedHost = raw
    var parsedPort = port
    val colon = raw.indexOf(':')
    if (colon > 0 && raw.indexOf(':') == raw.lastIndexOf(':')) {
        raw.substring(colon + 1).toIntOrNull()?.let {
            parsedHost = raw.substring(0, colon)
            parsedPort = it
        }
    }
    val publicHttps = PUBLIC_HTTPS_SUFFIXES.any { parsedHost.endsWith(it, ignoreCase = true) }
    val useHttps = secure ?: (parsedPort == 443 || publicHttps)
    val usePort = if (useHttps && publicHttps && parsedPort == 8787) 443 else parsedPort
    return AgentEndpoint(parsedHost, usePort, useHttps)
}

private val PUBLIC_HTTPS_SUFFIXES = listOf(
    "trycloudflare.com",
    "ngrok-free.app",
    "ngrok.io",
    "loca.lt",
)
