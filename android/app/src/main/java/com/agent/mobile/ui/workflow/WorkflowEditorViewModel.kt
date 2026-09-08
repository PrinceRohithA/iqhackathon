package com.agent.mobile.ui.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.mobile.automation.CapturedUiElement
import com.agent.mobile.automation.ElementPicker
import com.agent.mobile.core.AppContainer
import com.agent.mobile.workflow.WorkflowValidator
import com.agent.shared.model.NodeType
import com.agent.shared.model.Selector
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.Workflow
import com.agent.shared.model.WorkflowNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

data class WorkflowEditorState(
    val tool: ToolDefinition? = null,
    val nodes: List<WorkflowNode> = emptyList(),
    val selectedId: String? = null,
    val errors: List<String> = emptyList(),
    val saved: Boolean = false,
    val picking: Boolean = false,
    val capturedValue: String? = null,
    val indicateError: String? = null,
)

class WorkflowEditorViewModel(
    private val container: AppContainer,
    private val toolId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkflowEditorState())
    val state: StateFlow<WorkflowEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val tool = container.toolRegistry.get(toolId)
            if (tool == null) {
                _state.value = _state.value.copy(errors = listOf("Could not load this tool. Try opening it again."))
                return@launch
            }
            val current = _state.value
            if (current.tool == null) {
                _state.value = current.copy(
                    tool = tool,
                    nodes = current.nodes.ifEmpty { tool.workflow?.nodes.orEmpty() },
                )
            } else {
                _state.value = current.copy(tool = current.tool)
            }
            ElementPicker.consumePending(_state.value.selectedId)?.let { (nodeId, captured) ->
                applyCapture(captured, nodeId)
            }
        }
        viewModelScope.launch {
            ElementPicker.captures.collect { captured -> applyCapture(captured) }
        }
        viewModelScope.launch {
            ElementPicker.picking.collect { picking ->
                _state.value = _state.value.copy(picking = picking)
            }
        }
        viewModelScope.launch {
            ElementPicker.error.collect { err ->
                if (err != null) _state.value = _state.value.copy(indicateError = err, picking = false)
            }
        }
    }

    fun select(id: String?) { _state.value = _state.value.copy(selectedId = id) }

    fun indicate() {
        val node = _state.value.nodes.find { it.id == _state.value.selectedId } ?: return
        if (node.type !in SELECTOR_NODES) return
        _state.value = _state.value.copy(indicateError = null)
        if (!ElementPicker.start(node.id)) {
            _state.value = _state.value.copy(
                indicateError = ElementPicker.error.value ?: "Accessibility is not enabled",
            )
            return
        }
        viewModelScope.launch { persist() }
    }

    fun cancelIndicate() {
        ElementPicker.stop()
        _state.value = _state.value.copy(picking = false)
    }

    fun addNode(type: NodeType) {
        val node = defaultNode(type)
        _state.value = _state.value.copy(nodes = _state.value.nodes + node, selectedId = node.id, saved = false)
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        _state.value = _state.value.copy(nodes = _state.value.nodes.filterNot { it.id == id }, selectedId = null, saved = false)
    }

    fun move(delta: Int) {
        val id = _state.value.selectedId ?: return
        val list = _state.value.nodes.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        val node = list.removeAt(index)
        list.add(target, node)
        _state.value = _state.value.copy(nodes = list, saved = false)
    }

    fun updateSelected(params: Map<String, String>, selector: Selector? = null) {
        val id = _state.value.selectedId ?: return
        _state.value = _state.value.copy(
            nodes = _state.value.nodes.map { node ->
                if (node.id != id) node else nodeWith(node, params, selector)
            },
            saved = false,
        )
    }

    fun save(andThen: (() -> Unit)? = null) {
        viewModelScope.launch {
            persist()
            andThen?.invoke()
        }
    }

    private suspend fun persist(): Boolean {
        var tool = _state.value.tool ?: container.toolRegistry.get(toolId)
        if (tool == null) {
            _state.value = _state.value.copy(errors = listOf("Could not save: tool not found"), saved = false)
            return false
        }
        if (_state.value.tool == null) {
            _state.value = _state.value.copy(
                tool = tool,
                nodes = _state.value.nodes.ifEmpty { tool.workflow?.nodes.orEmpty() },
            )
        }
        tool = _state.value.tool ?: tool
        val workflow = (tool.workflow ?: Workflow(id = "wf_${tool.id}", inputs = tool.inputs)).copy(
            nodes = _state.value.nodes,
            inputs = tool.inputs,
        )
        val updated = tool.copy(workflow = workflow)
        val warnings = WorkflowValidator.validate(updated)
        return try {
            container.toolRegistry.save(updated)
            _state.value = _state.value.copy(tool = updated, errors = warnings, saved = true)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                errors = listOf("Save failed: ${e.message ?: "unknown error"}"),
                saved = false,
            )
            false
        }
    }

    private fun applyCapture(captured: CapturedUiElement, nodeId: String? = null) {
        val id = nodeId ?: _state.value.selectedId ?: ElementPicker.targetNodeId ?: return
        if (_state.value.nodes.none { it.id == id }) return
        val selector = captured.toSelector()
        _state.value = _state.value.copy(
            nodes = _state.value.nodes.map { node ->
                if (node.id != id) node else {
                    val params = node.scalarParams().toMutableMap()
                    params["capturedValue"] = captured.value
                    if (node.type == NodeType.READ_TEXT && params["name"].isNullOrBlank()) {
                        params["name"] = "text"
                    }
                    nodeWith(node, params, selector)
                }
            },
            selectedId = id,
            capturedValue = captured.value,
            picking = false,
            saved = false,
            indicateError = null,
        )
        ElementPicker.consumePending(id)
        viewModelScope.launch { persist() }
    }

    private fun nodeWith(node: WorkflowNode, params: Map<String, String>, selector: Selector?): WorkflowNode {
        val map = params
            .filterKeys { it != "selector" }
            .mapValues { JsonPrimitive(it.value) as kotlinx.serialization.json.JsonElement }
            .toMutableMap()
        val resolved = selector ?: node.selector()
        if (resolved != null) {
            map["selector"] = com.agent.shared.AgentJson.instance.encodeToJsonElement(Selector.serializer(), resolved)
        }
        return node.copy(params = JsonObject(map))
    }

    private fun defaultNode(type: NodeType): WorkflowNode {
        val id = UUID.randomUUID().toString()
        val params = when (type) {
            NodeType.LAUNCH_APP -> mapOf("appName" to "Instagram", "packageName" to "com.instagram.android")
            NodeType.FIND_ELEMENT -> mapOf("text" to "")
            NodeType.TYPE_TEXT -> mapOf("value" to "{{message}}")
            NodeType.READ_TEXT -> mapOf("name" to "text")
            NodeType.WAIT -> mapOf("durationMs" to "1000")
            NodeType.SWIPE -> mapOf("direction" to "up")
            NodeType.SET_VARIABLE -> mapOf("name" to "status", "value" to "ok")
            NodeType.RETURN_RESULT -> mapOf("name" to "status", "value" to "ok")
            NodeType.LOOP -> mapOf("times" to "2")
            NodeType.CONDITION -> mapOf("variable" to "status", "operator" to "eq", "value" to "ok")
            else -> emptyMap()
        }
        val selector = if (type in SELECTOR_NODES) Selector() else null
        val map = params.mapValues { JsonPrimitive(it.value) as kotlinx.serialization.json.JsonElement }.toMutableMap()
        if (selector != null) {
            map["selector"] = com.agent.shared.AgentJson.instance.encodeToJsonElement(Selector.serializer(), selector)
        }
        return WorkflowNode(id, type, JsonObject(map))
    }

    companion object {
        val SELECTOR_NODES = setOf(
            NodeType.FIND_ELEMENT,
            NodeType.TAP,
            NodeType.LONG_PRESS,
            NodeType.TYPE_TEXT,
            NodeType.READ_TEXT,
        )
    }
}
