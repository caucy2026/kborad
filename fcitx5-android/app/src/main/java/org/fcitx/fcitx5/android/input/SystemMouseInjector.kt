/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Injects a physical-mouse-shaped event stream into one Android display.
 *
 * KBoard's production key is the V900 platform key, so the release package can hold the
 * signature-only INJECT_EVENTS permission without sharing android.uid.system. Reflection is
 * limited to the two hidden framework entry points; all MotionEvents use the public API.
 */
internal class SystemMouseInjector(private val context: Context) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val cursorByDisplay = mutableMapOf<Int, PointF>()
    private var pressedButtonState = 0
    private var downTime = 0L
    private var unavailableReasonLogged = false

    private val inputManager: Any? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSystemService(Context.INPUT_SERVICE)
    }

    private val injectInputEvent by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        inputManager?.javaClass?.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }

    private val setDisplayId by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }

    private val setActionButton by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)
    }

    @Synchronized
    fun move(displayId: Int, dx: Int, dy: Int): Boolean {
        if (dx == 0 && dy == 0 || !isAvailable()) return false
        val bounds = displayBounds(displayId) ?: return false
        val cursor = cursorByDisplay.getOrPut(displayId) {
            PointF(bounds.first / 2f, bounds.second / 2f)
        }
        cursor.x = (cursor.x + dx).coerceIn(0f, (bounds.first - 1).coerceAtLeast(0).toFloat())
        cursor.y = (cursor.y + dy).coerceIn(0f, (bounds.second - 1).coerceAtLeast(0).toFloat())
        return inject(
            createEvent(
                displayId = displayId,
                action = MotionEvent.ACTION_HOVER_MOVE,
                x = cursor.x,
                y = cursor.y,
                buttonState = pressedButtonState,
                actionButton = 0
            )
        )
    }

    @Synchronized
    fun button(displayId: Int, button: String, down: Boolean): Boolean {
        if (!isAvailable()) return false
        val buttonMask = when (button) {
            "left" -> MotionEvent.BUTTON_PRIMARY
            "middle" -> MotionEvent.BUTTON_TERTIARY
            "right" -> MotionEvent.BUTTON_SECONDARY
            else -> return false
        }
        val bounds = displayBounds(displayId) ?: return false
        val cursor = cursorByDisplay.getOrPut(displayId) {
            PointF(bounds.first / 2f, bounds.second / 2f)
        }
        val now = SystemClock.uptimeMillis()
        if (down) {
            if (pressedButtonState and buttonMask != 0) return true
            if (pressedButtonState == 0) downTime = now
            pressedButtonState = pressedButtonState or buttonMask
            val pointerDown = createEvent(
                displayId,
                MotionEvent.ACTION_DOWN,
                cursor.x,
                cursor.y,
                pressedButtonState,
                buttonMask
            )
            val buttonPress = createEvent(
                displayId,
                MotionEvent.ACTION_BUTTON_PRESS,
                cursor.x,
                cursor.y,
                pressedButtonState,
                buttonMask
            )
            val downAccepted = inject(pointerDown)
            val pressAccepted = inject(buttonPress)
            return downAccepted && pressAccepted
        }

        if (pressedButtonState and buttonMask == 0) return true
        val buttonRelease = createEvent(
            displayId,
            MotionEvent.ACTION_BUTTON_RELEASE,
            cursor.x,
            cursor.y,
            pressedButtonState,
            buttonMask
        )
        pressedButtonState = pressedButtonState and buttonMask.inv()
        val pointerUp = createEvent(
            displayId,
            MotionEvent.ACTION_UP,
            cursor.x,
            cursor.y,
            pressedButtonState,
            buttonMask
        )
        val releaseAccepted = inject(buttonRelease)
        val upAccepted = inject(pointerUp)
        val accepted = releaseAccepted && upAccepted
        if (pressedButtonState == 0) downTime = 0L
        return accepted
    }

    @Synchronized
    fun release(displayId: Int) {
        if (pressedButtonState == 0) return
        listOf("left", "middle", "right").forEach { button ->
            button(displayId, button, false)
        }
    }

    private fun isAvailable(): Boolean {
        if (context.checkSelfPermission(INJECT_EVENTS_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        if (!unavailableReasonLogged) {
            unavailableReasonLogged = true
            Log.e(TAG, "INJECT_EVENTS is not granted; falling back to the editor mouse protocol")
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun displayBounds(displayId: Int): Pair<Int, Int>? {
        val display = displayManager?.getDisplay(displayId)?.takeIf { it.isValid } ?: return null
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun createEvent(
        displayId: Int,
        action: Int,
        x: Float,
        y: Float,
        buttonState: Int,
        actionButton: Int
    ): MotionEvent {
        val eventTime = SystemClock.uptimeMillis()
        val pointerProperties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val pointerCoords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = if (action == MotionEvent.ACTION_HOVER_MOVE) 0f else 1f
            size = 1f
        })
        val event = MotionEvent.obtain(
            downTime.takeIf { it != 0L } ?: eventTime,
            eventTime,
            action,
            1,
            pointerProperties,
            pointerCoords,
            0,
            buttonState,
            1f,
            1f,
            -1,
            0,
            InputDevice.SOURCE_MOUSE,
            0
        )
        if (actionButton != 0) setActionButton.invoke(event, actionButton)
        setDisplayId.invoke(event, displayId)
        return event
    }

    private fun inject(event: InputEvent): Boolean = try {
        val accepted = injectInputEvent?.invoke(inputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC)
            as? Boolean ?: false
        if (!accepted) Log.w(TAG, "Framework rejected mouse event")
        accepted
    } catch (error: ReflectiveOperationException) {
        Log.e(TAG, "System mouse injection failed", error)
        false
    } catch (error: SecurityException) {
        Log.e(TAG, "System mouse injection permission denied", error)
        false
    } finally {
        if (event is MotionEvent) event.recycle()
    }

    private companion object {
        const val TAG = "KBoardSystemMouse"
        const val INJECT_EVENTS_PERMISSION = "android.permission.INJECT_EVENTS"
        const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
    }
}
