package com.agent.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionPolicy {
    @SerialName("automatic")
    AUTOMATIC,

    @SerialName("approval_required")
    APPROVAL_REQUIRED,
}

@Serializable
enum class ExecutionStatus {
    @SerialName("created")
    CREATED,

    @SerialName("waiting_approval")
    WAITING_APPROVAL,

    @SerialName("running")
    RUNNING,

    @SerialName("completed")
    COMPLETED,

    @SerialName("failed")
    FAILED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("timeout")
    TIMEOUT,
}

@Serializable
enum class ToolType {
    @SerialName("user")
    USER,

    @SerialName("system")
    SYSTEM,
}

@Serializable
enum class NodeType {
    @SerialName("launch_app")
    LAUNCH_APP,

    @SerialName("find_element")
    FIND_ELEMENT,

    @SerialName("tap")
    TAP,

    @SerialName("long_press")
    LONG_PRESS,

    @SerialName("swipe")
    SWIPE,

    @SerialName("type_text")
    TYPE_TEXT,

    @SerialName("read_text")
    READ_TEXT,

    @SerialName("back")
    BACK,

    @SerialName("wait")
    WAIT,

    @SerialName("condition")
    CONDITION,

    @SerialName("loop")
    LOOP,

    @SerialName("set_variable")
    SET_VARIABLE,

    @SerialName("input_parameter")
    INPUT_PARAMETER,

    @SerialName("return_result")
    RETURN_RESULT,

    @SerialName("screenshot")
    SCREENSHOT,
}

@Serializable
enum class ConnectionState {
    @SerialName("disconnected")
    DISCONNECTED,

    @SerialName("connecting")
    CONNECTING,

    @SerialName("connected")
    CONNECTED,

    @SerialName("reconnecting")
    RECONNECTING,
}
