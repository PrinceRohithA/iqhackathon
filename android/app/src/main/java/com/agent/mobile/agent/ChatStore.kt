package com.agent.mobile.agent

import com.agent.shared.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class ChatStore {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun add(message: ChatMessage) {
        _messages.update { current -> current + message }
    }

    fun addUser(content: String, imageUri: String? = null): ChatMessage {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        add(msg)
        return msg
    }

    fun addAssistant(content: String, id: String = UUID.randomUUID().toString()): ChatMessage {
        val existing = _messages.value.indexOfFirst { it.id == id && it.role == "assistant" }
        val msg = ChatMessage(id, "assistant", content, timestamp = System.currentTimeMillis())
        if (existing >= 0) {
            _messages.update { it.toMutableList().also { list -> list[existing] = msg } }
        } else {
            add(msg)
        }
        return msg
    }

    fun addTool(toolName: String, content: String, executionId: String): ChatMessage {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "tool",
            content = content,
            toolName = toolName,
            executionId = executionId,
            timestamp = System.currentTimeMillis(),
        )
        add(msg)
        return msg
    }

    fun appendAssistantDelta(id: String, delta: String) {
        val existing = _messages.value.indexOfFirst { it.id == id }
        if (existing >= 0) {
            _messages.update { list ->
                val copy = list.toMutableList()
                copy[existing] = copy[existing].copy(content = copy[existing].content + delta)
                copy
            }
        } else {
            addAssistant(delta, id)
        }
    }

    fun clear() {
        _messages.value = emptyList()
    }
}
