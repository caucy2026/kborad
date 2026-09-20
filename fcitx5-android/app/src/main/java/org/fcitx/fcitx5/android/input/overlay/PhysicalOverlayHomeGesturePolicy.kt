package org.fcitx.fcitx5.android.input.overlay

import kotlin.math.abs

internal object PhysicalOverlayHomeGesturePolicy {
    fun shouldClose(
        startedInBottomStrip: Boolean,
        deltaX: Float,
        deltaY: Float,
        durationMillis: Long,
        maximumPointerCount: Int,
        minimumUpwardDistancePx: Float = 192f
    ): Boolean {
        if (!startedInBottomStrip || maximumPointerCount != 1) return false
        if (durationMillis !in 1L..1_500L) return false
        if (deltaY > -minimumUpwardDistancePx) return false
        return abs(deltaX) <= abs(deltaY) * 0.75f
    }
}
