package com.agent.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class ProtocolMessage {
    @Serializable
    @SerialName("chat.message")
    data class ChatUserMessage(
        val messageId: String,
        val sessionId: String? = null,
        val content: String,
        val imageUri: String? = null,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("assistant.message")
    data class AssistantMessage(
        val messageId: String,
        val content: String,
        val sessionId: String? = null,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("assistant.delta")
    data class AssistantDelta(
        val messageId: String,
        val delta: String,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("tool.execute")
    data class ToolExecute(
        val executionId: String,
        val toolId: String,
        val toolName: String = "",
        val inputs: JsonObject = JsonObject(emptyMap()),
    ) : ProtocolMessage()

    @Serializable
    @SerialName("execution.status")
    data class ExecutionStatusMsg(
        val executionId: String,
        val status: ExecutionStatus,
        val nodeId: String? = null,
        val nodeLabel: String? = null,
        val message: String? = null,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("execution.result")
    data class ExecutionResult(
        val executionId: String,
        val status: ExecutionStatus,
        val output: JsonObject? = null,
        val error: ExecutionError? = null,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("execution.cancel")
    data class ExecutionCancel(
        val executionId: String,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("approval.request")
    data class ApprovalRequest(
        val executionId: String,
        val toolId: String,
        val toolName: String,
        val description: String = "",
        val inputs: JsonObject = JsonObject(emptyMap()),
    ) : ProtocolMessage()

    @Serializable
    @SerialName("tools.sync")
    data class ToolsSync(
        val tools: List<ToolDefinition>,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("health")
    data class Health(
        val health: HealthResponse,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("error")
    data class ErrorMsg(
        val code: String,
        val message: String,
        val executionId: String? = null,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("connection.ack")
    data class ConnectionAck(
        val sessionId: String,
        val deviceId: String,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("copilot.observe")
    data class CopilotObserve(
        val turnId: String,
        val goal: String,
        val step: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val imageJpegBase64: String? = null,
        val screenTree: String = "",
    ) : ProtocolMessage()

    @Serializable
    @SerialName("copilot.plan")
    data class CopilotPlan(
        val turnId: String,
        val speak: String = "",
        val needNextScreenshot: Boolean = false,
        val done: Boolean = false,
        val actions: List<CopilotAction> = emptyList(),
        val error: String? = null,
    ) : ProtocolMessage()

    @Serializable
    @SerialName("copilot.stop")
    data class CopilotStop(
        val turnId: String? = null,
    ) : ProtocolMessage()
}

@Serializable
data class CopilotAction(
    val type: String,
    val x: Int? = null,
    val y: Int? = null,
    val direction: String? = null,
    val text: String? = null,
    val durationMs: Long? = null,
)
