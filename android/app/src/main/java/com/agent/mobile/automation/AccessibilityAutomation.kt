package com.agent.mobile.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.mobile.accessibility.AgentAccessibilityService
import com.agent.mobile.automation.NodeHitTester
import com.agent.shared.model.Selector
import com.agent.shared.model.SwipeGesture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class AccessibilityAutomation(private val context: Context) : AndroidAutomation {

    private fun service(): AgentAccessibilityService =
        AgentAccessibilityService.instance
            ?: error("Accessibility service is not enabled. Enable it in Settings.")

    override suspend fun find(selector: Selector): UiElement? = withContext(Dispatchers.Main) {
        if (selector.x != null && selector.y != null &&
            selector.resourceId.isNullOrBlank() &&
            selector.text.isNullOrBlank() &&
            selector.contentDescription.isNullOrBlank()
        ) {
            return@withContext UiElement(
                id = "coord",
                text = "",
                contentDescription = "",
                resourceId = "",
                className = "",
                bounds = Rect(selector.x!!, selector.y!!, selector.x!! + 1, selector.y!! + 1),
                clickable = true,
            )
        }
        val root = service().rootInActiveWindow ?: return@withContext null
        val matches = mutableListOf<AccessibilityNodeInfo>()
        collect(root, matches, selector)
        val index = selector.index ?: 0
        val node = matches.getOrNull(index) ?: return@withContext null
        node.toUiElement()
    }

    override suspend fun findAt(x: Int, y: Int): UiElement? = withContext(Dispatchers.Main) {
        val node = NodeHitTester.findAt(x, y) ?: return@withContext null
        val element = node.toUiElement()
        node.recycle()
        element
    }

    override suspend fun tap(element: UiElement) {
        val clicked = withContext(Dispatchers.Main) {
            findLiveNode(element)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        if (!clicked) {
            tapAt(element.bounds.centerX(), element.bounds.centerY())
        }
        delay(250)
    }

    override suspend fun tapAt(x: Int, y: Int) {
        dispatchClick(x.toFloat(), y.toFloat(), 80)
        delay(250)
    }

    override suspend fun longPress(element: UiElement) {
        val pressed = withContext(Dispatchers.Main) {
            findLiveNode(element)?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
        }
        if (!pressed) {
            dispatchClick(element.bounds.centerX().toFloat(), element.bounds.centerY().toFloat(), 800)
        }
        delay(250)
    }

    override suspend fun longPressAt(x: Int, y: Int) {
        dispatchClick(x.toFloat(), y.toFloat(), 800)
        delay(250)
    }

    override suspend fun type(element: UiElement, text: String) {
        withContext(Dispatchers.Main) {
            val node = findLiveNode(element) ?: service().rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
            if (!ok) {
                node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        delay(200)
    }

    override suspend fun typeFocused(text: String) {
        typeIntoCurrent(text)
    }

    override suspend fun typeAt(x: Int, y: Int, text: String) {
        tapAt(x, y)
        delay(350)
        typeIntoCurrent(text)
    }

    private suspend fun typeIntoCurrent(text: String) {
        withContext(Dispatchers.Main) {
            val root = service().rootInActiveWindow
            val node = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: findFirstEditable(root)
            node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val set = node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
            if (!set && node != null) {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("copilot", text))
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
        }
        delay(250)
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val hit = findFirstEditable(child)
            if (hit != null) return hit
        }
        return null
    }

    override suspend fun home() {
        service().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        delay(400)
    }

    override suspend fun swipe(gesture: SwipeGesture) {
        val metrics = context.resources.displayMetrics
        val cx = gesture.x ?: (metrics.widthPixels / 2f)
        val cy = gesture.y ?: (metrics.heightPixels / 2f)
        val dist = minOf(metrics.widthPixels, metrics.heightPixels) * 0.35f
        val (x2, y2) = when (gesture.direction.lowercase()) {
            "down" -> cx to cy + dist
            "left" -> cx - dist to cy
            "right" -> cx + dist to cy
            else -> cx to cy - dist
        }
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 350)
        dispatch(GestureDescription.Builder().addStroke(stroke).build())
        delay(300)
    }

    override suspend fun readText(element: UiElement): String =
        element.text.ifBlank { element.contentDescription }

    override suspend fun back() {
        service().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(400)
    }

    override suspend fun launchApp(packageName: String?, appName: String?) {
        val pkg = resolvePackage(packageName, appName)
            ?: error("Could not resolve app ${appName ?: packageName}")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: error("No launch intent for $pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        delay(1500)
        waitForIdle()
    }

    override suspend fun screenshot(): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val svc = runCatching { service() }.getOrNull() ?: return null
        AgentAccessibilityService.instance?.hideOverlaysForCapture()
        delay(250)
        return try {
            withTimeout(8_000) {
                val software = suspendCancellableCoroutine<Bitmap?> { cont ->
                    svc.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        context.mainExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                                try {
                                    val hardware = result.hardwareBuffer
                                    val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                                    val copy = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                    wrapped?.recycle()
                                    hardware.close()
                                    if (cont.isActive) cont.resume(copy)
                                } catch (_: Exception) {
                                    runCatching { result.hardwareBuffer.close() }
                                    if (cont.isActive) cont.resume(null)
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                } ?: return@withTimeout null
                withContext(Dispatchers.Default) { toJpeg(software) }
            }
        } catch (_: Exception) {
            null
        } finally {
            AgentAccessibilityService.instance?.restoreIndicateOverlays()
        }
    }

    private fun toJpeg(source: Bitmap): ByteArray {
        val maxEdge = 720
        val scale = maxEdge.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        return try {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        } finally {
            if (scaled !== source) scaled.recycle()
            source.recycle()
        }
    }

    override suspend fun readScreenTree(): String = withContext(Dispatchers.Main) {
        val root = service().rootInActiveWindow ?: return@withContext "(no active window)"
        buildString { dump(root, this, 0) }
    }

    override suspend fun waitForIdle(timeoutMs: Long) {
        delay(timeoutMs.coerceAtMost(3_000))
    }

    private fun resolvePackage(packageName: String?, appName: String?): String? {
        if (!packageName.isNullOrBlank()) return packageName
        if (appName.isNullOrBlank()) return null
        val needle = appName.trim().lowercase()
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        return apps.firstOrNull { info ->
            pm.getApplicationLabel(info).toString().lowercase() == needle
        }?.packageName ?: apps.firstOrNull { info ->
            pm.getApplicationLabel(info).toString().lowercase().contains(needle)
        }?.packageName
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, selector: Selector) {
        if (matches(node, selector)) out += node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, selector)
        }
    }

    private fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val resourceId = selector.resourceId
        val contentDescription = selector.contentDescription
        val text = selector.text
        val className = selector.className
        if (!resourceId.isNullOrBlank() && node.viewIdResourceName != resourceId) return false
        if (!contentDescription.isNullOrBlank()) {
            val desc = node.contentDescription?.toString().orEmpty()
            if (!desc.contains(contentDescription, ignoreCase = true)) return false
        }
        if (!text.isNullOrBlank()) {
            val nodeText = node.text?.toString().orEmpty()
            if (!nodeText.contains(text, ignoreCase = true)) return false
        }
        if (!className.isNullOrBlank() && node.className?.toString() != className) return false
        return !resourceId.isNullOrBlank() ||
            !contentDescription.isNullOrBlank() ||
            !text.isNullOrBlank() ||
            !className.isNullOrBlank()
    }

    private fun findLiveNode(element: UiElement): AccessibilityNodeInfo? {
        val root = service().rootInActiveWindow ?: return null
        val matches = mutableListOf<AccessibilityNodeInfo>()
        collect(
            root,
            matches,
            Selector(
                resourceId = element.resourceId.ifBlank { null },
                text = element.text.ifBlank { null },
                contentDescription = element.contentDescription.ifBlank { null },
                className = element.className.ifBlank { null },
            ),
        )
        return matches.firstOrNull()
    }

    private fun AccessibilityNodeInfo.toUiElement(): UiElement {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return UiElement(
            id = viewIdResourceName.orEmpty().ifBlank { hashCode().toString() },
            text = text?.toString().orEmpty(),
            contentDescription = contentDescription?.toString().orEmpty(),
            resourceId = viewIdResourceName.orEmpty(),
            className = className?.toString().orEmpty(),
            bounds = bounds,
            clickable = isClickable,
        )
    }

    private fun dump(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        out.append(indent)
            .append(node.className)
            .append(" text=").append(text)
            .append(" desc=").append(desc)
            .append(" id=").append(id)
            .append(" clickable=").append(node.isClickable)
            .append(" editable=").append(node.isEditable)
            .append(" bounds=[").append(bounds.left).append(',').append(bounds.top)
            .append("][").append(bounds.right).append(',').append(bounds.bottom).append(']')
            .append('\n')
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dump(child, out, depth + 1)
        }
    }

    private suspend fun dispatchClick(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun dispatch(gesture: GestureDescription) {
        val svc = service()
        suspendCancellableCoroutine { cont ->
            val ok = svc.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                },
                null,
            )
            if (!ok && cont.isActive) cont.resume(Unit)
        }
    }
}
