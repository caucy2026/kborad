package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayVirtualDisplayPolicyTest {
    @Test
    fun privateImeDisplayHasTrustedFocusTouchAndSystemDecorationCapabilities() {
        val flags = OverlayVirtualDisplayPolicy.flags

        assertTrue(flags and OverlayVirtualDisplayPolicy.FLAG_SUPPORTS_TOUCH != 0)
        assertEquals(0, flags and OverlayVirtualDisplayPolicy.FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS)
        assertTrue(flags and OverlayVirtualDisplayPolicy.FLAG_TRUSTED != 0)
        assertTrue(flags and OverlayVirtualDisplayPolicy.FLAG_OWN_DISPLAY_GROUP != 0)
        assertEquals(0, flags and android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)
    }
}
