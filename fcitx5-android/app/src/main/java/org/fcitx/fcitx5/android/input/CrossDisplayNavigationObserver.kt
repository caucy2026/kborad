package org.fcitx.fcitx5.android.input

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import org.fcitx.fcitx5.android.utils.navbarFrameHeight
import kotlin.math.abs

/** Observe the IME window, including the system navigation strip outside InputView. */
internal class CrossDisplayNavigationObserver(
    private val original: Window.Callback,
    private val decor: View,
    private val enabled: () -> Boolean,
    private val hide: () -> Unit
) : Window.Callback by original {
    private var tracking = false
    private var startX = 0f
    private var startY = 0f
    private var lastY = 0f
    private var lastX = 0f
    private var startedAt = 0L
    private val slop = ViewConfiguration.get(decor.context).scaledTouchSlop

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            tracking = enabled() && event.pointerCount == 1 &&
                event.y >= decor.height - decor.context.navbarFrameHeight() &&
                abs(event.x - decor.width / 2f) < decor.width / 4f
            startX = event.x
            startY = event.y
            lastX = event.x
            lastY = event.y
            startedAt = event.eventTime
        }
        if (event.pointerCount > 1) tracking = false
        // CANCEL carries the final pointer position when SystemUI takes the gesture.
        // The last MOVE delivered to this window can still be below touch slop.
        if (tracking) {
            lastX = event.x
            lastY = event.y
        }
        val terminal = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        val close = tracking && terminal && enabled() &&
            event.eventTime - startedAt < 1500 &&
            startY - lastY > slop && abs(lastX - startX) < startY - lastY
        if (tracking && terminal) {
            Log.i("KBoardCrossNav", "action=${event.actionMasked} dy=${startY-lastY} dx=${lastX-startX} threshold=$slop close=$close")
        }
        // Preserve Android/SystemUI dispatch and do not synthesize HOME or consume the gesture.
        val handled = original.dispatchTouchEvent(event)
        if (terminal) tracking = false
        if (close) decor.post { if (enabled()) hide() }
        return handled
    }
}
