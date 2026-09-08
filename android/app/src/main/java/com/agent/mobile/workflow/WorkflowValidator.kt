package com.agent.mobile.workflow

import com.agent.shared.model.NodeType
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolType

object WorkflowValidator {
    fun validate(tool: ToolDefinition): List<String> {
        val errors = mutableListOf<String>()
        if (tool.name.isBlank()) errors += "Tool name is required"
        if (tool.description.isBlank()) errors += "Tool description is required"
        if (!tool.name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) {
            errors += "Tool name must be a valid identifier (letters, numbers, underscore)"
        }
        tool.inputs.forEach { input ->
            if (input.name.isBlank()) errors += "Input name cannot be empty"
        }
        if (tool.type == ToolType.USER) {
            val workflow = tool.workflow
            if (workflow == null || workflow.nodes.isEmpty()) {
                errors += "Workflow must contain at least one node"
            } else {
                val definedVars = tool.inputs.map { it.name }.toMutableSet()
                workflow.nodes.forEach { node ->
                    when (node.type) {
                        NodeType.FIND_ELEMENT -> {
                            val selector = node.selector()
                            if (selector == null || selector.isEmpty()) {
                                errors += "Find Element node ${node.id} needs a selector"
                            }
                        }
                        NodeType.LAUNCH_APP -> {
                            if (node.stringParam("packageName").isBlank() && node.stringParam("appName").isBlank()) {
                                errors += "Launch App node ${node.id} needs packageName or appName"
                            }
                        }
                        NodeType.TYPE_TEXT -> {
                            if (node.stringParam("value").isBlank()) {
                                errors += "Type Text node ${node.id} needs a value"
                            }
                        }
                        NodeType.SET_VARIABLE -> {
                            val name = node.stringParam("name")
                            if (name.isBlank()) errors += "Set Variable node ${node.id} needs a name"
                            else definedVars += name
                        }
                        else -> Unit
                    }
                    val interpolated = Regex("\\{\\{([^}]+)}}").findAll(
                        node.params.values.joinToString { it.toString() },
                    )
                    interpolated.forEach { match ->
                        val key = match.groupValues[1].trim()
                        if (key !in definedVars && key !in tool.inputs.map { it.name }) {
                            errors += "Undefined variable {{$key}} in node ${node.id}"
                        }
                    }
                }
            }
        }
        return errors.distinct()
    }
}
