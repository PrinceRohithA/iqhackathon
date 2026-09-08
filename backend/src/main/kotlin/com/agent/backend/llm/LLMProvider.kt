package com.agent.backend.llm

import com.agent.shared.model.ChatMessage
import kotlinx.serialization.json.JsonObject

data class LlmChatRequest(
    val messages: List<ChatMessage>,
    val tools: List<JsonObject> = emptyList(),
    val model: String? = null,
    val jsonMode: Boolean = false,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class LlmChatResponse(
    val content: String?,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val model: String? = null,
)

interface LLMProvider {
    suspend fun chat(request: LlmChatRequest): LlmChatResponse
    suspend fun health(): Pair<Boolean, String?>
}
