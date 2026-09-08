package com.agent.mobile.execution

import com.agent.mobile.agent.AgentClient
import com.agent.mobile.agent.ChatStore
import com.agent.mobile.data.ExecutionRepository
import com.agent.mobile.tools.SystemToolExecutor
import com.agent.mobile.tools.ToolRegistry
import com.agent.mobile.workflow.WorkflowEngine
import com.agent.shared.model.ExecutionLogEntry
import com.agent.shared.model.ExecutionNodeState
import com.agent.shared.model.ExecutionPolicy
import com.agent.shared.model.ExecutionStatus
import com.agent.shared.model.ProtocolMessage
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolResult
import com.agent.shared.model.ToolType
import com.agent.shared.model.WorkflowExecution
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class PendingApproval(
    val executionId: String,
    val tool: ToolDefinition,
    val inputs: Map<String, String>,
)

class ExecutionManager(
    private val toolRegistry: ToolRegistry,
    private val workflowEngine: WorkflowEngine,
    private val systemTools: SystemToolExecutor,
    private val executionRepository: ExecutionRepository,
    private val agentClient: AgentClient,
    private val chatStore: ChatStore,
    private val scope: CoroutineScope,
) {
    private val _current = MutableStateFlow<WorkflowExecution?>(null)
    val current: StateFlow<WorkflowExecution?> = _current.asStateFlow()

    private val _pending = MutableStateFlow<PendingApproval?>(null)
    val pending: StateFlow<PendingApproval?> = _pending.asStateFlow()

    private val approvals = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private var runningJob: Job? = null

    fun start() {
        scope.launch {
            agentClient.incoming.collect { msg ->
                when (msg) {
                    is ProtocolMessage.ToolExecute -> handleToolExecute(msg)
                    is ProtocolMessage.ExecutionCancel -> cancel(msg.executionId)
                    is ProtocolMessage.AssistantMessage -> chatStore.addAssistant(msg.content, msg.messageId)
                    is ProtocolMessage.AssistantDelta -> chatStore.appendAssistantDelta(msg.messageId, msg.delta)
                    is ProtocolMessage.ErrorMsg -> chatStore.addAssistant(msg.message)
                    is ProtocolMessage.ConnectionAck -> Unit
                    else -> Unit
                }
            }
        }
        scope.launch {
            toolRegistry.observe().collect { tools ->
                if (agentClient.connection.value.name == "CONNECTED") {
                    agentClient.syncTools(tools.filter { it.enabled })
                }
            }
        }
        scope.launch {
            agentClient.connection.collect { state ->
                if (state == com.agent.shared.model.ConnectionState.CONNECTED) {
                    agentClient.syncTools(toolRegistry.enabled())
                }
            }
        }
    }

    fun approve(executionId: String) {
        approvals[executionId]?.complete(true)
        _pending.value = _pending.value?.takeIf { it.executionId != executionId }
    }

    fun reject(executionId: String) {
        approvals[executionId]?.complete(false)
        _pending.value = _pending.value?.takeIf { it.executionId != executionId }
    }

    fun cancel(executionId: String? = _current.value?.executionId) {
        workflowEngine.cancel()
        runningJob?.cancel()
        val current = _current.value ?: return
        if (executionId != null && current.executionId != executionId) return
        val updated = current.copy(status = ExecutionStatus.CANCELLED, finishedAt = System.currentTimeMillis())
        _current.value = updated
        agentClient.send(
            ProtocolMessage.ExecutionResult(
                executionId = current.executionId,
                status = ExecutionStatus.CANCELLED,
                error = com.agent.shared.model.ExecutionError("CANCELLED", "User stopped execution"),
            ),
        )
        scope.launch { executionRepository.save(updated) }
    }

    suspend fun runLocal(tool: ToolDefinition, inputs: Map<String, String> = emptyMap()): WorkflowExecution {
        val executionId = UUID.randomUUID().toString()
        return executeTool(executionId, tool, inputs, notifyBackend = false)
    }

    private fun handleToolExecute(msg: ProtocolMessage.ToolExecute) {
        scope.launch {
            val tool = toolRegistry.get(msg.toolId) ?: toolRegistry.get(msg.toolName)
            if (tool == null) {
                agentClient.send(
                    ProtocolMessage.ExecutionResult(
                        executionId = msg.executionId,
                        status = ExecutionStatus.FAILED,
                        error = com.agent.shared.model.ExecutionError("UNKNOWN_TOOL", "Tool not found: ${msg.toolId}"),
                    ),
                )
                return@launch
            }
            val inputs = msg.inputs.entries.associate { it.key to (it.value.jsonPrimitive.contentOrNull ?: it.value.toString()) }
            executeTool(msg.executionId, tool, inputs, notifyBackend = true)
        }
    }

    private suspend fun executeTool(
        executionId: String,
        tool: ToolDefinition,
        inputs: Map<String, String>,
        notifyBackend: Boolean,
    ): WorkflowExecution {
        val nodeStates = tool.workflow?.nodes?.map {
            ExecutionNodeState(it.id, it.label())
        }.orEmpty()
        var state = WorkflowExecution(
            executionId = executionId,
            toolId = tool.id,
            toolName = tool.name,
            status = ExecutionStatus.CREATED,
            nodes = nodeStates,
            startedAt = System.currentTimeMillis(),
        )
        _current.value = state
        chatStore.addTool(tool.name, "Using ${tool.name}", executionId)

        if (tool.executionPolicy == ExecutionPolicy.APPROVAL_REQUIRED) {
            state = state.copy(status = ExecutionStatus.WAITING_APPROVAL)
            _current.value = state
            _pending.value = PendingApproval(executionId, tool, inputs)
            val deferred = CompletableDeferred<Boolean>()
            approvals[executionId] = deferred
            if (notifyBackend) {
                agentClient.send(ProtocolMessage.ExecutionStatusMsg(executionId, ExecutionStatus.WAITING_APPROVAL, message = "Waiting for approval"))
            }
            val approved = deferred.await()
            approvals.remove(executionId)
            _pending.value = null
            if (!approved) {
                state = state.copy(status = ExecutionStatus.CANCELLED, finishedAt = System.currentTimeMillis())
                _current.value = state
                if (notifyBackend) {
                    agentClient.send(
                        ProtocolMessage.ExecutionResult(
                            executionId,
                            ExecutionStatus.CANCELLED,
                            error = com.agent.shared.model.ExecutionError("CANCELLED", "User rejected ${tool.name}"),
                        ),
                    )
                }
                executionRepository.save(state)
                return state
            }
        }

        state = state.copy(status = ExecutionStatus.RUNNING)
        _current.value = state
        if (notifyBackend) {
            agentClient.send(ProtocolMessage.ExecutionStatusMsg(executionId, ExecutionStatus.RUNNING, message = "Running"))
        }

        val result: ToolResult
        val logs: List<ExecutionLogEntry>
        if (tool.type == ToolType.SYSTEM) {
            result = systemTools.execute(tool.name, inputs)
            logs = listOf(
                ExecutionLogEntry(System.currentTimeMillis(), null, if (result.success) "completed" else "failed", tool.name, result.error?.message),
            )
        } else {
            val workflow = tool.workflow
                ?: return fail(state, "NO_WORKFLOW", "Tool has no workflow", notifyBackend)
            val job = scope.launch {
                // placeholder so cancel() can cancel engine via flag
            }
            runningJob = job
            val run = workflowEngine.execute(workflow, inputs, executionId) { event ->
                val status = when (event.type) {
                    "node.started" -> ExecutionStatus.RUNNING
                    "node.completed" -> ExecutionStatus.COMPLETED
                    "node.failed" -> ExecutionStatus.FAILED
                    else -> ExecutionStatus.RUNNING
                }
                val nodes = _current.value?.nodes.orEmpty().map { node ->
                    if (node.nodeId == event.nodeId) node.copy(status = status) else node
                }
                _current.value = _current.value?.copy(
                    currentNodeId = event.nodeId,
                    currentNodeLabel = event.message,
                    nodes = nodes,
                    status = ExecutionStatus.RUNNING,
                )
                if (notifyBackend) {
                    agentClient.send(
                        ProtocolMessage.ExecutionStatusMsg(
                            executionId,
                            ExecutionStatus.RUNNING,
                            event.nodeId,
                            event.message,
                            event.message,
                        ),
                    )
                }
            }
            runningJob = null
            result = ToolResult(run.result.success, run.result.data ?: run.returnData, run.result.error)
            logs = run.logs
        }

        val finalStatus = when {
            result.success -> ExecutionStatus.COMPLETED
            result.error?.code == "TIMEOUT" -> ExecutionStatus.TIMEOUT
            result.error?.code == "CANCELLED" -> ExecutionStatus.CANCELLED
            else -> ExecutionStatus.FAILED
        }
        state = (_current.value ?: state).copy(
            status = finalStatus,
            result = result,
            logs = logs,
            finishedAt = System.currentTimeMillis(),
        )
        _current.value = state
        executionRepository.save(state)
        chatStore.addTool(
            tool.name,
            if (result.success) "Completed ${tool.name}" else "Failed: ${result.error?.message}",
            executionId,
        )
        if (notifyBackend) {
            agentClient.send(
                ProtocolMessage.ExecutionResult(
                    executionId = executionId,
                    status = finalStatus,
                    output = result.output ?: JsonObject(mapOf("success" to JsonPrimitive(result.success))),
                    error = result.error,
                ),
            )
        }
        return state
    }

    private suspend fun fail(
        state: WorkflowExecution,
        code: String,
        message: String,
        notifyBackend: Boolean,
    ): WorkflowExecution {
        val updated = state.copy(
            status = ExecutionStatus.FAILED,
            result = ToolResult(false, error = com.agent.shared.model.ExecutionError(code, message)),
            finishedAt = System.currentTimeMillis(),
        )
        _current.value = updated
        if (notifyBackend) {
            agentClient.send(ProtocolMessage.ExecutionResult(state.executionId, ExecutionStatus.FAILED, error = updated.result?.error))
        }
        executionRepository.save(updated)
        return updated
    }
}
