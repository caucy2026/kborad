/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Routes the visible D0 navigation-bar hide affordance back to a D2-owned IME session.
 *
 * V900 Android 12 draws the IME on D0 after applying fallback display policy, but continues to
 * dispatch D0 BACK to the launcher because the served editor remains on D2. A transparent,
 * system-UID-only hit target above the existing bottom-left navigation button preserves the
 * system artwork while forwarding only that button to KBoard. It exists only during a confirmed
 * D2 -> D0 IME route and is removed on hide or the reverse route.
 */
internal object DesktopNavigationHideBridge {
    private const val TAG = "KBoardNavHideBridge"
    private const val DEFAULT_DISPLAY = 0
    private const val NAVIGATION_BAR_PANEL_TYPE = 2024
    private const val WIDTH_DP = 96
    private const val HEIGHT_DP = 48
    private const val RELAY_GRACE_MS = 2_500L
    private const val CONFIRM_TIMEOUT_MS = 6_000L

    private var windowManager: WindowManager? = null
    private var hitTarget: View? = null
    private var relayGraceUntil = 0L
    private var confirmed = false

    fun arm(context: Context) {
        relayGraceUntil = SystemClock.uptimeMillis() + RELAY_GRACE_MS
        confirmed = false
        if (hitTarget != null) {
            scheduleTimeout()
            return
        }
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(DEFAULT_DISPLAY) ?: return
        val displayContext = context.applicationContext.createDisplayContext(display)
        val density = displayContext.resources.displayMetrics.density
        val view = View(displayContext).apply {
            contentDescription = "Hide KBoard"
            setOnClickListener {
                disarm()
                FcitxInputMethodService.hideFromNavigationBridge()
            }
        }
        val params = WindowManager.LayoutParams(
            (WIDTH_DP * density).toInt(),
            (HEIGHT_DP * density).toInt(),
            NAVIGATION_BAR_PANEL_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            title = "KBoard navigation hide bridge"
        }
        val manager = displayContext.getSystemService(WindowManager::class.java)
        try {
            manager.addView(view, params)
            windowManager = manager
            hitTarget = view
            scheduleTimeout()
            Log.i(TAG, "armed on display 0")
        } catch (error: RuntimeException) {
            Log.e(TAG, "unable to add navigation hide bridge", error)
        }
    }

    fun confirm(displayId: Int) {
        if (displayId != DEFAULT_DISPLAY) {
            disarm()
            return
        }
        if (hitTarget == null) return
        confirmed = true
        relayGraceUntil = 0L
        hitTarget?.removeCallbacks(timeout)
        Log.i(TAG, "confirmed on display 0")
    }

    fun onImeWindowHidden() {
        if (SystemClock.uptimeMillis() < relayGraceUntil) return
        disarm()
    }

    fun disarm() {
        val view = hitTarget ?: return
        view.removeCallbacks(timeout)
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "failed to remove navigation hide bridge", it) }
        hitTarget = null
        windowManager = null
        relayGraceUntil = 0L
        confirmed = false
        Log.i(TAG, "disarmed")
    }

    private val timeout = Runnable {
        if (!confirmed) disarm()
    }

    private fun scheduleTimeout() {
        hitTarget?.removeCallbacks(timeout)
        hitTarget?.postDelayed(timeout, CONFIRM_TIMEOUT_MS)
    }
}
