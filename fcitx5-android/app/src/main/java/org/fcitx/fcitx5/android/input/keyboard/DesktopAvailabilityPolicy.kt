/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * 全局摸鱼键盘仅允许在「输入画面与键盘不同屏」时进入。
 * 物理 Overlay 已由 OverlayRequestPolicy 强制分屏，直接放行。
 */
object DesktopAvailabilityPolicy {
    fun allowDesktop(
        physicalOverlayActive: Boolean,
        onlineDisplayCount: Int,
        imeDisplayId: Int,
        editorDisplayId: Int?
    ): Boolean {
        if (physicalOverlayActive) return true
        if (onlineDisplayCount < 2) return false
        val editor = editorDisplayId ?: return false
        return editor != imeDisplayId
    }
}
