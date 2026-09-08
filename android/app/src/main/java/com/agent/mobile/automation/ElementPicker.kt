package com.agent.mobile.automation

import android.content.Context
import android.content.Intent
import com.agent.mobile.MainActivity
import com.agent.mobile.accessibility.AgentAccessibilityService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object ElementPicker {
    private val _captures = MutableSharedFlow<CapturedUiElement>(extraBufferCapacity = 1)
    val captures: SharedFlow<CapturedUiElement> = _captures.asSharedFlow()

    private val _picking = MutableStateFlow(false)
    val picking: StateFlow<Boolean> = _picking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    var targetNodeId: String? = null
        private set

    @Volatile
    private var pendingCapture: CapturedUiElement? = null

    fun start(nodeId: String): Boolean {
        val service = AgentAccessibilityService.instance
        if (service == null) {
            _error.value = "Enable Accessibility → Android Agent Automation first."
            return false
        }
        if (_picking.value) {
            service.stopPicking()
        }
        _error.value = null
        targetNodeId = nodeId
        pendingCapture = null
        _picking.value = true
        service.startPicking()
        return true
    }

    fun pause() {
        AgentAccessibilityService.instance?.pauseFor()
    }

    fun stop() {
        _picking.value = false
        AgentAccessibilityService.instance?.stopPicking()
    }

    fun emit(element: CapturedUiElement) {
        pendingCapture = element
        _captures.tryEmit(element)
        _picking.value = false
    }

    fun consumePending(selectedId: String?): Pair<String, CapturedUiElement>? {
        val capture = pendingCapture ?: return null
        val id = targetNodeId ?: selectedId ?: return null
        pendingCapture = null
        return id to capture
    }

    fun fail(message: String) {
        _error.value = message
        _picking.value = false
    }

    fun resumeApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        context.startActivity(intent)
    }
}
