package com.agent.mobile.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.agent.mobile.accessibility.AgentAccessibilityService

object NodeHitTester {
    fun findAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val service = AgentAccessibilityService.instance ?: return null
        val windows = service.windows.orEmpty().sortedByDescending { it.layer }
        if (windows.isEmpty()) {
            val root = service.rootInActiveWindow ?: return null
            if (root.packageName?.toString() == service.packageName) return null
            return smallestContaining(root, x, y)
        }
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() == service.packageName) continue
            val hit = smallestContaining(root, x, y) ?: continue
            if (hit.packageName?.toString() == service.packageName) {
                hit.recycle()
                continue
            }
            val bounds = Rect()
            hit.getBoundsInScreen(bounds)
            val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
            if (area <= bestArea) {
                best?.recycle()
                best = hit
                bestArea = area
            } else {
                hit.recycle()
            }
        }
        return best
    }

    fun toCaptured(node: AccessibilityNodeInfo): CapturedUiElement {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        return CapturedUiElement(
            resourceId = node.viewIdResourceName.orEmpty(),
            text = text,
            contentDescription = desc,
            className = node.className?.toString().orEmpty(),
            packageName = node.packageName?.toString().orEmpty(),
            bounds = bounds,
            value = text.ifBlank { desc },
        )
    }

    private fun smallestContaining(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return null
        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MAX_VALUE
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = smallestContaining(child, x, y) ?: continue
            val score = score(hit)
            if (best == null || score < bestScore) {
                best?.recycle()
                best = hit
                bestScore = score
            } else {
                hit.recycle()
            }
        }
        return best ?: AccessibilityNodeInfo.obtain(node)
    }

    private fun score(node: AccessibilityNodeInfo): Int {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
        val identityBonus = when {
            !node.viewIdResourceName.isNullOrBlank() -> 0
            !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() -> 50_000
            else -> 200_000
        }
        return area + identityBonus
    }
}
