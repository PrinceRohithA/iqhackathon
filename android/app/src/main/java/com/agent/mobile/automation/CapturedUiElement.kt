package com.agent.mobile.automation

import android.graphics.Rect
import com.agent.shared.model.Selector

data class CapturedUiElement(
    val resourceId: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val packageName: String = "",
    val bounds: Rect = Rect(),
    val value: String = "",
) {
    fun toSelector(): Selector {
        val id = resourceId.ifBlank { null }
        val desc = contentDescription.ifBlank { null }
        val label = text.ifBlank { null }
        val cls = className.ifBlank { null }
        return when {
            id != null -> Selector(
                resourceId = id,
                text = label,
                contentDescription = desc,
                className = cls,
            )
            desc != null -> Selector(contentDescription = desc, text = label, className = cls)
            label != null -> Selector(text = label, className = cls)
            else -> Selector(className = cls, x = bounds.centerX(), y = bounds.centerY())
        }
    }
}
