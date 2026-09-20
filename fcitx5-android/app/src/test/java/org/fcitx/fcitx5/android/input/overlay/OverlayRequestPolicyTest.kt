package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRequestPolicyTest {
    @Test
    fun clampsCanvasToVendorSafeShortHeight() {
        assertEquals(665, OverlayRequestPolicy.safeCanvasHeight(1280, 1280))
        assertEquals(600, OverlayRequestPolicy.safeCanvasHeight(1280, 600))
    }

    @Test
    fun acceptsDistinctOnlineDisplaysAndBottomKeyboardBounds() {
        assertTrue(
            OverlayRequestPolicy.isValid(
                requestId = 7,
                sessionId = "remote-session",
                sourceDisplayId = 0,
                targetDisplayId = 2,
                targetWidth = 1920,
                targetHeight = 1280,
                keyboardHeight = 620,
                onlineDisplayIds = setOf(0, 2)
            )
        )
    }

    @Test
    fun rejectsSameOfflineOrOversizedDisplayRequests() {
        assertFalse(OverlayRequestPolicy.isValid(1, "s", 2, 2, 1920, 1280, 620, setOf(0, 2)))
        assertFalse(OverlayRequestPolicy.isValid(1, "s", 0, 3, 1920, 1280, 620, setOf(0, 2)))
        assertFalse(OverlayRequestPolicy.isValid(1, "s", 0, 2, 1920, 1280, 1281, setOf(0, 2)))
    }
}
