package com.agent.shared

import kotlinx.serialization.json.Json

object AgentJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
        isLenient = true
    }
}
