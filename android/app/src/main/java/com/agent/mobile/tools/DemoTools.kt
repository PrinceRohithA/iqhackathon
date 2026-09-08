package com.agent.mobile.tools

import com.agent.shared.model.ExecutionPolicy
import com.agent.shared.model.InputParameter
import com.agent.shared.model.NodeType
import com.agent.shared.model.Selector
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolType
import com.agent.shared.model.Workflow
import com.agent.shared.model.WorkflowNode
import com.agent.shared.AgentJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object DemoTools {
    fun openAppAndNavigate(): ToolDefinition {
        val inputs = listOf(
            InputParameter("appName", "string", "App to open, e.g. Settings"),
            InputParameter("elementText", "string", "On-screen text to find and tap"),
        )
        val launch = WorkflowNode(
            id = "n1",
            type = NodeType.LAUNCH_APP,
            params = JsonObject(mapOf("appName" to JsonPrimitive("{{appName}}"))),
        )
        val find = WorkflowNode(
            id = "n2",
            type = NodeType.FIND_ELEMENT,
            params = JsonObject(
                mapOf(
                    "selector" to AgentJson.instance.encodeToJsonElement(
                        Selector.serializer(),
                        Selector(text = "{{elementText}}"),
                    ),
                ),
            ),
        )
        val tap = WorkflowNode(id = "n3", type = NodeType.TAP)
        val result = WorkflowNode(
            id = "n4",
            type = NodeType.RETURN_RESULT,
            params = JsonObject(mapOf("name" to JsonPrimitive("status"), "value" to JsonPrimitive("opened"))),
        )
        return ToolDefinition(
            id = "demo_open_app_and_navigate",
            name = "open_app_and_navigate",
            description = "Launch an Android app, find an on-screen element by text, tap it, and return success.",
            type = ToolType.USER,
            inputs = inputs,
            workflow = Workflow(version = 1, id = "wf_demo_nav", inputs = inputs, nodes = listOf(launch, find, tap, result)),
            executionPolicy = ExecutionPolicy.AUTOMATIC,
            enabled = true,
        )
    }
}
