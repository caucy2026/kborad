package org.fcitx.fcitx5.android.input.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import org.fcitx.fcitx5.android.input.keyboard.DesktopKeyboard

internal object PhysicalOverlayWindowPolicy {
    const val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    const val format = PixelFormat.TRANSLUCENT
    const val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    const val gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    const val fitInsetsTypes = 0
    const val preserveSystemNavigationGestures = true
    const val showSystemNavigationBars = true

    /**
     * Desktop mode owns the complete physical panel. Navigation-bar avoidance is already
     * applied inside InputView, so shrinking this bottom-gravity window would clip its top.
     */
    fun resolveHeight(layoutName: String, requestedHeight: Int, physicalHeight: Int): Int {
        require(physicalHeight > 0)
        return if (layoutName == DesktopKeyboard.Name) {
            physicalHeight
        } else {
            requestedHeight.coerceIn(1, physicalHeight)
        }
    }
}
