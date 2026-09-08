package com.agent.mobile.data

import com.agent.shared.AgentJson
import com.agent.shared.model.ExecutionLogEntry
import com.agent.shared.model.ExecutionStatus
import com.agent.shared.model.ToolResult
import com.agent.shared.model.WorkflowExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

class ExecutionRepository(private val dao: ExecutionDao) {
    fun observeRecent(): Flow<List<WorkflowExecution>> =
        dao.observeRecent().map { rows -> rows.map { it.toExecution() } }

    suspend fun save(execution: WorkflowExecution) {
        dao.upsert(
            ExecutionEntity(
                id = execution.executionId,
                toolId = execution.toolId,
                status = execution.status.name,
                logJson = AgentJson.instance.encodeToString(
                    ListSerializer(ExecutionLogEntry.serializer()),
                    execution.logs,
                ),
                resultJson = execution.result?.let {
                    AgentJson.instance.encodeToString(ToolResult.serializer(), it)
                },
                createdAt = execution.startedAt,
            ),
        )
    }

    private fun ExecutionEntity.toExecution(): WorkflowExecution {
        val logs = runCatching {
            AgentJson.instance.decodeFromString(ListSerializer(ExecutionLogEntry.serializer()), logJson)
        }.getOrDefault(emptyList())
        val result = resultJson?.let {
            runCatching { AgentJson.instance.decodeFromString(ToolResult.serializer(), it) }.getOrNull()
        }
        val status = runCatching { ExecutionStatus.valueOf(status) }.getOrDefault(ExecutionStatus.FAILED)
        return WorkflowExecution(
            executionId = id,
            toolId = toolId,
            status = status,
            logs = logs,
            result = result,
            startedAt = createdAt,
        )
    }
}
