/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * Tracks desktop-keyboard mode while always synchronizing view-owned styling.
 *
 * Input views and candidate components can be recreated independently. Therefore a repeated mode
 * value is not a no-op for styling: the newly active view must still receive the current mode.
 */
internal class DesktopKeyboardModeState {
    var enabled: Boolean = false
        private set

    fun synchronize(next: Boolean, applyVisualMode: (Boolean) -> Unit): Boolean {
        applyVisualMode(next)
        val changed = enabled != next
        enabled = next
        return changed
    }
}
