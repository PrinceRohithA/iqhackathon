package com.agent.shared.tools

import com.agent.shared.model.ExecutionPolicy
import com.agent.shared.model.InputParameter
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolType

object SystemToolCatalog {
    val openApp = ToolDefinition(
        id = "system_open_app",
        name = "open_app",
        description = "Launch an installed Android application by package name or app name.",
        type = ToolType.SYSTEM,
        inputs = listOf(
            InputParameter("packageName", "string", "Android package name, e.g. com.instagram.android", required = false),
            InputParameter("appName", "string", "Human-readable app name if package is unknown", required = false),
        ),
        executionPolicy = ExecutionPolicy.AUTOMATIC,
        permissions = listOf("query_packages"),
        enabled = true,
    )

    val takeScreenshot = ToolDefinition(
        id = "system_take_screenshot",
        name = "take_screenshot",
        description = "Capture a screenshot of the current display.",
        type = ToolType.SYSTEM,
        executionPolicy = ExecutionPolicy.AUTOMATIC,
        permissions = listOf("accessibility"),
        enabled = true,
    )

    val readCurrentScreen = ToolDefinition(
        id = "system_read_current_screen",
        name = "read_current_screen",
        description = "Read the visible accessibility text tree of the current screen.",
        type = ToolType.SYSTEM,
        executionPolicy = ExecutionPolicy.AUTOMATIC,
        permissions = listOf("accessibility"),
        enabled = true,
    )

    val getDeviceInfo = ToolDefinition(
        id = "system_get_device_info",
        name = "get_device_info",
        description = "Return device model, manufacturer, Android SDK version, and other basic info.",
        type = ToolType.SYSTEM,
        executionPolicy = ExecutionPolicy.AUTOMATIC,
        enabled = true,
    )

    val all: List<ToolDefinition> = listOf(openApp, takeScreenshot, readCurrentScreen, getDeviceInfo)

    fun byName(name: String): ToolDefinition? = all.find { it.name == name || it.id == name }
}
