/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.fcitx.fcitx5.android.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Frosted relative touchpad used only by [DesktopKeyboard].
 *
 * Pointer deltas are accumulated and emitted at most once per display frame. Mouse buttons keep
 * real DOWN/UP lifetimes, so a second finger can hold a button while the first finger drags.
 */
class DesktopTouchpadView(context: Context) : View(context), Choreographer.FrameCallback {
    var onMouseMove: ((Int, Int) -> Unit)? = null
    var onMouseButton: ((String, Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val cornerRadius = 14f * density
    private val outerInset = 5f * density
    private val gap = 4f * density
    private val buttonHeight = 35f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val panelRect = RectF()
    private val touchRect = RectF()
    private val buttonRects = Array(3) { RectF() }
    private val buttonNames = arrayOf(
        RemoteMouseInputProtocol.BUTTON_LEFT,
        RemoteMouseInputProtocol.BUTTON_MIDDLE,
        RemoteMouseInputProtocol.BUTTON_RIGHT
    )
    private val buttonLabels = arrayOf(
        context.getString(R.string.desktop_mouse_left),
        context.getString(R.string.desktop_mouse_middle),
        context.getString(R.string.desktop_mouse_right)
    )
    private val touchpadHint = context.getString(R.string.desktop_mouse_touchpad_hint)

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = 0x66D8F2FF
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        color = 0x3DD8F2FF
    }
    private val touchAreaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD92A526B.toInt()
    }
    private val touchAreaBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xD96DE1FF.toInt()
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f * density
        color = 0x6DEAF8FF
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB8EAF7FF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }

    private var movementPointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var frameScheduled = false
    private val pressedButtons = linkedMapOf<Int, Int>()
    private var compactHorizontalLayout = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.desktop_mouse_touchpad_description)
        // The V900's Android 12 compositor may retain a zero-sized hardware layer when this
        // view is created before the dynamic desktop header is measured. The panel is mostly
        // static Canvas drawing, so a software layer is both cheap and deterministic here.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        panelRect.set(outerInset, outerInset, w - outerInset, h - outerInset)
        val stackedMinimumHeight = buttonHeight + gap + 42f * density
        compactHorizontalLayout = panelRect.height() < stackedMinimumHeight
        if (!compactHorizontalLayout) {
            val stripTop = max(panelRect.top + 28f * density, panelRect.bottom - buttonHeight)
            touchRect.set(panelRect.left, panelRect.top, panelRect.right, stripTop - gap)
            val buttonWidth = (panelRect.width() - gap * 2f) / 3f
            buttonRects.forEachIndexed { index, rect ->
                val left = panelRect.left + index * (buttonWidth + gap)
                rect.set(left, stripTop, left + buttonWidth, panelRect.bottom)
            }
        } else {
            // Android 12 V900 constrains the IME header to 48dp. In that compact shape, place
            // mouse buttons beside the pad instead of silently clipping them below the view.
            val controlsWidth = panelRect.width() * 0.36f
            val controlsLeft = panelRect.right - controlsWidth
            touchRect.set(panelRect.left, panelRect.top, controlsLeft - gap, panelRect.bottom)
            val buttonWidth = (controlsWidth - gap * 2f) / 3f
            buttonRects.forEachIndexed { index, rect ->
                val left = controlsLeft + index * (buttonWidth + gap)
                rect.set(left, panelRect.top, left + buttonWidth, panelRect.bottom)
            }
        }
        glassPaint.shader = LinearGradient(
            panelRect.left,
            panelRect.top,
            panelRect.right,
            panelRect.bottom,
            intArrayOf(0xC02B5067.toInt(), 0xAA183B52.toInt(), 0xC025465D.toInt()),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, glassPaint)
        canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, borderPaint)

        if (touchRect.height() > 0f) {
            canvas.drawRoundRect(touchRect, 10f * density, 10f * density, touchAreaPaint)
            canvas.drawRoundRect(touchRect, 10f * density, 10f * density, touchAreaBorderPaint)
            val guideHalfWidth = min(touchRect.width() * 0.1f, 60f * density)
            val guideHalfHeight = min(touchRect.height() * 0.18f, 14f * density)
            canvas.drawLine(
                touchRect.centerX() - guideHalfWidth,
                touchRect.centerY(),
                touchRect.centerX() + guideHalfWidth,
                touchRect.centerY(),
                guidePaint
            )
            canvas.drawLine(
                touchRect.centerX(),
                touchRect.centerY() - guideHalfHeight,
                touchRect.centerX(),
                touchRect.centerY() + guideHalfHeight,
                guidePaint
            )
            canvas.drawText(
                touchpadHint,
                touchRect.centerX(),
                touchRect.centerY() - guideHalfHeight - hintPaint.descent() - 3f * density,
                hintPaint
            )
        }

        buttonRects.forEachIndexed { index, rect ->
            buttonPaint.color = if (index in pressedButtons.values) {
                if (index == 2) 0xD837A56D.toInt() else 0xD83B82F6.toInt()
            } else {
                0xEE17384D.toInt()
            }
            canvas.drawRoundRect(rect, 8f * density, 8f * density, buttonPaint)
            canvas.drawRoundRect(rect, 8f * density, 8f * density, touchAreaBorderPaint)
            canvas.drawText(
                buttonLabels[index],
                rect.centerX(),
                rect.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f -
                    (if (compactHorizontalLayout) 8f * density else 0f),
                labelPaint
            )
            if (index < buttonRects.lastIndex) {
                val x = (rect.right + buttonRects[index + 1].left) / 2f
                canvas.drawLine(x, rect.top + gap, x, rect.bottom - gap, dividerPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                handlePointerDown(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> handlePointerMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                handlePointerUp(event.getPointerId(index))
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> releaseAllPointers()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handlePointerDown(pointerId: Int, x: Float, y: Float) {
        val buttonIndex = buttonRects.indexOfFirst { it.contains(x, y) }
        if (buttonIndex >= 0) {
            if (buttonIndex in pressedButtons.values) return
            pressedButtons[pointerId] = buttonIndex
            onMouseButton?.invoke(buttonNames[buttonIndex], true)
            invalidate()
            return
        }
        if (movementPointerId == MotionEvent.INVALID_POINTER_ID && touchRect.contains(x, y)) {
            movementPointerId = pointerId
            lastX = x
            lastY = y
        }
    }

    private fun handlePointerMove(event: MotionEvent) {
        val pointerIndex = event.findPointerIndex(movementPointerId)
        if (pointerIndex < 0) return
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y
        if (dx == 0f && dy == 0f) return
        val distance = hypot(dx, dy)
        val acceleration = when {
            distance < touchSlop * 0.35f -> 0.72f
            distance < touchSlop -> 1.0f
            else -> min(2.25f, 1f + (distance - touchSlop) / (touchSlop * 2.5f))
        }
        pendingDx += dx * acceleration
        pendingDy += dy * acceleration
        scheduleFrame()
    }

    private fun handlePointerUp(pointerId: Int) {
        pressedButtons.remove(pointerId)?.let { buttonIndex ->
            onMouseButton?.invoke(buttonNames[buttonIndex], false)
            invalidate()
        }
        if (pointerId == movementPointerId) {
            movementPointerId = MotionEvent.INVALID_POINTER_ID
            flushMotion()
        }
    }

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        flushMotion()
        if (abs(pendingDx) >= 0.5f || abs(pendingDy) >= 0.5f) scheduleFrame()
    }

    private fun flushMotion() {
        val dx = pendingDx.roundToInt()
        val dy = pendingDy.roundToInt()
        if (dx == 0 && dy == 0) return
        pendingDx -= dx
        pendingDy -= dy
        onMouseMove?.invoke(dx.coerceIn(-240, 240), dy.coerceIn(-240, 240))
    }

    private fun releaseAllPointers() {
        pressedButtons.values.toList().forEach { onMouseButton?.invoke(buttonNames[it], false) }
        pressedButtons.clear()
        movementPointerId = MotionEvent.INVALID_POINTER_ID
        flushMotion()
        invalidate()
    }

    fun releaseInputState() = releaseAllPointers()

    fun dispose() {
        releaseAllPointers()
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(this)
        frameScheduled = false
        onMouseMove = null
        onMouseButton = null
    }

}
