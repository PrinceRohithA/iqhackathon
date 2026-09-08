package com.agent.backend.llm

import com.agent.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory

class GeminiProvider(private val config: AppConfig) : LLMProvider {
    private val log = LoggerFactory.getLogger(GeminiProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    private val openaiBaseUrl = config.geminiBaseUrl.trimEnd('/')
    private val nativeBaseUrl = "https://generativelanguage.googleapis.com/v1beta"
    private val preferredModel = config.geminiModel.ifBlank { "gemini-3.1-flash-lite" }
    private val fallbackModels = listOf(
        preferredModel,
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash-lite",
    ).distinct()

    override suspend fun chat(request: LlmChatRequest): LlmChatResponse {
        val key = config.geminiApiKey
        if (key.isBlank()) error("GEMINI_API_KEY is not set")
        val imageCount = request.messages.count { !it.imageJpegBase64.isNullOrBlank() }
        log.info(
            "gemini chat model={} images={} jsonMode={} tools={} messages={}",
            preferredModel,
            imageCount,
            request.jsonMode,
            request.tools.size,
            request.messages.joinToString { "${it.role}:${it.content.length}c" },
        )
        var lastError: String? = null
        for (model in fallbackModels) {
            try {
                return chatOpenAi(request, key, model)
            } catch (e: Exception) {
                lastError = e.message
                log.warn("gemini openai-compat failed model={}: {}", model, e.message)
                if (isAuthError(e.message)) break
            }
            try {
                return chatNative(request, key, model)
            } catch (e: Exception) {
                lastError = e.message
                log.warn("gemini native failed model={}: {}", model, e.message)
                if (isAuthError(e.message)) break
            }
        }
        error(lastError ?: "Gemini request failed")
    }

    override suspend fun health(): Pair<Boolean, String?> {
        val key = config.geminiApiKey
        if (key.isBlank()) return false to "GEMINI_API_KEY is not set"
        return try {
            val response = client.get("$nativeBaseUrl/models") {
                header("x-goog-api-key", key)
                parameter("key", key)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return false to geminiErrorMessage(text).ifBlank { "HTTP ${response.status.value}" }
            }
            val names = json.parseToJsonElement(text).jsonObject["models"]?.jsonArray.orEmpty().mapNotNull { el ->
                el.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.substringAfter("models/")
            }
            val match = fallbackModels.firstOrNull { candidate -> names.any { it == candidate } } ?: names.firstOrNull()
            true to (match ?: preferredModel)
        } catch (e: Exception) {
            log.warn("gemini health failed: {}", e.message)
            false to e.message
        }
    }

    private suspend fun chatOpenAi(request: LlmChatRequest, key: String, model: String): LlmChatResponse {
        val url = "$openaiBaseUrl/chat/completions"
        val body = buildJsonObject {
            put("model", model)
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
                            val jpeg = msg.imageJpegBase64
                            if (!jpeg.isNullOrBlank() && msg.role == "user") {
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(buildJsonObject { put("type", "text"); put("text", msg.content) })
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put("url", "data:image/jpeg;base64,$jpeg")
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            } else {
                                put("content", msg.content)
                            }
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
        val http = client.post(url) {
            contentType(ContentType.Application.Json)
            bearerAuth(key)
            header("x-goog-api-key", key)
            setBody(body)
        }
        val text = http.bodyAsText()
        if (!http.status.isSuccess()) {
            error("Gemini OpenAI HTTP ${http.status.value}: ${geminiErrorMessage(text)}")
        }
        val response = json.parseToJsonElement(text).jsonObject
        response["error"]?.jsonObject?.let { err ->
            error(err["message"]?.jsonPrimitive?.contentOrNull ?: err.toString())
        }
        val choice = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("Gemini returned no choices")
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
            "gemini OK via openai model={} contentChars={} toolCalls={}",
            model,
            content?.length ?: 0,
            toolCalls.joinToString { it.name }.ifBlank { "(none)" },
        )
        return LlmChatResponse(content = content, toolCalls = toolCalls, model = model)
    }

    private suspend fun chatNative(request: LlmChatRequest, key: String, model: String): LlmChatResponse {
        val url = "$nativeBaseUrl/models/$model:generateContent"
        val systemText = request.messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = buildJsonObject {
            if (systemText.isNotBlank()) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        putJsonArray("parts") { add(buildJsonObject { put("text", systemText) }) }
                    },
                )
            }
            putJsonArray("contents") {
                request.messages.filter { it.role != "system" }.forEach { msg ->
                    add(
                        buildJsonObject {
                            put("role", if (msg.role == "assistant") "model" else "user")
                            putJsonArray("parts") {
                                if (msg.role == "tool") {
                                    add(
                                        buildJsonObject {
                                            put(
                                                "functionResponse",
                                                buildJsonObject {
                                                    put("name", msg.toolName ?: "tool")
                                                    put("response", buildJsonObject { put("result", msg.content) })
                                                },
                                            )
                                        },
                                    )
                                } else {
                                    add(buildJsonObject { put("text", msg.content.ifBlank { " " }) })
                                    val jpeg = msg.imageJpegBase64
                                    if (!jpeg.isNullOrBlank() && msg.role == "user") {
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "inlineData",
                                                    buildJsonObject {
                                                        put("mimeType", "image/jpeg")
                                                        put("data", jpeg)
                                                    },
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    add(
                        buildJsonObject {
                            putJsonArray("functionDeclarations") {
                                request.tools.forEach { tool ->
                                    val fn = tool["function"]?.jsonObject ?: return@forEach
                                    add(
                                        buildJsonObject {
                                            put("name", fn["name"]?.jsonPrimitive?.content.orEmpty())
                                            fn["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
                                            fn["parameters"]?.let { put("parameters", it) }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 2048)
                    if (request.jsonMode) put("responseMimeType", "application/json")
                },
            )
        }
        val http = client.post(url) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", key)
            parameter("key", key)
            setBody(body)
        }
        val text = http.bodyAsText()
        if (!http.status.isSuccess()) {
            error("Gemini native HTTP ${http.status.value}: ${geminiErrorMessage(text)}")
        }
        val response = json.parseToJsonElement(text).jsonObject
        response["error"]?.jsonObject?.let { err ->
            error(err["message"]?.jsonPrimitive?.contentOrNull ?: err.toString())
        }
        val parts = response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val content = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("").ifBlank { null }
        val toolCalls = parts.mapNotNull { el ->
            val call = el.jsonObject["functionCall"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = java.util.UUID.randomUUID().toString(),
                name = call["name"]?.jsonPrimitive?.content.orEmpty(),
                argumentsJson = call["args"]?.toString() ?: "{}",
            )
        }
        log.info(
            "gemini OK via native model={} contentChars={} toolCalls={}",
            model,
            content?.length ?: 0,
            toolCalls.joinToString { it.name }.ifBlank { "(none)" },
        )
        return LlmChatResponse(content = content, toolCalls = toolCalls, model = model)
    }

    private fun geminiErrorMessage(text: String): String {
        val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return text.take(400)
        return parsed["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: parsed["message"]?.jsonPrimitive?.contentOrNull
            ?: text.take(400)
    }

    private fun isAuthError(message: String?): Boolean {
        val text = message.orEmpty().lowercase()
        return "api key" in text || "unauthenticated" in text || "401" in text || "403" in text || "permission" in text
    }

    private fun openaiRole(role: String): String = when (role) {
        "tool" -> "tool"
        "assistant" -> "assistant"
        "system" -> "system"
        else -> "user"
    }
}
