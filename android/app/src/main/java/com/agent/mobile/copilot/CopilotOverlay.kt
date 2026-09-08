package com.agent.mobile.copilot

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.agent.mobile.accessibility.AgentAccessibilityService

class CopilotOverlay(
    private val service: AgentAccessibilityService,
    private val onSend: (String) -> Unit,
    private val onStop: () -> Unit,
    private val onCancel: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    private var bubble: View? = null
    private var panel: View? = null
    private var chip: View? = null
    private var statusLabel: TextView? = null
    private var input: EditText? = null
    private var hiddenForCapture = false

    fun showBubble() {
        onMain {
            hidePanel()
            hideChip()
            if (bubble != null) {
                bubble?.visibility = View.VISIBLE
                return@onMain
            }
            val size = (56 * density).toInt()
            val view = TextView(service).apply {
                text = "AI"
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF0D9488.toInt())
                }
                setOnClickListener { showPanel() }
            }
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                width = size
                height = size
                gravity = Gravity.BOTTOM or Gravity.END
                x = (16 * density).toInt()
                y = (96 * density).toInt()
            }
            makeDraggable(view, params)
            try {
                windowManager.addView(view, params)
                bubble = view
            } catch (_: Exception) {
            }
        }
    }

    fun hideCompletely() {
        onMain {
            hidePanel()
            hideChip()
            val view = bubble ?: return@onMain
            bubble = null
            runCatching { windowManager.removeView(view) }
        }
    }

    fun hideBubble() {
        onMain {
            if (panel != null || chip != null) return@onMain
            val view = bubble ?: return@onMain
            bubble = null
            runCatching { windowManager.removeView(view) }
        }
    }

    fun showPanel() {
        onMain {
            bubble?.visibility = View.GONE
            hideChip()
            if (panel != null) {
                panel?.visibility = View.VISIBLE
                return@onMain
            }
            val column = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                background = GradientDrawable().apply {
                    setColor(0xF00B1220.toInt())
                    cornerRadius = 18 * density
                }
            }
            val title = TextView(service).apply {
                text = "Copilot"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            val field = EditText(service).apply {
                hint = "What should I do on this screen?"
                setHintTextColor(0xFF9CA3AF.toInt())
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFF1F2937.toInt())
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_SEND
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEND) {
                        submit()
                        true
                    } else {
                        false
                    }
                }
            }
            input = field
            val row = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            val cancel = Button(service).apply {
                text = "Cancel"
                setOnClickListener {
                    onCancel()
                    showBubble()
                }
            }
            val send = Button(service).apply {
                text = "Send"
                setOnClickListener { submit() }
            }
            row.addView(cancel)
            row.addView(send)
            column.addView(title)
            column.addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            })
            column.addView(row)
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
                y = (16 * density).toInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            try {
                windowManager.addView(column, params)
                panel = column
            } catch (_: Exception) {
            }
        }
    }

    fun showStatus(text: String, showStop: Boolean = true) {
        onMain {
            hidePanel()
            bubble?.visibility = View.GONE
            val label = statusLabel
            if (chip != null && label != null) {
                chip?.visibility = View.VISIBLE
                label.text = text
                return@onMain
            }
            val row = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                background = GradientDrawable().apply {
                    setColor(0xF00B1220.toInt())
                    cornerRadius = 20 * density
                }
            }
            val status = TextView(service).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            statusLabel = status
            val stop = Button(service).apply {
                this.text = "Stop"
                setOnClickListener { onStop() }
                visibility = if (showStop) View.VISIBLE else View.GONE
            }
            row.addView(status)
            row.addView(stop)
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
                y = (16 * density).toInt()
            }
            try {
                windowManager.addView(row, params)
                chip = row
            } catch (_: Exception) {
            }
        }
    }

    fun hideForCapture() {
        onMain {
            hiddenForCapture = true
            bubble?.visibility = View.GONE
            panel?.visibility = View.GONE
            chip?.visibility = View.GONE
        }
    }

    fun destroy() {
        hideCompletely()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else service.mainExecutor.execute(block)
    }

    private fun submit() {
        val goal = input?.text?.toString()?.trim().orEmpty()
        if (goal.isBlank()) return
        input?.text = null
        onSend(goal)
    }

    private fun hidePanel() {
        val view = panel ?: return
        panel = null
        input = null
        runCatching { windowManager.removeView(view) }
    }

    private fun hideChip() {
        val view = chip ?: return
        chip = null
        statusLabel = null
        runCatching { windowManager.removeView(view) }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX - (event.rawX - downRawX).toInt()
                    params.y = startY - (event.rawY - downRawY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
    }
}
