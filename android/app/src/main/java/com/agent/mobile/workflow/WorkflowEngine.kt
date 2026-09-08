package com.agent.mobile.workflow

import com.agent.mobile.automation.AndroidAutomation
import com.agent.mobile.automation.UiElement
import com.agent.shared.model.AgentEvent
import com.agent.shared.model.ExecutionError
import com.agent.shared.model.ExecutionLogEntry
import com.agent.shared.model.NodeResult
import com.agent.shared.model.NodeType
import com.agent.shared.model.Selector
import com.agent.shared.model.SwipeGesture
import com.agent.shared.model.Workflow
import com.agent.shared.model.WorkflowNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

class WorkflowCancelledException : CancellationException("Workflow cancelled")

data class WorkflowRunResult(
    val result: NodeResult,
    val logs: List<ExecutionLogEntry>,
    val returnData: JsonObject?,
)

class WorkflowEngine(
    private val automation: AndroidAutomation,
) {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    suspend fun execute(
        workflow: Workflow,
        inputs: Map<String, String>,
        executionId: String = UUID.randomUUID().toString(),
        onEvent: (AgentEvent) -> Unit = {},
    ): WorkflowRunResult {
        cancelled = false
        val ctx = RuntimeContext(inputs.toMutableMap())
        val logs = mutableListOf<ExecutionLogEntry>()
        fun log(nodeId: String?, status: String, message: String, error: String? = null) {
            logs += ExecutionLogEntry(System.currentTimeMillis(), nodeId, status, message, error)
        }
        fun event(type: String, nodeId: String? = null, message: String? = null) {
            onEvent(AgentEvent(type, executionId, nodeId, message, System.currentTimeMillis()))
        }

        event("workflow.started", message = "Workflow ${workflow.id} started")
        log(null, "started", "Workflow started")
        return try {
            withTimeout(workflow.timeoutMs) {
                coroutineScope {
                    executeNodes(workflow.nodes, workflow, ctx, executionId, onEvent, ::log)
                    val merged = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
                        "success" to JsonPrimitive(true),
                    )
                    ctx.variables.forEach { (k, v) -> merged[k] = JsonPrimitive(v) }
                    ctx.returnData?.forEach { (k, v) -> merged[k] = v }
                    val data = JsonObject(merged)
                    event("workflow.completed", message = "Workflow completed")
                    log(null, "completed", "Workflow completed")
                    WorkflowRunResult(NodeResult(true, data), logs, data)
                }
            }
        } catch (e: WorkflowCancelledException) {
            event("workflow.failed", message = "Cancelled")
            log(null, "cancelled", "Workflow cancelled")
            WorkflowRunResult(
                NodeResult(false, error = ExecutionError("CANCELLED", "Workflow cancelled")),
                logs,
                null,
            )
        } catch (e: TimeoutCancellationException) {
            event("workflow.failed", message = "Timeout")
            log(null, "timeout", "Workflow exceeded ${workflow.timeoutMs}ms")
            WorkflowRunResult(
                NodeResult(false, error = ExecutionError("TIMEOUT", "Workflow exceeded ${workflow.timeoutMs}ms")),
                logs,
                null,
            )
        } catch (e: Exception) {
            val code = if (e.message?.contains("not found", true) == true) "ELEMENT_NOT_FOUND" else "FAILED"
            event("workflow.failed", message = e.message)
            log(null, "failed", e.message ?: "failed", e.message)
            WorkflowRunResult(
                NodeResult(false, error = ExecutionError(code, e.message ?: "Workflow failed")),
                logs,
                null,
            )
        }
    }

    private suspend fun executeNodes(
        nodes: List<WorkflowNode>,
        workflow: Workflow,
        ctx: RuntimeContext,
        executionId: String,
        onEvent: (AgentEvent) -> Unit,
        log: (String?, String, String, String?) -> Unit,
    ) {
        var i = 0
        while (i < nodes.size) {
            if (cancelled) throw WorkflowCancelledException()
            val node = nodes[i]
            when (node.type) {
                NodeType.CONDITION -> {
                    val ok = ctx.matches(node.stringParam("variable"), node.stringParam("operator", "eq"), interpolate(node.stringParam("value"), ctx))
                    i = if (ok) {
                        if (node.children.isNotEmpty()) {
                            executeNodes(node.children, workflow, ctx, executionId, onEvent, log)
                        }
                        i + 1
                    } else {
                        i + 1 + if (node.children.isEmpty()) 1 else 0
                    }
                }
                NodeType.LOOP -> {
                    val times = node.intParam("times", 1).coerceIn(1, 50)
                    val body = node.children.ifEmpty { nodes.drop(i + 1).take(1) }
                    repeat(times) {
                        executeNodes(body, workflow, ctx, executionId, onEvent, log)
                    }
                    i += if (node.children.isEmpty()) 2 else 1
                }
                else -> {
                    executeOne(node, workflow, ctx, executionId, onEvent, log)
                    i++
                }
            }
        }
    }

    private suspend fun executeOne(
        node: WorkflowNode,
        workflow: Workflow,
        ctx: RuntimeContext,
        executionId: String,
        onEvent: (AgentEvent) -> Unit,
        log: (String?, String, String, String?) -> Unit,
    ) {
        onEvent(AgentEvent("node.started", executionId, node.id, node.label(), System.currentTimeMillis()))
        log(node.id, "started", node.label(), null)
        try {
            withTimeout(workflow.nodeTimeoutMs) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                when (node.type) {
                    NodeType.LAUNCH_APP -> automation.launchApp(
                        interpolate(node.stringParam("packageName"), ctx).ifBlank { null },
                        interpolate(node.stringParam("appName"), ctx).ifBlank { null },
                    )
                    NodeType.FIND_ELEMENT -> {
                        val selector = resolvedSelector(node, ctx)
                        val found = automation.find(selector)
                            ?: error("Could not find ${selector.describe()}")
                        ctx.lastElement = found
                        ctx.variables["lastText"] = found.text
                    }
                    NodeType.TAP -> {
                        val element = target(node, ctx)
                        automation.tap(element)
                    }
                    NodeType.LONG_PRESS -> automation.longPress(target(node, ctx))
                    NodeType.SWIPE -> automation.swipe(
                        SwipeGesture(interpolate(node.stringParam("direction", "up"), ctx)),
                    )
                    NodeType.TYPE_TEXT -> {
                        val element = runCatching { target(node, ctx) }.getOrNull() ?: ctx.lastElement
                            ?: error("No element focused for typing")
                        automation.type(element, interpolate(node.stringParam("value"), ctx))
                    }
                    NodeType.READ_TEXT -> {
                        val element = runCatching { target(node, ctx) }.getOrNull() ?: ctx.lastElement
                            ?: error("Could not find element to read")
                        val text = automation.readText(element)
                        val name = node.stringParam("name", "text").ifBlank { "text" }
                        ctx.variables[name] = text
                        ctx.variables["lastText"] = text
                        ctx.mergeReturn(name, text)
                    }
                    NodeType.BACK -> automation.back()
                    NodeType.WAIT -> kotlinx.coroutines.delay(node.intParam("durationMs", 1000).toLong())
                    NodeType.SET_VARIABLE -> ctx.variables[node.stringParam("name")] =
                        interpolate(node.stringParam("value"), ctx)
                    NodeType.INPUT_PARAMETER -> {
                        val name = node.stringParam("name")
                        ctx.variables[name] = ctx.inputs[name] ?: ctx.variables[name].orEmpty()
                    }
                    NodeType.RETURN_RESULT -> {
                        val key = node.stringParam("name", "status")
                        val value = interpolate(node.stringParam("value", "ok"), ctx)
                        ctx.returnData = JsonObject(mapOf(key to JsonPrimitive(value), "success" to JsonPrimitive(true)))
                    }
                    NodeType.SCREENSHOT -> automation.screenshot()
                    NodeType.CONDITION, NodeType.LOOP -> Unit
                }
            }
            onEvent(AgentEvent("node.completed", executionId, node.id, node.label(), System.currentTimeMillis()))
            log(node.id, "completed", node.label(), null)
        } catch (e: WorkflowCancelledException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            onEvent(AgentEvent("node.failed", executionId, node.id, "timeout", System.currentTimeMillis()))
            log(node.id, "timeout", node.label(), "Node exceeded ${workflow.nodeTimeoutMs}ms")
            throw e
        } catch (e: Exception) {
            onEvent(AgentEvent("node.failed", executionId, node.id, e.message, System.currentTimeMillis()))
            log(node.id, "failed", node.label(), e.message)
            throw e
        }
    }

    private suspend fun target(node: WorkflowNode, ctx: RuntimeContext): UiElement {
        val selector = node.selector()
        if (selector != null && !selector.isEmpty()) {
            val found = automation.find(resolvedSelector(node, ctx)) ?: error("Could not find ${selector.describe()}")
            ctx.lastElement = found
            return found
        }
        return ctx.lastElement ?: error("No element selected. Add a Find Element node first.")
    }

    private fun resolvedSelector(node: WorkflowNode, ctx: RuntimeContext): Selector {
        val raw = node.selector() ?: Selector(text = interpolate(node.stringParam("text"), ctx).ifBlank { null })
        return raw.copy(
            resourceId = raw.resourceId?.let { interpolate(it, ctx) },
            text = raw.text?.let { interpolate(it, ctx) },
            contentDescription = raw.contentDescription?.let { interpolate(it, ctx) },
            className = raw.className,
            index = raw.index,
            x = raw.x,
            y = raw.y,
        )
    }

    private fun interpolate(template: String, ctx: RuntimeContext): String {
        var result = template
        val pattern = Regex("\\{\\{([^}]+)}}")
        result = pattern.replace(result) { match ->
            val key = match.groupValues[1].trim()
            ctx.inputs[key] ?: ctx.variables[key] ?: ""
        }
        return result
    }
}

class RuntimeContext(
    val inputs: MutableMap<String, String>,
    val variables: MutableMap<String, String> = mutableMapOf(),
    var lastElement: UiElement? = null,
    var returnData: JsonObject? = null,
) {
    fun mergeReturn(key: String, value: String) {
        val merged = returnData?.toMutableMap() ?: mutableMapOf()
        merged[key] = JsonPrimitive(value)
        merged["success"] = JsonPrimitive(true)
        returnData = JsonObject(merged)
    }

    fun matches(variable: String, operator: String, expected: String): Boolean {
        val actual = variables[variable] ?: inputs[variable].orEmpty()
        return when (operator.lowercase()) {
            "neq", "ne", "!=" -> actual != expected
            "contains" -> actual.contains(expected, ignoreCase = true)
            "empty" -> actual.isBlank()
            else -> actual == expected
        }
    }
}
