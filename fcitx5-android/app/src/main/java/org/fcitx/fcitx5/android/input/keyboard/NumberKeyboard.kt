/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class NumberKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "Number"

        // Keep 123-page label sizes close to Gboard with balanced visual weight.
        private const val DigitTextSize = 26f
        private const val OperatorTextSize = 23f

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                NumPadKey("+", 0xffab, OperatorTextSize, 0.15f, KeyDef.Appearance.Variant.Alternative),
                NumPadKey("1", 0xffb1, DigitTextSize, 0f),
                NumPadKey("2", 0xffb2, DigitTextSize, 0f),
                NumPadKey("3", 0xffb3, DigitTextSize, 0f),
                NumPadKey("/", 0xffaf, OperatorTextSize, 0.15f, KeyDef.Appearance.Variant.Alternative),
            ),
            listOf(
                NumPadKey("-", 0xffad, OperatorTextSize, 0.15f, KeyDef.Appearance.Variant.Alternative),
                NumPadKey("4", 0xffb4, DigitTextSize, 0f),
                NumPadKey("5", 0xffb5, DigitTextSize, 0f),
                NumPadKey("6", 0xffb6, DigitTextSize, 0f),
                MiniSpaceKey()
            ),
            listOf(
                NumPadKey("*", 0xffaa, OperatorTextSize, 0.15f, KeyDef.Appearance.Variant.Alternative),
                NumPadKey("7", 0xffb7, DigitTextSize, 0f),
                NumPadKey("8", 0xffb8, DigitTextSize, 0f),
                NumPadKey("9", 0xffb9, DigitTextSize, 0f),
                BackspaceKey()
            ),
            listOf(
                LayoutSwitchKey("ABC", TextKeyboard.Name),
                NumPadKey(",", 0xffac, OperatorTextSize, 0.1f, KeyDef.Appearance.Variant.Alternative),
                LayoutSwitchKey("!?#", PickerWindow.Key.Symbol.name, 0.13333f, KeyDef.Appearance.Variant.AltForeground),
                NumPadKey("0", 0xffb0, DigitTextSize, 0.23334f),
                NumPadKey("=", 0xffbd, OperatorTextSize, 0.13333f, KeyDef.Appearance.Variant.AltForeground),
                NumPadKey(".", 0xffae, OperatorTextSize, 0.1f, KeyDef.Appearance.Variant.Alternative),
                ReturnKey()
            )
        )
    }

    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val space: TextKeyView by lazy { findViewById(R.id.button_mini_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    @SuppressLint("MissingSuperCall")
    override fun onPopupAction(action: PopupAction) {
        // leave empty on purpose to disable popup in NumberKeyboard
    }

}