/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/** Private InputConnection command shared with KEMI's cross-display keyboard host. */
object RemoteMouseInputProtocol {
    const val ACTION = "org.fcitx.fcitx5.android.REMOTE_MOUSE"
    const val EXTRA_TYPE = "type"
    const val EXTRA_DX = "dx"
    const val EXTRA_DY = "dy"
    const val EXTRA_BUTTON = "button"
    const val EXTRA_DOWN = "down"

    const val TYPE_MOVE = "move"
    const val TYPE_BUTTON = "button"

    const val BUTTON_LEFT = "left"
    const val BUTTON_MIDDLE = "middle"
    const val BUTTON_RIGHT = "right"

    val BUTTONS = setOf(BUTTON_LEFT, BUTTON_MIDDLE, BUTTON_RIGHT)
}
