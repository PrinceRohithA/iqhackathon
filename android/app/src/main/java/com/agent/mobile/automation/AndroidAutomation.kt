package com.agent.mobile.automation

import android.graphics.Rect
import com.agent.shared.model.Selector
import com.agent.shared.model.SwipeGesture

data class UiElement(
    val id: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val className: String,
    val bounds: Rect,
    val clickable: Boolean,
)

interface AndroidAutomation {
    suspend fun find(selector: Selector): UiElement?
    suspend fun findAt(x: Int, y: Int): UiElement?
    suspend fun tap(element: UiElement)
    suspend fun tapAt(x: Int, y: Int)
    suspend fun longPress(element: UiElement)
    suspend fun longPressAt(x: Int, y: Int)
    suspend fun type(element: UiElement, text: String)
    suspend fun typeFocused(text: String)
    suspend fun typeAt(x: Int, y: Int, text: String)
    suspend fun swipe(gesture: SwipeGesture)
    suspend fun home()
    suspend fun readText(element: UiElement): String
    suspend fun back()
    suspend fun launchApp(packageName: String?, appName: String?)
    suspend fun screenshot(): ByteArray?
    suspend fun readScreenTree(): String
    suspend fun waitForIdle(timeoutMs: Long = 1500)
}
