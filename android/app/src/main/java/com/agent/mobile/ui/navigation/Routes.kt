package com.agent.mobile.ui.navigation

object Routes {
    const val Chat = "chat"
    const val Tools = "tools"
    const val Execution = "execution"
    const val Settings = "settings"
    const val ToolEdit = "tool_edit/{toolId}"
    const val WorkflowEditor = "workflow_editor/{toolId}"

    fun toolEdit(toolId: String) = "tool_edit/$toolId"
    fun workflowEditor(toolId: String) = "workflow_editor/$toolId"
}
