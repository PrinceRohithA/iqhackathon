package com.agent.mobile.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.mobile.core.AppContainer
import com.agent.shared.model.ExecutionPolicy
import com.agent.shared.model.InputParameter
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolType
import com.agent.shared.model.Workflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ToolEditState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val policy: ExecutionPolicy = ExecutionPolicy.APPROVAL_REQUIRED,
    val inputs: List<InputParameter> = emptyList(),
    val errors: List<String> = emptyList(),
    val saved: Boolean = false,
    val isNew: Boolean = true,
    val nodeCount: Int = 0,
)

class ToolEditViewModel(
    private val container: AppContainer,
    private val toolId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolEditState(isNew = toolId == "new"))
    val state: StateFlow<ToolEditState> = _state.asStateFlow()

    init {
        if (toolId != "new") {
            viewModelScope.launch {
                val tool = container.toolRegistry.get(toolId) ?: return@launch
                _state.value = ToolEditState(
                    id = tool.id,
                    name = tool.name,
                    description = tool.description,
                    policy = tool.executionPolicy,
                    inputs = tool.inputs,
                    isNew = false,
                    nodeCount = tool.workflow?.nodes?.size ?: 0,
                )
            }
        }
    }

    fun updateName(value: String) { _state.value = _state.value.copy(name = value, saved = false) }
    fun updateDescription(value: String) { _state.value = _state.value.copy(description = value, saved = false) }
    fun updatePolicy(value: ExecutionPolicy) { _state.value = _state.value.copy(policy = value, saved = false) }

    fun addInput() {
        _state.value = _state.value.copy(
            inputs = _state.value.inputs + InputParameter(name = "input_${_state.value.inputs.size + 1}"),
            saved = false,
        )
    }

    fun updateInput(index: Int, input: InputParameter) {
        _state.value = _state.value.copy(
            inputs = _state.value.inputs.toMutableList().also { if (index in it.indices) it[index] = input },
            saved = false,
        )
    }

    fun removeInput(index: Int) {
        _state.value = _state.value.copy(inputs = _state.value.inputs.filterIndexed { i, _ -> i != index }, saved = false)
    }

    suspend fun save(): String? {
        val s = _state.value
        val existing = if (!s.isNew) container.toolRegistry.get(s.id) else null
        val name = s.name.trim().replace(Regex("\\s+"), "_")
        val tool = ToolDefinition(
            id = s.id,
            name = name,
            description = s.description.trim(),
            type = ToolType.USER,
            inputs = s.inputs.filter { it.name.isNotBlank() },
            workflow = existing?.workflow ?: Workflow(id = "wf_${s.id}", inputs = s.inputs),
            executionPolicy = s.policy,
            enabled = existing?.enabled ?: true,
        )
        if (s.name.isBlank() || s.description.isBlank()) {
            _state.value = s.copy(errors = listOf("Name and description are required"))
            return null
        }
        return try {
            container.toolRegistry.save(tool)
            _state.value = s.copy(
                name = name,
                saved = true,
                errors = emptyList(),
                isNew = false,
                nodeCount = tool.workflow?.nodes?.size ?: 0,
            )
            tool.id
        } catch (e: Exception) {
            _state.value = s.copy(errors = listOf("Save failed: ${e.message ?: "unknown error"}"))
            null
        }
    }
}
