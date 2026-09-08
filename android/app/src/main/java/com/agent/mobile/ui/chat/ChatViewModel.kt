package com.agent.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.mobile.accessibility.AgentAccessibilityService
import com.agent.mobile.core.AppContainer
import com.agent.mobile.execution.PendingApproval
import com.agent.shared.model.ChatMessage
import com.agent.shared.model.ConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(private val container: AppContainer) : ViewModel() {
    val messages: StateFlow<List<ChatMessage>> = container.chatStore.messages
    val connection: StateFlow<ConnectionState> = container.agentClient.connection
    val pending: StateFlow<PendingApproval?> = container.executionManager.pending
    val lastError: StateFlow<String?> = container.agentClient.lastError

    fun openCopilot() {
        if (!AgentAccessibilityService.isEnabled) {
            container.chatStore.addAssistant("Enable Accessibility in Settings first, then tap Copilot.")
            return
        }
        container.copilotSession.showPanel()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val user = container.chatStore.addUser(trimmed)
        viewModelScope.launch {
            if (container.agentClient.connection.value != ConnectionState.CONNECTED) {
                container.chatStore.addAssistant("Not connected to the PC backend. Pair the device in Settings.")
                return@launch
            }
            container.agentClient.sendChat(user.id.ifBlank { UUID.randomUUID().toString() }, trimmed)
        }
    }

    fun approve() {
        val pending = container.executionManager.pending.value ?: return
        container.executionManager.approve(pending.executionId)
    }

    fun reject() {
        val pending = container.executionManager.pending.value ?: return
        container.executionManager.reject(pending.executionId)
    }
}
