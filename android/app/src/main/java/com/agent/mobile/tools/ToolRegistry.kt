package com.agent.mobile.tools

import com.agent.mobile.data.ToolRepository
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolType
import com.agent.shared.tools.SystemToolCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ToolRegistry(private val repository: ToolRepository) {
    fun observe(): Flow<List<ToolDefinition>> = repository.observeAll()

    suspend fun ensureSystemTools() {
        val existing = repository.getAll().associateBy { it.id }
        SystemToolCatalog.all.forEach { system ->
            val current = existing[system.id]
            if (current == null) {
                repository.save(system)
            } else {
                repository.save(
                    system.copy(
                        enabled = current.enabled,
                        executionPolicy = current.executionPolicy,
                    ),
                )
            }
        }
        val demo = DemoTools.openAppAndNavigate()
        if (existing[demo.id] == null) repository.save(demo)
    }

    suspend fun get(idOrName: String): ToolDefinition? {
        val all = repository.getAll()
        return all.find { it.id == idOrName || it.name == idOrName }
    }

    suspend fun enabled(): List<ToolDefinition> = repository.getAll().filter { it.enabled }

    suspend fun save(tool: ToolDefinition) = repository.save(tool)

    suspend fun delete(id: String) {
        val tool = repository.get(id) ?: return
        if (tool.type == ToolType.SYSTEM) return
        repository.delete(id)
    }
}
