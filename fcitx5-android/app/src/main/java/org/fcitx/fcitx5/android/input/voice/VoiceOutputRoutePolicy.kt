/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.voice

internal object VoiceOutputRoutePolicy {
    fun useDirectCommit(desktopKeyboardMode: Boolean, physicalOverlay: Boolean): Boolean =
        desktopKeyboardMode || physicalOverlay

    fun useEditorComposition(desktopKeyboardMode: Boolean, physicalOverlay: Boolean): Boolean =
        !useDirectCommit(desktopKeyboardMode, physicalOverlay)
}
