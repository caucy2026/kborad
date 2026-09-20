package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalOverlaySystemNavigationPolicyTest {
    @Test
    fun closesOnlyForHomeAndQuickstepRecentReasons() {
        assertTrue(PhysicalOverlaySystemNavigationPolicy.shouldClose("homekey"))
        assertTrue(PhysicalOverlaySystemNavigationPolicy.shouldClose("recentapps"))
        assertFalse(PhysicalOverlaySystemNavigationPolicy.shouldClose(null))
        assertFalse(PhysicalOverlaySystemNavigationPolicy.shouldClose("globalactions"))
        assertFalse(PhysicalOverlaySystemNavigationPolicy.shouldClose("dream"))
    }

    @Test
    fun staleNavigationEventCannotCloseReplacementOwner() {
        assertTrue(
            PhysicalOverlaySystemNavigationPolicy.ownsEvent(
                eventRequestId = 65,
                eventSessionId = "session-65",
                ownerRequestId = 65,
                ownerSessionId = "session-65"
            )
        )
        assertFalse(
            PhysicalOverlaySystemNavigationPolicy.ownsEvent(
                eventRequestId = 65,
                eventSessionId = "session-65",
                ownerRequestId = 66,
                ownerSessionId = "session-66"
            )
        )
    }
}
