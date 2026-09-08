package com.agent.mobile.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.mobile.core.AppContainer
import com.agent.shared.model.ExecutionPolicy
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToolsViewModel(private val container: AppContainer) : ViewModel() {
    val tools: StateFlow<List<ToolDefinition>> = container.toolRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleEnabled(tool: ToolDefinition) {
        viewModelScope.launch { container.toolRegistry.save(tool.copy(enabled = !tool.enabled)) }
    }

    fun setPolicy(tool: ToolDefinition, policy: ExecutionPolicy) {
        viewModelScope.launch { container.toolRegistry.save(tool.copy(executionPolicy = policy)) }
    }

    fun delete(tool: ToolDefinition) {
        if (tool.type == ToolType.SYSTEM) return
        viewModelScope.launch { container.toolRegistry.delete(tool.id) }
    }

    fun run(tool: ToolDefinition) {
        viewModelScope.launch { container.executionManager.runLocal(tool) }
    }
}
