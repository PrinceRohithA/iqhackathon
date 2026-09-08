package com.agent.backend.executions

import com.agent.backend.persistence.Executions
import com.agent.shared.AgentJson
import com.agent.shared.model.ExecutionStatus
import com.agent.shared.model.ProtocolMessage
import com.agent.shared.model.WorkflowExecution
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.concurrent.ConcurrentHashMap

class ExecutionCoordinator {
    private val live = ConcurrentHashMap<String, WorkflowExecution>()

    fun track(execution: WorkflowExecution) {
        live[execution.executionId] = execution
        transaction {
            val existing = Executions.selectAll().where { Executions.id eq execution.executionId }.count()
            if (existing == 0L) {
                Executions.insert {
                    it[id] = execution.executionId
                    it[toolId] = execution.toolId
                    it[status] = execution.status.name
                    it[resultJson] = execution.result?.let { r -> AgentJson.instance.encodeToString(com.agent.shared.model.ToolResult.serializer(), r) }
                    it[createdAt] = execution.startedAt
                }
            } else {
                Executions.update({ Executions.id eq execution.executionId }) {
                    it[status] = execution.status.name
                    it[resultJson] = execution.result?.let { r -> AgentJson.instance.encodeToString(com.agent.shared.model.ToolResult.serializer(), r) }
                }
            }
        }
    }

    fun onStatus(msg: ProtocolMessage.ExecutionStatusMsg) {
        val current = live[msg.executionId] ?: WorkflowExecution(
            executionId = msg.executionId,
            toolId = "",
            status = msg.status,
            startedAt = System.currentTimeMillis(),
        )
        live[msg.executionId] = current.copy(
            status = msg.status,
            currentNodeId = msg.nodeId,
            currentNodeLabel = msg.nodeLabel,
        )
    }

    fun get(id: String): WorkflowExecution? = live[id]
}
