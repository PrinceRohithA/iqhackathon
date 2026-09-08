package com.agent.backend.tools

import com.agent.shared.AgentJson
import com.agent.shared.model.ToolDefinition
import com.agent.shared.tools.SystemToolCatalog
import com.agent.backend.persistence.CachedTools
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ToolRegistry {
    private val json = AgentJson.instance
    private val system = SystemToolCatalog.all.associateBy { it.name }.toMutableMap()
    private val deviceTools = mutableMapOf<String, ToolDefinition>()

    init {
        reloadCache()
    }

    @Synchronized
    fun replaceDeviceTools(tools: List<ToolDefinition>) {
        deviceTools.clear()
        tools.forEach { tool ->
            deviceTools[tool.name] = tool.copy(workflow = null)
        }
        transaction {
            CachedTools.deleteAll()
            deviceTools.values.forEach { tool ->
                CachedTools.insert {
                    it[id] = tool.id
                    it[definitionJson] = json.encodeToString(ToolDefinition.serializer(), tool)
                }
            }
        }
    }

    @Synchronized
    fun upsert(tool: ToolDefinition) {
        deviceTools[tool.name] = tool.copy(workflow = null)
        transaction {
            CachedTools.deleteWhere { CachedTools.id eq tool.id }
            CachedTools.insert {
                it[id] = tool.id
                it[definitionJson] = json.encodeToString(ToolDefinition.serializer(), tool.copy(workflow = null))
            }
        }
    }

    @Synchronized
    fun delete(id: String) {
        val existing = get(id) ?: return
        deviceTools.remove(existing.name)
        transaction { CachedTools.deleteWhere { CachedTools.id eq id } }
    }

    @Synchronized
    fun get(idOrName: String): ToolDefinition? =
        deviceTools.values.find { it.id == idOrName || it.name == idOrName }
            ?: system.values.find { it.id == idOrName || it.name == idOrName }

    @Synchronized
    fun enabled(): List<ToolDefinition> {
        val device = deviceTools.values.filter { it.enabled }
        val names = device.map { it.name }.toSet()
        val leftoverSystem = system.values.filter { it.enabled && it.name !in names }
        return device + leftoverSystem
    }

    fun toOpenAiTools(): List<JsonObject> = enabled().map { tool ->
        val properties = tool.inputs.associate { input ->
            input.name to JsonObject(
                mapOf(
                    "type" to JsonPrimitive(if (input.type == "number") "number" else "string"),
                    "description" to JsonPrimitive(input.description.ifBlank { input.name }),
                ),
            )
        }
        val required = tool.inputs.filter { it.required }.map { JsonPrimitive(it.name) }
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("function"),
                "function" to JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(tool.name),
                        "description" to JsonPrimitive(tool.description),
                        "parameters" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("object"),
                                "properties" to JsonObject(properties),
                                "required" to kotlinx.serialization.json.JsonArray(required),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun reloadCache() {
        runCatching {
            transaction {
                CachedTools.selectAll().forEach { row ->
                    val tool = json.decodeFromString(ToolDefinition.serializer(), row[CachedTools.definitionJson])
                    deviceTools[tool.name] = tool
                }
            }
        }
    }
}
