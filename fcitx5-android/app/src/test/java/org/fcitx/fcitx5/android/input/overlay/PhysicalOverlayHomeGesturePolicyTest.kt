package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalOverlayHomeGesturePolicyTest {
    @Test
    fun acceptsSinglePointerVerticalSwipeFromBottomSystemGestureStrip() {
        assertTrue(
            PhysicalOverlayHomeGesturePolicy.shouldClose(
                startedInBottomStrip = true,
                deltaX = 0f,
                deltaY = -562f,
                durationMillis = 514L,
                maximumPointerCount = 1
            )
        )
    }

    @Test
    fun rejectsTapHorizontalSlowMultiPointerAndNonBottomGestures() {
        assertFalse(PhysicalOverlayHomeGesturePolicy.shouldClose(true, 0f, -8f, 80L, 1))
        assertFalse(PhysicalOverlayHomeGesturePolicy.shouldClose(true, 400f, -100f, 300L, 1))
        assertFalse(PhysicalOverlayHomeGesturePolicy.shouldClose(true, 0f, -562f, 2_001L, 1))
        assertFalse(PhysicalOverlayHomeGesturePolicy.shouldClose(true, 0f, -562f, 514L, 2))
        assertFalse(PhysicalOverlayHomeGesturePolicy.shouldClose(false, 0f, -562f, 514L, 1))
        assertFalse(PhysicalOverlayHomeGesturePolicy.shouldClose(true, 0f, 562f, 514L, 1))
    }
}
