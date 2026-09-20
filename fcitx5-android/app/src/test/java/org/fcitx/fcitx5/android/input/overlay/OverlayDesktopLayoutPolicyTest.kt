package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDesktopLayoutPolicyTest {
    @Test
    fun shortCanvasKeepsAllRowsMouseAreaAndExitBarInsideBounds() {
        val result = OverlayDesktopLayoutPolicy.calculate(
            canvasWidthPx = 1920,
            canvasHeightPx = 665,
            physicalTargetHeightPx = 1280,
            density = 2f
        )

        assertEquals(665, result.keyboardHeightPx)
        assertEquals(112, result.operationHeightPx)
        assertTrue(result.touchpadHeightPx >= 96)
        assertTrue(result.rowHeightPx >= 64)
        assertTrue(result.usedHeightPx <= 665)
    }
}
