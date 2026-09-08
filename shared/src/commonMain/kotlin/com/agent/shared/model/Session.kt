package com.agent.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WorkflowExecution(
    val executionId: String,
    val toolId: String,
    val toolName: String = "",
    val status: ExecutionStatus = ExecutionStatus.CREATED,
    val currentNodeId: String? = null,
    val currentNodeLabel: String? = null,
    val nodes: List<ExecutionNodeState> = emptyList(),
    val result: ToolResult? = null,
    val logs: List<ExecutionLogEntry> = emptyList(),
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
)

@Serializable
data class ExecutionNodeState(
    val nodeId: String,
    val label: String,
    val status: ExecutionStatus = ExecutionStatus.CREATED,
)

@Serializable
data class ExecutionLogEntry(
    val timestamp: Long,
    val nodeId: String? = null,
    val status: String,
    val message: String,
    val error: String? = null,
)

@Serializable
data class AgentEvent(
    val type: String,
    val executionId: String,
    val nodeId: String? = null,
    val message: String? = null,
    val timestamp: Long = 0,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val executionId: String? = null,
    val toolCallsJson: String? = null,
    val imageJpegBase64: String? = null,
    val timestamp: Long,
)

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val model: String = "",
    val sdk: Int = 0,
)

@Serializable
data class HealthResponse(
    val backend: String = "ok",
    val lmStudio: LmStudioHealth = LmStudioHealth(),
    val deviceConnected: Boolean = false,
    val pairingCode: String = "",
    val hostAddresses: List<String> = emptyList(),
    val port: Int = 8787,
)

@Serializable
data class LmStudioHealth(
    val reachable: Boolean = false,
    val baseUrl: String = "",
    val model: String? = null,
    val error: String? = null,
    val provider: String = "",
)

@Serializable
data class PairingRequest(
    val code: String,
    val device: DeviceInfo,
)

@Serializable
data class PairingResponse(
    val token: String,
    val sessionId: String,
)

@Serializable
data class ChatRequest(
    val sessionId: String? = null,
    val message: String,
)

@Serializable
data class NodeResult(
    val success: Boolean,
    val data: JsonObject? = null,
    val error: ExecutionError? = null,
)
