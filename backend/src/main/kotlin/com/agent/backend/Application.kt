package com.agent.backend

import com.agent.backend.agent.AgentRuntime
import com.agent.backend.agent.CopilotRuntime
import com.agent.backend.config.AppConfig
import com.agent.backend.devices.DeviceManager
import com.agent.backend.executions.ExecutionCoordinator
import com.agent.backend.llm.GeminiProvider
import com.agent.backend.persistence.initDatabase
import com.agent.backend.tools.ToolRegistry
import com.agent.shared.AgentJson
import com.agent.shared.model.ChatRequest
import com.agent.shared.model.HealthResponse
import com.agent.shared.model.LmStudioHealth
import com.agent.shared.model.PairingRequest
import com.agent.shared.model.PairingResponse
import com.agent.shared.model.ProtocolMessage
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.WorkflowExecution
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.File

fun main() {
    val config = AppConfig()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig()) {
    val log = LoggerFactory.getLogger("com.agent.backend")
    initDatabase(config.dbPath)
    val devices = DeviceManager()
    val tools = ToolRegistry()
    val executions = ExecutionCoordinator()
    val llm = GeminiProvider(config)
    log.info("LLM provider=Gemini model={} keySet={}", config.geminiModel, config.geminiApiKey.isNotBlank())
    if (config.geminiApiKey.isBlank()) {
        log.warn("GEMINI_API_KEY is missing. Put it in backend/.env or the GEMINI_API_KEY environment variable.")
    }
    val agent = AgentRuntime(llm, tools, devices, executions)
    val copilot = CopilotRuntime(llm, devices)

    install(ContentNegotiation) { json(AgentJson.instance) }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyMethod()
    }
    install(WebSockets)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ProtocolMessage.ErrorMsg("ERROR", cause.message ?: "Unknown error"),
            )
        }
    }

    println()
    println("Android Agent backend listening on ${config.host}:${config.port}")
    println("Pairing code: ${config.pairingCode}")
    config.localAddresses().forEach { ip ->
        println("  Connect from phone: http://$ip:${config.port}  code=${config.pairingCode}")
    }
    println("LLM: Gemini model=${config.geminiModel} key=${if (config.geminiApiKey.isBlank()) "MISSING" else "set"}")
    println()

    routing {
        get("/health") {
            val (ok, modelOrError) = runCatching { llm.health() }.getOrDefault(false to "health check failed")
            call.respond(
                HealthResponse(
                    backend = "ok",
                    lmStudio = LmStudioHealth(
                        reachable = ok,
                        baseUrl = config.geminiBaseUrl,
                        model = if (ok) modelOrError else config.geminiModel,
                        error = if (ok) null else modelOrError,
                        provider = "gemini",
                    ),
                    deviceConnected = devices.connected() != null,
                    pairingCode = config.pairingCode,
                    hostAddresses = config.localAddresses(),
                    port = config.port,
                ),
            )
        }

        get("/pair") {
            val apk = File(config.apkPath)
            val apkLink = if (apk.isFile) {
                """<p><a href="/app.apk" style="color:#5eead4">Download Android APK</a> (${apk.length() / 1024} KB)</p>"""
            } else {
                ""
            }
            val addresses = config.localAddresses().joinToString("<br>") { ip ->
                "http://$ip:${config.port} &nbsp; code <b>${config.pairingCode}</b>"
            }
            call.respondText(
                """
                <html><body style="font-family:sans-serif;background:#0b1220;color:#e5e7eb;padding:32px">
                <h1>Android Agent pairing</h1>
                <p>Enter this code in the Android app Settings screen.</p>
                <p style="font-size:48px;letter-spacing:8px">${config.pairingCode}</p>
                <p>Backend port ${config.port}</p>
                <p>$addresses</p>
                $apkLink
                </body></html>
                """.trimIndent(),
                ContentType.Text.Html,
            )
        }

        get("/app.apk") {
            val file = File(config.apkPath)
            if (!file.isFile) {
                call.respond(HttpStatusCode.NotFound, ProtocolMessage.ErrorMsg("NOT_FOUND", "APK not built yet"))
                return@get
            }
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "agent-debug.apk").toString(),
            )
            call.respondFile(file)
        }

        post("/session") {
            val req = call.receive<PairingRequest>()
            if (req.code.trim() != config.pairingCode) {
                log.warn("pairing rejected for device {}", req.device.deviceId)
                call.respond(HttpStatusCode.Unauthorized, ProtocolMessage.ErrorMsg("PAIRING", "Invalid pairing code"))
                return@post
            }
            val session = devices.pair(req.device)
            log.info("paired device={} name={} session={}", req.device.deviceId, req.device.name, session.sessionId)
            call.respond(PairingResponse(session.token, session.sessionId))
        }

        get("/tools") { call.respond(tools.enabled()) }

        post("/tools") {
            val tool = call.receive<ToolDefinition>()
            tools.upsert(tool)
            call.respond(tool)
        }

        put("/tools/{id}") {
            val tool = call.receive<ToolDefinition>()
            tools.upsert(tool)
            call.respond(tool)
        }

        delete("/tools/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            tools.delete(id)
            call.respond(buildJsonObject { put("ok", true) })
        }

        post("/tools/{id}/execute") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val session = devices.connected() ?: return@post call.respond(
                HttpStatusCode.Conflict,
                ProtocolMessage.ErrorMsg("NO_DEVICE", "No Android device connected"),
            )
            val tool = tools.get(id) ?: return@post call.respond(
                HttpStatusCode.NotFound,
                ProtocolMessage.ErrorMsg("UNKNOWN_TOOL", "Tool not found"),
            )
            val body = runCatching { AgentJson.instance.parseToJsonElement(call.receiveText()) as? kotlinx.serialization.json.JsonObject }
                .getOrNull() ?: kotlinx.serialization.json.JsonObject(emptyMap())
            val executionId = java.util.UUID.randomUUID().toString()
            devices.send(session, ProtocolMessage.ToolExecute(executionId, tool.id, tool.name, body))
            call.respond(buildJsonObject { put("executionId", executionId) })
        }

        get("/executions/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val execution = executions.get(id)
            if (execution == null) call.respond(HttpStatusCode.NotFound, ProtocolMessage.ErrorMsg("NOT_FOUND", "Unknown execution"))
            else call.respond(execution)
        }

        post("/chat") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
            val session = token?.let { devices.authenticate(it) } ?: devices.connected()
            if (session == null) {
                call.respond(HttpStatusCode.Unauthorized, ProtocolMessage.ErrorMsg("AUTH", "Device not paired"))
                return@post
            }
            val req = call.receive<ChatRequest>()
            call.application.launch { agent.handleUserMessage(session, req.message) }
            call.respond(buildJsonObject { put("ok", true); put("sessionId", session.sessionId) })
        }

        webSocket("/ws") {
            val token = call.request.queryParameters["token"].orEmpty()
            val session = devices.attach(token, this)
            if (session == null) {
                log.warn("ws auth failed tokenPrefix={}", token.take(8))
                send(Frame.Text(AgentJson.instance.encodeToString(ProtocolMessage.serializer(), ProtocolMessage.ErrorMsg("AUTH", "Invalid token"))))
                return@webSocket
            }
            log.info("ws connected session={} device={}", session.sessionId, session.device.deviceId)
            send(
                Frame.Text(
                    AgentJson.instance.encodeToString(
                        ProtocolMessage.serializer(),
                        ProtocolMessage.ConnectionAck(session.sessionId, session.device.deviceId),
                    ),
                ),
            )
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val raw = frame.readText()
                    val msg = runCatching {
                        AgentJson.instance.decodeFromString(ProtocolMessage.serializer(), raw)
                    }.onFailure { err ->
                        log.warn("ws decode failed chars={} err={} preview={}", raw.length, err.message, raw.take(200))
                    }.getOrNull() ?: continue
                    log.debug("ws inbound {} from {}", msg::class.simpleName, session.device.deviceId)
                    when (msg) {
                        is ProtocolMessage.ToolsSync -> {
                            log.info("tools.sync count={}", msg.tools.size)
                            tools.replaceDeviceTools(msg.tools)
                        }
                        is ProtocolMessage.ExecutionResult -> {
                            log.info("execution.result id={} status={}", msg.executionId, msg.status)
                            executions.track(
                                WorkflowExecutionLite(msg),
                            )
                            devices.complete(msg)
                        }
                        is ProtocolMessage.ExecutionStatusMsg -> executions.onStatus(msg)
                        is ProtocolMessage.ChatUserMessage -> {
                            log.info("chat.message session={} chars={}", session.sessionId, msg.content.length)
                            agent.handleUserMessage(session, msg.content)
                        }
                        is ProtocolMessage.CopilotObserve -> {
                            log.info("ws copilot.observe turn={} step={}", msg.turnId, msg.step)
                            call.application.launch { copilot.handleObserve(session, msg) }
                        }
                        is ProtocolMessage.CopilotStop -> log.info("ws copilot.stop turn={}", msg.turnId)
                        is ProtocolMessage.ExecutionCancel -> {
                            session.pending[msg.executionId]?.complete(
                                ProtocolMessage.ExecutionResult(
                                    msg.executionId,
                                    com.agent.shared.model.ExecutionStatus.CANCELLED,
                                    error = com.agent.shared.model.ExecutionError("CANCELLED", "Cancelled"),
                                ),
                            )
                        }
                        else -> Unit
                    }
                }
            } finally {
                log.info("ws disconnected session={} device={}", session.sessionId, session.device.deviceId)
                devices.detach(token)
            }
        }
    }
}

private fun WorkflowExecutionLite(msg: ProtocolMessage.ExecutionResult): com.agent.shared.model.WorkflowExecution =
    WorkflowExecution(
        executionId = msg.executionId,
        toolId = "",
        status = msg.status,
        result = com.agent.shared.model.ToolResult(
            success = msg.status == com.agent.shared.model.ExecutionStatus.COMPLETED,
            output = msg.output,
            error = msg.error,
        ),
        startedAt = System.currentTimeMillis(),
        finishedAt = System.currentTimeMillis(),
    )
