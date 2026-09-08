package com.agent.mobile.tools

import android.content.Context
import android.os.Build
import android.util.Base64
import com.agent.mobile.automation.AndroidAutomation
import com.agent.shared.model.ExecutionError
import com.agent.shared.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SystemToolExecutor(
    private val context: Context,
    private val automation: AndroidAutomation,
) {
    suspend fun execute(name: String, inputs: Map<String, String>): ToolResult {
        return try {
            when (name) {
                "open_app" -> {
                    automation.launchApp(inputs["packageName"], inputs["appName"])
                    ok("launched" to (inputs["packageName"] ?: inputs["appName"].orEmpty()))
                }
                "take_screenshot" -> {
                    val bytes = automation.screenshot()
                        ?: return fail("UNSUPPORTED", "Screenshots require Android 11+ with Accessibility screenshot permission")
                    ok("imageBase64" to Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
                "read_current_screen" -> {
                    val tree = automation.readScreenTree()
                    ok("screen" to tree)
                }
                "get_device_info" -> {
                    ok(
                        "manufacturer" to Build.MANUFACTURER,
                        "model" to Build.MODEL,
                        "sdk" to Build.VERSION.SDK_INT.toString(),
                        "release" to Build.VERSION.RELEASE,
                        "package" to context.packageName,
                    )
                }
                else -> fail("UNKNOWN_TOOL", "Unknown system tool $name")
            }
        } catch (e: Exception) {
            fail("FAILED", e.message ?: "System tool failed")
        }
    }

    private fun ok(vararg pairs: Pair<String, String>) = ToolResult(
        success = true,
        output = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) }),
    )

    private fun fail(code: String, message: String) = ToolResult(
        success = false,
        error = ExecutionError(code, message),
    )
}
