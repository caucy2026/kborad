/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewPropertyAnimator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import org.fcitx.fcitx5.android.utils.circlePressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageResource
import splitties.views.padding

class ToolButton(context: Context) : CustomGestureView(context) {

    companion object {
        val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation
        private const val PHYSICAL_KEY_TRAVEL_DP = 3
        private const val PHYSICAL_KEY_DOWN_DURATION_MS = 50L
        private const val PHYSICAL_KEY_UP_DURATION_MS = 110L
        private val PHYSICAL_KEY_UP_INTERPOLATOR = OvershootInterpolator(0.35f)
    }

    private val image = imageView {
        isClickable = false
        isFocusable = false
        padding = dp(10)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private var physicalKeyStyleEnabled = false
    var physicalPressVisualEnabled = true
        set(value) {
            field = value
            if (physicalKeyStyleEnabled) physicalRestColor?.let(::setPhysicalRestBackground)
        }
    private var physicalPressedColor = 0
    private var physicalRestHighlightColor = 0
    private var physicalRestColor: Int? = null

    var iconRotation: Float
        get() = image.rotation
        set(value) {
            image.rotation = value
        }

    constructor(context: Context, @DrawableRes icon: Int, theme: Theme) : this(context) {
        image.imageTintList = ColorStateList.valueOf(theme.altKeyTextColor)
        setIcon(icon)
        setPressHighlightColor(theme.keyPressHighlightColor)
        add(image, lParams(wrapContent, wrapContent, gravityCenter))
    }

    fun iconAnimate(): ViewPropertyAnimator = image.animate()

    fun setIcon(@DrawableRes icon: Int) {
        image.imageResource = icon
    }

    fun setIconTintColor(@ColorInt color: Int) {
        image.imageTintList = ColorStateList.valueOf(color)
    }

    fun useFullSizeIcon(sizeDp: Int = 24) {
        image.setPadding(0, 0, 0, 0)
        image.layoutParams = LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER)
    }

    fun setPressHighlightColor(@ColorInt color: Int) {
        background = if (disableAnimation) {
            circlePressHighlightDrawable(color)
        } else {
            borderlessRippleDrawable(color, dp(20))
        }
    }

    fun setCircleBackgroundColor(@ColorInt color: Int) {
        background = InsetDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            },
            dp(6)
        )
    }

    private fun setPhysicalPressedBackground(@ColorInt fillColor: Int) {
        val borderWidth = dp(2)
        background = InsetDrawable(
            LayerDrawable(
                arrayOf(
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xFF3F8CFF.toInt(), 0xFF35E0A1.toInt())
                    ).apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(8).toFloat()
                    },
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(7).toFloat()
                        setColor(fillColor)
                    }
                )
            ).apply {
                setLayerInset(1, borderWidth, borderWidth, borderWidth, borderWidth)
            },
            dp(4)
        )
    }

    private fun setPhysicalRestBackground(@ColorInt fillColor: Int) {
        val keycap = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fillColor)
            setStroke(dp(1), 0x9958CBE8.toInt())
        }
        background = if (physicalPressVisualEnabled) {
            RippleDrawable(
                ColorStateList.valueOf(physicalRestHighlightColor),
                keycap,
                null
            )
        } else {
            keycap
        }
    }

    fun setPhysicalKeyStyle(
        enabled: Boolean,
        @ColorInt pressedColor: Int,
        @ColorInt restHighlightColor: Int,
        @ColorInt restColor: Int? = null
    ) {
        physicalKeyStyleEnabled = enabled
        physicalPressedColor = pressedColor
        physicalRestHighlightColor = restHighlightColor
        physicalRestColor = restColor
        physicalKeySoundEnabled = enabled
        animate().cancel()
        translationY = 0f
        translationZ = 0f
        elevation = if (enabled) dp(PHYSICAL_KEY_TRAVEL_DP).toFloat() else 0f
        if (!enabled) {
            physicalRestColor = null
            setPressHighlightColor(restHighlightColor)
        } else {
            restColor?.let(::setPhysicalRestBackground)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (physicalKeyStyleEnabled && physicalPressVisualEnabled && isEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animate().cancel()
                    setPhysicalPressedBackground(physicalPressedColor)
                    animate()
                        .translationY(dp(PHYSICAL_KEY_TRAVEL_DP).toFloat())
                        .translationZ(-dp(PHYSICAL_KEY_TRAVEL_DP).toFloat())
                        .setDuration(PHYSICAL_KEY_DOWN_DURATION_MS)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animate().cancel()
                    physicalRestColor?.let(::setPhysicalRestBackground)
                        ?: setPressHighlightColor(physicalRestHighlightColor)
                    animate()
                        .translationY(0f)
                        .translationZ(0f)
                        .setInterpolator(PHYSICAL_KEY_UP_INTERPOLATOR)
                        .setDuration(PHYSICAL_KEY_UP_DURATION_MS)
                        .start()
                }
            }
        }
        return super.onTouchEvent(event)
    }

}
