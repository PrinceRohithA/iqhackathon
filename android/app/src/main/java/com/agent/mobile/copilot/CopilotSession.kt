package com.agent.mobile.copilot

import android.content.Context
import android.util.Base64
import com.agent.mobile.accessibility.AgentAccessibilityService
import com.agent.mobile.agent.AgentClient
import com.agent.mobile.automation.AccessibilityAutomation
import com.agent.mobile.automation.ElementPicker
import com.agent.shared.model.ConnectionState
import com.agent.shared.model.CopilotAction
import com.agent.shared.model.ProtocolMessage
import com.agent.shared.model.SwipeGesture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CopilotSession(
    private val appContext: Context,
    private val automation: AccessibilityAutomation,
    private val agentClient: AgentClient,
    private val scope: CoroutineScope,
) {
    private var overlay: CopilotOverlay? = null
    private var job: Job? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ProtocolMessage.CopilotPlan>>()

    fun startListening() {
        scope.launch {
            agentClient.incoming.collect { msg ->
                if (msg is ProtocolMessage.CopilotPlan) {
                    pending.remove(msg.turnId)?.complete(msg)
                }
            }
        }
    }

    fun attach(service: AgentAccessibilityService) {
        overlay?.destroy()
        overlay = CopilotOverlay(
            service = service,
            onSend = { goal -> start(goal) },
            onStop = { stop() },
            onCancel = { stop() },
        ).also { /* bubble is shown only when the Agent app is not in front */ }
    }

    fun detach() {
        stop()
        overlay?.destroy()
        overlay = null
    }

    fun showPanel() {
        val current = overlay
        if (current == null) {
            AgentAccessibilityService.instance?.let { attach(it) }
        }
        overlay?.showPanel()
    }

    fun hideOverlaysForCapture() {
        overlay?.hideForCapture()
    }

    fun hideCompletely() {
        overlay?.hideCompletely()
    }

    fun hideBubble() {
        if (job?.isActive == true) return
        overlay?.hideBubble()
    }

    fun restoreBubble() {
        if (job?.isActive == true) return
        if (ElementPicker.picking.value) return
        overlay?.showBubble()
    }

    fun stop() {
        job?.cancel()
        job = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        agentClient.send(ProtocolMessage.CopilotStop())
        overlay?.showBubble()
    }

    private fun start(goal: String) {
        if (agentClient.connection.value != ConnectionState.CONNECTED) {
            overlay?.showStatus("Pair the device in Settings first", showStop = false)
            scope.launch {
                delay(2_000)
                overlay?.showBubble()
            }
            return
        }
        job?.cancel()
        job = scope.launch {
            try {
                runLoop(goal)
            } catch (_: CancellationException) {
                overlay?.showBubble()
            } catch (e: Exception) {
                overlay?.showStatus(e.message ?: "Copilot failed", showStop = false)
                delay(2_500)
                overlay?.showBubble()
            }
        }
    }

    private suspend fun runLoop(goal: String) {
        val metrics = appContext.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        var step = 1
        while (step <= MAX_STEPS) {
            overlay?.hideForCapture()
            delay(250)
            val jpeg = runCatching { automation.screenshot() }.getOrNull()
            val tree = runCatching { automation.readScreenTree() }.getOrDefault("(no tree)").take(8_000)
            val turnId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<ProtocolMessage.CopilotPlan>()
            pending[turnId] = deferred
            overlay?.showStatus("Thinking…")
            agentClient.send(
                ProtocolMessage.CopilotObserve(
                    turnId = turnId,
                    goal = goal,
                    step = step,
                    screenWidth = width,
                    screenHeight = height,
                    imageJpegBase64 = jpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                    screenTree = tree,
                ),
            )
            val plan = withTimeout(90_000) { deferred.await() }
            if (!plan.error.isNullOrBlank()) {
                overlay?.showStatus(plan.error ?: "Failed", showStop = false)
                delay(2_500)
                overlay?.showBubble()
                return
            }
            overlay?.showStatus(plan.speak.ifBlank { "Acting…" })
            overlay?.hideForCapture()
            delay(120)
            for (action in plan.actions) {
                execute(action, width, height)
            }
            if (plan.done || !plan.needNextScreenshot) {
                overlay?.showStatus(if (plan.done) plan.speak.ifBlank { "Done" } else plan.speak.ifBlank { "Done" }, showStop = false)
                delay(2_000)
                overlay?.showBubble()
                return
            }
            step++
            delay(400)
        }
        overlay?.showStatus("Stopped after $MAX_STEPS steps", showStop = false)
        delay(2_000)
        overlay?.showBubble()
    }

    private suspend fun execute(action: CopilotAction, width: Int, height: Int) {
        val x = action.x?.let { (it.coerceIn(0, 1000) / 1000f * width).toInt() }
        val y = action.y?.let { (it.coerceIn(0, 1000) / 1000f * height).toInt() }
        when (action.type.lowercase()) {
            "tap" -> if (x != null && y != null) automation.tapAt(x, y)
            "long_press", "longpress" -> if (x != null && y != null) automation.longPressAt(x, y)
            "swipe" -> automation.swipe(
                SwipeGesture(
                    direction = action.direction ?: "up",
                    x = x?.toFloat(),
                    y = y?.toFloat(),
                ),
            )
            "type", "type_text", "input" -> {
                val value = action.text.orEmpty()
                if (x != null && y != null) automation.typeAt(x, y, value)
                else automation.typeFocused(value)
            }
            "back" -> automation.back()
            "home" -> automation.home()
            "wait" -> delay(action.durationMs ?: 800)
        }
    }

    companion object {
        const val MAX_STEPS = 12
    }
}
