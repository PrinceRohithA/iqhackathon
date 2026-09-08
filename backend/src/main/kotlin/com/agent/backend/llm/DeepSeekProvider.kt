package com.agent.backend.llm

import com.agent.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory

class DeepSeekProvider(private val config: AppConfig) : LLMProvider {
    private val log = LoggerFactory.getLogger(DeepSeekProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
        }
    }

    private val baseUrl = config.deepseekBaseUrl.trimEnd('/')
    private val model = config.deepseekModel.ifBlank { "deepseek-chat" }

    override suspend fun chat(request: LlmChatRequest): LlmChatResponse {
        val key = config.deepseekApiKey
        if (key.isBlank()) error("DEEPSEEK_API_KEY is not set")
        val url = "$baseUrl/chat/completions"
        log.info(
            "deepseek POST {} model={} jsonMode={} messages={}",
            url,
            model,
            request.jsonMode,
            request.messages.joinToString { "${it.role}:${it.content.length}c" },
        )
        val body = buildJsonObject {
            put("model", request.model?.ifBlank { null } ?: model)
            put("temperature", 0.2)
            put("max_tokens", 2048)
            if (request.jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            putJsonArray("messages") {
                request.messages.forEach { msg ->
                    add(
                        buildJsonObject {
                            put("role", openaiRole(msg.role))
                            put("content", msg.content)
                            msg.toolCallId?.let { put("tool_call_id", it) }
                            msg.toolName?.let { put("name", it) }
                            val callsJson = msg.toolCallsJson
                            if (msg.role == "assistant" && !callsJson.isNullOrBlank()) {
                                put("tool_calls", json.parseToJsonElement(callsJson))
                            }
                        },
                    )
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") { request.tools.forEach { add(it) } }
                put("tool_choice", "auto")
            }
        }
        val response: JsonObject = client.post(url) {
            contentType(ContentType.Application.Json)
            bearerAuth(key)
            setBody(body)
        }.body()
        val error = response["error"]?.jsonObject
        if (error != null) {
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString()
            log.error("deepseek error: {}", message)
            error(message)
        }
        val choice = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("DeepSeek returned no choices")
        val message = choice["message"]?.jsonObject ?: JsonObject(emptyMap())
        val content = message["content"]?.jsonPrimitive?.contentOrNull
        val toolCalls = message["tool_calls"]?.jsonArray.orEmpty().mapNotNull { el ->
            val obj = el.jsonObject
            val fn = obj["function"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: java.util.UUID.randomUUID().toString(),
                name = fn["name"]?.jsonPrimitive?.content.orEmpty(),
                argumentsJson = fn["arguments"]?.jsonPrimitive?.contentOrNull
                    ?: fn["arguments"]?.toString().orEmpty(),
            )
        }
        log.info(
            "deepseek OK model={} contentChars={} toolCalls={}",
            model,
            content?.length ?: 0,
            toolCalls.joinToString { it.name }.ifBlank { "(none)" },
        )
        return LlmChatResponse(content = content, toolCalls = toolCalls, model = model)
    }

    override suspend fun health(): Pair<Boolean, String?> {
        val key = config.deepseekApiKey
        if (key.isBlank()) return false to "DEEPSEEK_API_KEY is not set"
        return try {
            val response: JsonObject = client.get("$baseUrl/models") {
                bearerAuth(key)
            }.body()
            val id = response["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            true to (id ?: model)
        } catch (e: Exception) {
            log.warn("deepseek health failed: {}", e.message)
            false to e.message
        }
    }

    private fun openaiRole(role: String): String = when (role) {
        "tool" -> "tool"
        "assistant" -> "assistant"
        "system" -> "system"
        else -> "user"
    }
}
