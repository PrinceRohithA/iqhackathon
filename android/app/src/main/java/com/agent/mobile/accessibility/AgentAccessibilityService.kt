package com.agent.mobile.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.agent.mobile.AgentApplication
import com.agent.mobile.automation.ElementPicker
import com.agent.mobile.automation.NodeHitTester
import java.util.concurrent.atomic.AtomicReference

class AgentAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var captureLayer: View? = null
    private var statusLabel: TextView? = null
    private var pauseButton: Button? = null
    private var picking = false
    private var pausedUntil = 0L
    private var capturing = false

    private val tick = object : Runnable {
        override fun run() {
            if (!picking) return
            val left = secondsLeft()
            if (left > 0) {
                if (capturing) showPausedOverlay()
                refreshChip(paused = true, seconds = left)
            } else {
                if (!capturing) showCaptureOverlay()
                refreshChip(paused = false, seconds = 0)
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        instanceRef.set(this)
        Log.i(TAG, "accessibility connected")
        runCatching { AgentApplication.instance.container.copilotSession.attach(this) }
        syncCopilotBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        if (picking) {
            if (secondsLeft() == 0 && !capturing) showCaptureOverlay()
            return
        }
        syncCopilotBubble()
    }

    override fun onInterrupt() = Unit

    fun startPicking() {
        mainExecutor.execute {
            picking = true
            capturing = false
            hideOverlay()
            runCatching { AgentApplication.instance.container.copilotSession.hideCompletely() }
            pauseForInternal(PAUSE_MS)
            showPausedOverlay()
            Log.i(TAG, "indicate started overlay=${overlay != null}")
            Toast.makeText(this, "Open the target app, then tap the field", Toast.LENGTH_LONG).show()
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    fun pauseFor(durationMs: Long = PAUSE_MS) {
        mainExecutor.execute {
            if (!picking) return@execute
            pauseForInternal(durationMs)
            showPausedOverlay()
            refreshChip(paused = true, seconds = (durationMs / 1000).toInt())
        }
    }

    fun hideOverlaysForCapture() {
        mainExecutor.execute { overlay?.visibility = View.GONE }
        runCatching { AgentApplication.instance.container.copilotSession.hideOverlaysForCapture() }
    }

    fun restoreIndicateOverlays() {
        mainExecutor.execute {
            if (!picking) return@execute
            overlay?.visibility = View.VISIBLE
        }
    }

    fun stopPicking() {
        mainExecutor.execute {
            picking = false
            capturing = false
            handler.removeCallbacks(tick)
            hideOverlay()
            syncCopilotBubble()
            Log.i(TAG, "indicate stopped")
        }
    }

    private fun pauseForInternal(durationMs: Long) {
        pausedUntil = SystemClock.uptimeMillis() + durationMs
    }

    private fun secondsLeft(): Int =
        ((pausedUntil - SystemClock.uptimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)

    private fun syncCopilotBubble() {
        if (picking) return
        runCatching {
            val copilot = AgentApplication.instance.container.copilotSession
            if (isOurAppFocused()) copilot.hideBubble() else copilot.restoreBubble()
        }
    }

    private fun isOurAppFocused(): Boolean {
        val focusedApp = windows.orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstOrNull { it.isFocused }
        val pkg = focusedApp?.root?.packageName?.toString() ?: return false
        return pkg == packageName
    }

    private fun showPausedOverlay() {
        if (overlay != null && !capturing) {
            overlay?.visibility = View.VISIBLE
            return
        }
        hideOverlay()
        capturing = false
        addOverlay(fullScreen = false)
    }

    private fun showCaptureOverlay() {
        if (capturing && overlay != null) {
            overlay?.visibility = View.VISIBLE
            return
        }
        hideOverlay()
        capturing = true
        addOverlay(fullScreen = true)
        Log.i(TAG, "capture overlay shown")
    }

    private fun addOverlay(fullScreen: Boolean) {
        val density = resources.displayMetrics.density
        val root = FrameLayout(this)
        if (fullScreen) {
            val capture = View(this).apply {
                setBackgroundColor(0x3300D4C0)
                isClickable = true
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) return@setOnTouchListener true
                    if (event.action == MotionEvent.ACTION_UP && picking && secondsLeft() == 0) {
                        val x = event.rawX.toInt()
                        val y = event.rawY.toInt()
                        val node = NodeHitTester.findAt(x, y)
                        Log.i(TAG, "tap x=$x y=$y node=${node != null}")
                        if (node != null) {
                            val captured = NodeHitTester.toCaptured(node)
                            node.recycle()
                            picking = false
                            capturing = false
                            handler.removeCallbacks(tick)
                            hideOverlay()
                            ElementPicker.emit(captured)
                            ElementPicker.resumeApp(this@AgentAccessibilityService)
                            Toast.makeText(this@AgentAccessibilityService, "Captured ${captured.value.ifBlank { captured.resourceId.ifBlank { "element" } }}", Toast.LENGTH_SHORT).show()
                            syncCopilotBubble()
                        } else {
                            statusLabel?.text = "Nothing there — tap a field"
                        }
                    }
                    true
                }
            }
            captureLayer = capture
            root.addView(
                capture,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0xF00B1220.toInt())
                cornerRadius = 20 * density
            }
            val label = TextView(this@AgentAccessibilityService).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            statusLabel = label
            val pause = Button(this@AgentAccessibilityService).apply {
                text = "Pause 5s"
                setOnClickListener { pauseFor(PAUSE_MS) }
            }
            pauseButton = pause
            val cancel = Button(this@AgentAccessibilityService).apply {
                text = "Cancel"
                setOnClickListener {
                    ElementPicker.stop()
                    ElementPicker.resumeApp(this@AgentAccessibilityService)
                }
            }
            addView(label)
            addView(pause)
            addView(cancel)
        }
        root.addView(
            chip,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply { bottomMargin = (16 * density).toInt() },
        )
        val params = if (fullScreen) captureLayoutParams() else pausedLayoutParams()
        try {
            windowManager.addView(root, params)
            overlay = root
            refreshChip(paused = !fullScreen, seconds = secondsLeft())
        } catch (e: Exception) {
            Log.e(TAG, "add overlay failed", e)
            overlay = null
            captureLayer = null
            statusLabel = null
            pauseButton = null
            capturing = false
            picking = false
            Toast.makeText(this, "Indicate overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
            ElementPicker.fail(e.message ?: "Could not show indicate overlay")
            ElementPicker.stop()
        }
    }

    private fun pausedLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
        }

    private fun captureLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP
        }

    private fun refreshChip(paused: Boolean, seconds: Int) {
        statusLabel?.text = if (paused) "Paused ${seconds}s — open the app" else "Tap a field"
        pauseButton?.visibility = if (paused) View.GONE else View.VISIBLE
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        overlay = null
        captureLayer = null
        statusLabel = null
        pauseButton = null
        capturing = false
        runCatching { windowManager.removeView(view) }
    }

    private val windowManager: WindowManager
        get() = getSystemService(WindowManager::class.java)

    override fun onDestroy() {
        picking = false
        handler.removeCallbacks(tick)
        hideOverlay()
        runCatching { AgentApplication.instance.container.copilotSession.detach() }
        if (instanceRef.get() === this) instanceRef.set(null)
        super.onDestroy()
    }

    companion object {
        const val PAUSE_MS = 5_000L
        private const val TAG = "AgentIndicate"
        private val instanceRef = AtomicReference<AgentAccessibilityService?>(null)
        val instance: AgentAccessibilityService? get() = instanceRef.get()
        val isEnabled: Boolean get() = instance != null
    }
}
