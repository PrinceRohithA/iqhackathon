package com.agent.backend.agent

import com.agent.backend.devices.DeviceManager
import com.agent.backend.devices.DeviceSession
import com.agent.backend.executions.ExecutionCoordinator
import com.agent.backend.llm.LLMProvider
import com.agent.backend.llm.LlmChatRequest
import com.agent.backend.persistence.Messages
import com.agent.backend.tools.ToolRegistry
import com.agent.shared.AgentJson
import com.agent.shared.model.ChatMessage
import com.agent.shared.model.ExecutionStatus
import com.agent.shared.model.ProtocolMessage
import com.agent.shared.model.WorkflowExecution
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import org.slf4j.LoggerFactory

class AgentRuntime(
    private val llm: LLMProvider,
    private val tools: ToolRegistry,
    private val devices: DeviceManager,
    private val executions: ExecutionCoordinator,
) {
    private val json = AgentJson.instance
    private val log = LoggerFactory.getLogger(AgentRuntime::class.java)

    suspend fun handleUserMessage(session: DeviceSession, content: String) {
        log.info("chat start session={} chars={}", session.sessionId, content.length)
        persist(session.sessionId, "user", content)
        val history = loadHistory(session.sessionId).toMutableList()
        if (history.none { it.role == "system" }) {
            history.add(0, ChatMessage(UUID.randomUUID().toString(), "system", SYSTEM_PROMPT, timestamp = System.currentTimeMillis()))
        }
        val openAiTools = tools.toOpenAiTools()
        var iterations = 0
        while (iterations++ < 8) {
            val response = try {
                llm.chat(LlmChatRequest(history, openAiTools))
            } catch (e: Exception) {
                val msg = "LLM error: ${e.message}"
                log.error("chat LLM error session={}: {}", session.sessionId, e.message, e)
                devices.send(session, ProtocolMessage.ErrorMsg("LLM_ERROR", msg))
                persist(session.sessionId, "assistant", msg)
                return
            }
            if (response.toolCalls.isEmpty()) {
                val text = response.content?.ifBlank { null } ?: "Done."
                persist(session.sessionId, "assistant", text)
                devices.send(session, ProtocolMessage.AssistantMessage(UUID.randomUUID().toString(), text, session.sessionId))
                log.info("chat done session={} replyChars={}", session.sessionId, text.length)
                return
            }
            log.info("chat toolCalls session={} names={}", session.sessionId, response.toolCalls.joinToString { it.name })
            val summary = response.content?.ifBlank { null } ?: "Using ${response.toolCalls.joinToString { it.name }}"
            devices.send(session, ProtocolMessage.AssistantMessage(UUID.randomUUID().toString(), summary, session.sessionId))
            history += ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = response.content.orEmpty(),
                toolCallsJson = JsonArray(
                    response.toolCalls.map { call ->
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(call.id),
                                "type" to JsonPrimitive("function"),
                                "function" to JsonObject(
                                    mapOf(
                                        "name" to JsonPrimitive(call.name),
                                        "arguments" to JsonPrimitive(call.argumentsJson),
                                    ),
                                ),
                            ),
                        )
                    },
                ).toString(),
                timestamp = System.currentTimeMillis(),
            )
            for (call in response.toolCalls) {
                val tool = tools.get(call.name)
                if (tool == null) {
                    val err = """{"success":false,"error":{"code":"UNKNOWN_TOOL","message":"Unknown tool ${call.name}"}}"""
                    history += ChatMessage(call.id, "tool", err, toolCallId = call.id, toolName = call.name, timestamp = System.currentTimeMillis())
                    continue
                }
                val args = parseArgs(call.argumentsJson)
                val executionId = UUID.randomUUID().toString()
                executions.track(
                    WorkflowExecution(
                        executionId = executionId,
                        toolId = tool.id,
                        toolName = tool.name,
                        status = ExecutionStatus.CREATED,
                        startedAt = System.currentTimeMillis(),
                    ),
                )
                val deferred = kotlinx.coroutines.CompletableDeferred<ProtocolMessage.ExecutionResult>()
                session.pending[executionId] = deferred
                devices.send(
                    session,
                    ProtocolMessage.ToolExecute(
                        executionId = executionId,
                        toolId = tool.id,
                        toolName = tool.name,
                        inputs = args,
                    ),
                )
                val result = try {
                    withTimeout(90_000) { deferred.await() }
                } catch (e: Exception) {
                    ProtocolMessage.ExecutionResult(
                        executionId,
                        ExecutionStatus.TIMEOUT,
                        error = com.agent.shared.model.ExecutionError("TIMEOUT", e.message ?: "Device did not return a result"),
                    )
                }
                session.pending.remove(executionId)
                val resultJson = json.encodeToString(ProtocolMessage.ExecutionResult.serializer(), result)
                history += ChatMessage(
                    id = call.id,
                    role = "tool",
                    content = resultJson,
                    toolCallId = call.id,
                    toolName = tool.name,
                    executionId = executionId,
                    timestamp = System.currentTimeMillis(),
                )
            }
        }
        val fallback = "Stopped after too many tool calls."
        persist(session.sessionId, "assistant", fallback)
        devices.send(session, ProtocolMessage.AssistantMessage(UUID.randomUUID().toString(), fallback, session.sessionId))
    }

    private fun parseArgs(raw: String): JsonObject {
        if (raw.isBlank()) return JsonObject(emptyMap())
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        return when (element) {
            is JsonObject -> element
            else -> JsonObject(mapOf("value" to JsonPrimitive(raw)))
        }
    }

    private fun persist(sessionId: String, role: String, content: String) {
        transaction {
            Messages.insert {
                it[id] = UUID.randomUUID().toString()
                it[Messages.sessionId] = sessionId
                it[Messages.role] = role
                it[Messages.content] = content
                it[createdAt] = System.currentTimeMillis()
            }
        }
    }

    private fun loadHistory(sessionId: String): MutableList<ChatMessage> = transaction {
        Messages.selectAll().where { Messages.sessionId eq sessionId }
            .map {
                ChatMessage(
                    id = it[Messages.id],
                    role = it[Messages.role],
                    content = it[Messages.content],
                    timestamp = it[Messages.createdAt],
                )
            }
            .toMutableList()
    }

    companion object {
        const val SYSTEM_PROMPT = """
You are an Android task agent.

Use available tools to complete the user's request.

Rules:
1. Select the tool that best matches the task.
2. Provide valid arguments.
3. Do not invent tools.
4. Never claim a task succeeded without a successful tool result.
5. If a tool fails, interpret the result and decide whether another action is appropriate.
6. Respect tool approval requirements — the device may pause for user approval.
7. Prefer user-defined tools when they match the request; otherwise use system tools such as open_app, take_screenshot, read_current_screen, and get_device_info.
"""
    }
}
