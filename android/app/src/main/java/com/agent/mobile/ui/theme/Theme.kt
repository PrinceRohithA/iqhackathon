package com.agent.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF2DD4BF)
private val TealDim = Color(0xFF0F766E)
private val Ink = Color(0xFF0B1220)
private val Panel = Color(0xFF111827)
private val Danger = Color(0xFFF87171)

private val Scheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF042F2E),
    secondary = TealDim,
    background = Ink,
    surface = Panel,
    surfaceVariant = Color(0xFF1F2937),
    error = Danger,
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF9CA3AF),
)

@Composable
fun AgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
