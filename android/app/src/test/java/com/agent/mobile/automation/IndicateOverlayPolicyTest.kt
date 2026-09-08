package com.agent.mobile.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicateOverlayPolicyTest {

    @Test
    fun staysHiddenWhenNotPicking() {
        assertEquals(
            IndicateOverlayMode.HIDDEN,
            IndicateOverlayPolicy.mode(picking = false, secondsLeft = 0, ourAppFocused = false),
        )
    }

    @Test
    fun showsPausedChipWhileTimerRuns() {
        assertEquals(
            IndicateOverlayMode.PAUSED,
            IndicateOverlayPolicy.mode(picking = true, secondsLeft = 5, ourAppFocused = false),
        )
    }

    @Test
    fun keepsPausedChipWhileThisAppIsInFront() {
        assertEquals(
            IndicateOverlayMode.PAUSED,
            IndicateOverlayPolicy.mode(picking = true, secondsLeft = 0, ourAppFocused = true),
        )
    }

    @Test
    fun capturesOnlyAfterLeavingThisAppAndPauseElapses() {
        assertEquals(
            IndicateOverlayMode.CAPTURE,
            IndicateOverlayPolicy.mode(picking = true, secondsLeft = 0, ourAppFocused = false),
        )
    }

    @Test
    fun rebuildsWhenSystemDetachedTheWindow() {
        assertTrue(
            IndicateOverlayPolicy.needsRebuild(
                desired = IndicateOverlayMode.PAUSED,
                showing = IndicateOverlayMode.PAUSED,
                attached = false,
            ),
        )
    }

    @Test
    fun doesNotRebuildStableAttachedOverlay() {
        assertFalse(
            IndicateOverlayPolicy.needsRebuild(
                desired = IndicateOverlayMode.CAPTURE,
                showing = IndicateOverlayMode.CAPTURE,
                attached = true,
            ),
        )
    }

    @Test
    fun rebuildsWhenSwitchingFromPausedToCapture() {
        assertTrue(
            IndicateOverlayPolicy.needsRebuild(
                desired = IndicateOverlayMode.CAPTURE,
                showing = IndicateOverlayMode.PAUSED,
                attached = true,
            ),
        )
    }

    @Test
    fun hidesWhenPickingStops() {
        assertTrue(
            IndicateOverlayPolicy.needsRebuild(
                desired = IndicateOverlayMode.HIDDEN,
                showing = IndicateOverlayMode.CAPTURE,
                attached = true,
            ),
        )
        assertFalse(
            IndicateOverlayPolicy.needsRebuild(
                desired = IndicateOverlayMode.HIDDEN,
                showing = IndicateOverlayMode.HIDDEN,
                attached = false,
            ),
        )
    }
}
