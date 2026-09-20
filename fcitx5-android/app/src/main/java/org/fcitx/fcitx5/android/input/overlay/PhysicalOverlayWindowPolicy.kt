package org.fcitx.fcitx5.android.input.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

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
}
