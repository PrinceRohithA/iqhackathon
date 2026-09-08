package com.agent.mobile.automation

enum class IndicateOverlayMode {
    HIDDEN,
    PAUSED,
    CAPTURE,
}

/**
 * Decide which indicate overlay to show. The paused chip stays up while this app
 * is in front so Indicate never kicks the user to Home with nothing on screen.
 * Full-screen capture starts only after the pause elapses and another app is focused.
 */
object IndicateOverlayPolicy {
    fun mode(
        picking: Boolean,
        secondsLeft: Int,
        ourAppFocused: Boolean,
    ): IndicateOverlayMode {
        if (!picking) return IndicateOverlayMode.HIDDEN
        if (secondsLeft > 0 || ourAppFocused) return IndicateOverlayMode.PAUSED
        return IndicateOverlayMode.CAPTURE
    }

    fun needsRebuild(
        desired: IndicateOverlayMode,
        showing: IndicateOverlayMode,
        attached: Boolean,
    ): Boolean {
        if (desired == IndicateOverlayMode.HIDDEN) return showing != IndicateOverlayMode.HIDDEN
        if (!attached || showing == IndicateOverlayMode.HIDDEN) return true
        return desired != showing
    }
}
