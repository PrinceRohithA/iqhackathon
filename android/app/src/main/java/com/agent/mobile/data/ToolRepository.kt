package com.agent.mobile.data

import com.agent.shared.AgentJson
import com.agent.shared.model.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ToolRepository(private val dao: ToolDao) {
    fun observeAll(): Flow<List<ToolDefinition>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDefinition() } }

    suspend fun getAll(): List<ToolDefinition> =
        dao.getAll().mapNotNull { it.toDefinition() }

    suspend fun get(id: String): ToolDefinition? = dao.getById(id)?.toDefinition()

    suspend fun save(tool: ToolDefinition) {
        dao.upsert(
            ToolEntity(
                id = tool.id,
                definitionJson = AgentJson.instance.encodeToString(ToolDefinition.serializer(), tool),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    private fun ToolEntity.toDefinition(): ToolDefinition? =
        runCatching {
            AgentJson.instance.decodeFromString(ToolDefinition.serializer(), definitionJson)
        }.onFailure { error ->
            android.util.Log.e("ToolRepository", "Failed to decode tool $id", error)
        }.getOrNull()
}
