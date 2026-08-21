/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.ColorInt

fun radiusDrawable(
    r: Float, @ColorInt
    color: Int = Color.WHITE
): Drawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = r
}

fun insetRadiusDrawable(
    hInset: Int,
    vInset: Int,
    r: Float = 0f,
    @ColorInt color: Int = Color.WHITE
): Drawable = InsetDrawable(
    radiusDrawable(r, color),
    hInset, vInset, hInset, vInset
)

fun insetOvalDrawable(
    hInset: Int,
    vInset: Int,
    @ColorInt color: Int = Color.WHITE
): Drawable = InsetDrawable(
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    },
    hInset, vInset, hInset, vInset
)

fun shadowedKeyBackgroundDrawable(
    @ColorInt bkgColor: Int,
    @ColorInt shadowColor: Int,
    radius: Float,
    shadowWidth: Int,
    hMargin: Int,
    vMargin: Int
): Drawable = LayerDrawable(
    arrayOf(
        radiusDrawable(radius, shadowColor),
        radiusDrawable(radius, bkgColor),
    )
).apply {
    setLayerInset(0, hMargin, vMargin, hMargin, vMargin - shadowWidth)
    setLayerInset(1, hMargin, vMargin, hMargin, vMargin)
}

fun borderedKeyBackgroundDrawable(
    @ColorInt bkgColor: Int,
    @ColorInt shadowColor: Int,
    radius: Float,
    strokeWidth: Int,
    hMargin: Int,
    vMargin: Int
): Drawable = LayerDrawable(
    arrayOf(
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bkgColor)
            setStroke(strokeWidth, shadowColor)
        }
    )
).apply {
    setLayerInset(0, hMargin, vMargin, hMargin, vMargin)
}

fun aquariumGlassKeyBackgroundDrawable(
    radius: Float,
    hMargin: Int,
    vMargin: Int
): Drawable {
    fun face(selected: Boolean): Drawable = LayerDrawable(
        arrayOf(
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x96142338.toInt(), 0x86081325.toInt())
            ).apply {
                cornerRadius = radius
                setStroke(1, if (selected) 0xE66DDCFF.toInt() else 0x806BA0C8.toInt())
            },
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    if (selected) 0x6679E2FF else 0x42FFFFFF,
                    0x00000000
                )
            ).apply {
                cornerRadius = radius * 0.9f
            }
        )
    ).apply {
        setLayerInset(0, hMargin, vMargin + 2, hMargin, vMargin)
        setLayerInset(1, hMargin + 2, vMargin + 2, hMargin + 2, vMargin + 5)
    }

    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), face(true))
        addState(intArrayOf(), face(false))
    }
}

fun aquariumSolidKeyBackgroundDrawable(
    radius: Float,
    hMargin: Int,
    vMargin: Int
): Drawable {
    fun face(selected: Boolean): Drawable = LayerDrawable(
        arrayOf(
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF020A12.toInt(), 0xFF07121E.toInt())
            ).apply {
                cornerRadius = radius
            },
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (selected) {
                    intArrayOf(0xFF297895.toInt(), 0xFF123B55.toInt())
                } else {
                    intArrayOf(0xFF29465C.toInt(), 0xFF102536.toInt())
                }
            ).apply {
                cornerRadius = radius
                setStroke(1, if (selected) 0xFF75DFFF.toInt() else 0xFF426A84.toInt())
            },
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x4DFFFFFF, 0x00FFFFFF)
            ).apply {
                cornerRadius = radius * 0.82f
            }
        )
    ).apply {
        setLayerInset(0, hMargin, vMargin + 3, hMargin, vMargin)
        setLayerInset(1, hMargin, vMargin, hMargin, vMargin + 3)
        setLayerInset(2, hMargin + 2, vMargin + 2, hMargin + 2, vMargin + 8)
    }

    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), face(true))
        addState(intArrayOf(), face(false))
    }
}
