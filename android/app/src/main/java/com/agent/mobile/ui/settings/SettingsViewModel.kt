package com.agent.mobile.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.mobile.accessibility.AgentAccessibilityService
import com.agent.mobile.agent.parseAgentEndpoint
import com.agent.mobile.core.AppContainer
import com.agent.shared.model.ConnectionState
import com.agent.shared.model.DeviceInfo
import com.agent.shared.model.HealthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _port = MutableStateFlow("8787")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val connection: StateFlow<ConnectionState> = container.agentClient.connection
    val health: StateFlow<HealthResponse?> = container.agentClient.health
    val accessibilityEnabled: Boolean get() = AgentAccessibilityService.isEnabled

    init {
        viewModelScope.launch {
            val s = container.settings.snapshot()
            if (_host.value.isEmpty()) _host.value = s.host
            if (_port.value == "8787" && s.port != 0) _port.value = s.port.toString()
            if (_code.value.isEmpty()) _code.value = s.pairingCode
        }
    }

    fun updateHost(value: String) {
        _host.value = value.filterNot { it.isWhitespace() }
    }

    fun updatePort(value: String) {
        _port.value = value.filter { it.isDigit() }.take(5)
    }

    fun updateCode(value: String) {
        _code.value = value.filter { it.isDigit() }.take(8)
    }

    fun connect() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                val endpoint = parseAgentEndpoint(_host.value, _port.value.toIntOrNull() ?: 8787)
                if (endpoint.host.isBlank()) error("Enter the PC IP, e.g. 10.109.32.248")
                if (_code.value.isBlank()) error("Enter the 6-digit pairing code shown on the PC")
                _host.value = endpoint.host
                _port.value = endpoint.port.toString()
                val saved = container.settings.snapshot()
                val device = DeviceInfo(
                    deviceId = saved.sessionId.ifBlank { UUID.randomUUID().toString() },
                    name = Build.MODEL,
                    model = Build.MODEL,
                    sdk = Build.VERSION.SDK_INT,
                )
                val pairing = container.agentClient.pair(endpoint.host, endpoint.port, _code.value, device)
                container.settings.update(
                    host = endpoint.host,
                    port = endpoint.port,
                    pairingCode = _code.value,
                    token = pairing.token,
                    sessionId = pairing.sessionId,
                )
                container.agentClient.connect(endpoint.host, endpoint.port, pairing.token)
                _message.value = "Connected to ${endpoint.httpBase}"
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not connect"
            } finally {
                _busy.value = false
            }
        }
    }

    fun disconnect() {
        container.agentClient.disconnect()
        viewModelScope.launch { container.settings.clearSession() }
        _message.value = "Disconnected"
    }

    fun refreshHealth() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val endpoint = parseAgentEndpoint(_host.value, _port.value.toIntOrNull() ?: 8787)
                val health = container.agentClient.fetchHealth(endpoint.host, endpoint.port)
                _message.value = "Backend ${health.backend}. Pairing code on server: ${health.pairingCode}"
            } catch (e: Exception) {
                _message.value = e.message ?: "Health check failed"
            } finally {
                _busy.value = false
            }
        }
    }
}

fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
