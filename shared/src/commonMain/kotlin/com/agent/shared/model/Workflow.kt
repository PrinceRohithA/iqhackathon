package com.agent.shared.model

import com.agent.shared.AgentJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Selector(
    val resourceId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val index: Int? = null,
    val x: Int? = null,
    val y: Int? = null,
) {
    fun isEmpty(): Boolean =
        resourceId.isNullOrBlank() &&
            text.isNullOrBlank() &&
            contentDescription.isNullOrBlank() &&
            className.isNullOrBlank() &&
            x == null &&
            y == null

    fun describe(): String = buildList {
        resourceId?.let { add("id=$it") }
        contentDescription?.let { add("desc=$it") }
        text?.let { add("text=$it") }
        className?.let { add("class=$it") }
        index?.let { add("index=$it") }
        if (x != null && y != null) add("($x,$y)")
    }.joinToString(", ").ifBlank { "(empty selector)" }
}

@Serializable
data class SwipeGesture(
    val direction: String = "up",
    val x: Float? = null,
    val y: Float? = null,
)

@Serializable
data class Workflow(
    val version: Int = 1,
    val id: String,
    val inputs: List<InputParameter> = emptyList(),
    val nodes: List<WorkflowNode> = emptyList(),
    val timeoutMs: Long = 60_000,
    val nodeTimeoutMs: Long = 10_000,
)

@Serializable
data class WorkflowNode(
    val id: String,
    val type: NodeType,
    val params: JsonObject = JsonObject(emptyMap()),
    val children: List<WorkflowNode> = emptyList(),
) {
    fun stringParam(key: String, default: String = ""): String =
        runCatching { params[key]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: default

    fun intParam(key: String, default: Int = 0): Int =
        runCatching { params[key]?.jsonPrimitive?.intOrNull }.getOrNull() ?: default

    fun longParam(key: String, default: Long = 0): Long =
        runCatching { params[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() }.getOrNull() ?: default

    fun scalarParams(): Map<String, String> =
        params.mapNotNull { (key, value) ->
            val primitive = value as? JsonPrimitive ?: return@mapNotNull null
            key to (primitive.contentOrNull ?: return@mapNotNull null)
        }.toMap()

    fun selector(): Selector? {
        val element = params["selector"] ?: return null
        return runCatching { AgentJson.instance.decodeFromJsonElement(Selector.serializer(), element) }.getOrNull()
    }

    fun label(): String = when (type) {
        NodeType.LAUNCH_APP -> "Launch App ${stringParam("appName").ifBlank { stringParam("packageName") }}".trim()
        NodeType.FIND_ELEMENT -> "Find ${selector()?.describe() ?: stringParam("text")}"
        NodeType.TAP -> "Tap"
        NodeType.LONG_PRESS -> "Long Press"
        NodeType.SWIPE -> "Swipe ${stringParam("direction", "up")}"
        NodeType.TYPE_TEXT -> "Type ${stringParam("value")}"
        NodeType.READ_TEXT -> "Read Text"
        NodeType.BACK -> "Back"
        NodeType.WAIT -> "Wait ${intParam("durationMs", 1000)}ms"
        NodeType.CONDITION -> "If ${stringParam("variable")} ${stringParam("operator", "eq")} ${stringParam("value")}"
        NodeType.LOOP -> "Loop ${intParam("times", 1)}x"
        NodeType.SET_VARIABLE -> "Set ${stringParam("name")}"
        NodeType.INPUT_PARAMETER -> "Input ${stringParam("name")}"
        NodeType.RETURN_RESULT -> "Return Result"
        NodeType.SCREENSHOT -> "Screenshot"
    }

    companion object {
        fun of(id: String, type: NodeType, vararg pairs: Pair<String, String>): WorkflowNode =
            WorkflowNode(
                id = id,
                type = type,
                params = JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) }),
            )
    }
}
