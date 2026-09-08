package com.agent.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class InputParameter(
    val name: String,
    val type: String = "string",
    val description: String = "",
    val required: Boolean = true,
)

@Serializable
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val type: ToolType = ToolType.USER,
    val inputs: List<InputParameter> = emptyList(),
    val outputSchema: JsonObject? = null,
    val workflow: Workflow? = null,
    val executionPolicy: ExecutionPolicy = ExecutionPolicy.APPROVAL_REQUIRED,
    val permissions: List<String> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class ToolCall(
    val id: String,
    val toolName: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ToolResult(
    val success: Boolean,
    val output: JsonObject? = null,
    val error: ExecutionError? = null,
)

@Serializable
data class ExecutionError(
    val code: String,
    val message: String,
)
