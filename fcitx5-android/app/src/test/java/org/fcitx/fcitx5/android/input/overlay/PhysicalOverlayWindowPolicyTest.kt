package org.fcitx.fcitx5.android.input.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalOverlayWindowPolicyTest {
    @Test
    fun bottomWindowIsTransparentTouchableAndDoesNotTakeApplicationFocus() {
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, PhysicalOverlayWindowPolicy.type)
        assertEquals(PixelFormat.TRANSLUCENT, PhysicalOverlayWindowPolicy.format)
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, PhysicalOverlayWindowPolicy.gravity)
        assertTrue(PhysicalOverlayWindowPolicy.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(PhysicalOverlayWindowPolicy.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }

    @Test
    fun systemNavigationGesturesStayVisibleWhileOverlayDrawsBehindTheirInsets() {
        assertEquals(0, PhysicalOverlayWindowPolicy.fitInsetsTypes)
        assertTrue(PhysicalOverlayWindowPolicy.preserveSystemNavigationGestures)
        assertTrue(PhysicalOverlayWindowPolicy.showSystemNavigationBars)
    }
}
