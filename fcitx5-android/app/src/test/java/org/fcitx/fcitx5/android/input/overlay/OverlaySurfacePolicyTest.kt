package org.fcitx.fcitx5.android.input.overlay

import android.graphics.Color
import android.graphics.PixelFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySurfacePolicyTest {
    @Test
    fun carrierUsesAlphaAndLeavesUnusedVirtualDisplayPixelsTransparent() {
        assertEquals(PixelFormat.TRANSLUCENT, OverlaySurfacePolicy.pixelFormat)
        assertEquals(Color.TRANSPARENT, OverlaySurfacePolicy.backgroundColor)
    }
}
