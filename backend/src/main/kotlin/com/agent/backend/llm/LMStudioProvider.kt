package com.agent.backend.llm

import com.agent.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory

class LMStudioProvider(private val config: AppConfig) : LLMProvider {
    private val log = LoggerFactory.getLogger(LMStudioProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 10_000
        }
    }

    @Volatile
    private var skipImages = false

    override suspend fun chat(request: LlmChatRequest): LlmChatResponse {
        val model = request.model?.ifBlank { null } ?: config.lmStudioModel.ifBlank { null } ?: resolveModel()
        val hasImages = request.messages.any { !it.imageJpegBase64.isNullOrBlank() }
        val tryImages = hasImages && !skipImages
        if (hasImages && skipImages) {
            log.warn(
                "screenshot is present on the request but NOT sent to LM Studio: model {} does not support image inputs. Load a vision model (Qwen2-VL, LLaVA, Gemma 3) in LM Studio.",
                model,
            )
        }
        return try {
            postChat(request, model, includeImages = tryImages)
        } catch (e: Exception) {
            val visionFail = e.message.orEmpty().contains("image", ignoreCase = true)
            if (!tryImages || !hasImages) throw e
            if (visionFail) skipImages = true
            log.error(
                "screenshot WAS sent to LM Studio but {} cannot view images ({}). Retrying without the image. Load a vision model to use screenshots.",
                model,
                e.message,
            )
            postChat(request, model, includeImages = false)
        }
    }

    private suspend fun postChat(request: LlmChatRequest, model: String, includeImages: Boolean): LlmChatResponse {
        val imageCount = request.messages.count { !it.imageJpegBase64.isNullOrBlank() }
        val url = "${config.lmStudioBaseUrl.trimEnd('/')}/chat/completions"
        log.debug(
            "lmstudio POST {} model={} imagesAttached={} tools={} messages={}",
            url,
            model,
            if (includeImages) imageCount else 0,
            request.tools.size,
            request.messages.joinToString { "${it.role}:${it.content.length}c" },
        )
        if (includeImages && imageCount > 0) {
            val kb = request.messages.mapNotNull { it.imageJpegBase64 }.sumOf { it.length } * 3 / 4 / 1024
            log.info("lmstudio attaching {} screenshot(s) (~{}KB) to model {}", imageCount, kb, model)
        }
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put("max_tokens", 2048)
            putJsonArray("messages") {
                request.messages.forEach { msg ->
                    add(
                        buildJsonObject {
                            put("role", openaiRole(msg.role))
                            val jpeg = msg.imageJpegBase64
                            if (includeImages && !jpeg.isNullOrBlank() && msg.role == "user") {
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "text")
                                                put("text", msg.content)
                                            },
                                        )
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
        val response: JsonObject = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        val error = response["error"]?.jsonObject
        if (error != null) {
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString()
            log.error("lmstudio error model={} includeImages={}: {}", model, includeImages, message)
            error(message)
        }
        val choice = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("LM Studio returned no choices")
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
        log.debug(
            "lmstudio OK model={} includeImages={} contentChars={} toolCalls={}",
            model,
            includeImages,
            content?.length ?: 0,
            toolCalls.joinToString { it.name }.ifBlank { "(none)" },
        )
        return LlmChatResponse(content = content, toolCalls = toolCalls, model = model)
    }

    override suspend fun health(): Pair<Boolean, String?> {
        return try {
            val response: JsonObject = client.get("${config.lmStudioBaseUrl.trimEnd('/')}/models").body()
            val model = response["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            true to model
        } catch (e: Exception) {
            false to e.message
        }
    }

    private suspend fun resolveModel(): String {
        val (_, model) = health()
        return model ?: "local-model"
    }

    private fun openaiRole(role: String): String = when (role) {
        "tool" -> "tool"
        "assistant" -> "assistant"
        "system" -> "system"
        else -> "user"
    }
}

@Serializable
private data class ModelsResponse(val data: List<ModelCard> = emptyList())

@Serializable
private data class ModelCard(val id: String, @SerialName("object") val obj: String? = null)
