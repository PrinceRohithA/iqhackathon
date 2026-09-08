package com.agent.backend.devices

import com.agent.shared.model.DeviceInfo
import com.agent.shared.model.ProtocolMessage
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DeviceSession(
    val sessionId: String,
    val token: String,
    val device: DeviceInfo,
    var socket: DefaultWebSocketSession? = null,
    val pending: ConcurrentHashMap<String, CompletableDeferred<ProtocolMessage.ExecutionResult>> = ConcurrentHashMap(),
)

class DeviceManager {
    private val byToken = ConcurrentHashMap<String, DeviceSession>()
    private val bySession = ConcurrentHashMap<String, DeviceSession>()

    fun pair(device: DeviceInfo): DeviceSession {
        val session = DeviceSession(
            sessionId = UUID.randomUUID().toString(),
            token = UUID.randomUUID().toString(),
            device = device,
        )
        byToken[session.token] = session
        bySession[session.sessionId] = session
        return session
    }

    fun authenticate(token: String): DeviceSession? = byToken[token]

    fun attach(token: String, socket: DefaultWebSocketSession): DeviceSession? {
        val session = byToken[token] ?: return null
        session.socket = socket
        return session
    }

    fun detach(token: String) {
        byToken[token]?.socket = null
    }

    fun connected(): DeviceSession? = byToken.values.firstOrNull { it.socket != null }

    fun bySessionId(id: String): DeviceSession? = bySession[id]

    suspend fun send(session: DeviceSession, message: ProtocolMessage) {
        val socket = session.socket ?: error("Device is not connected")
        val payload = com.agent.shared.AgentJson.instance.encodeToString(ProtocolMessage.serializer(), message)
        socket.send(Frame.Text(payload))
    }

    fun complete(result: ProtocolMessage.ExecutionResult) {
        byToken.values.forEach { session ->
            session.pending.remove(result.executionId)?.complete(result)
        }
    }
}
