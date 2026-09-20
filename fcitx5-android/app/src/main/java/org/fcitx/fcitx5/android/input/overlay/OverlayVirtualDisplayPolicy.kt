package org.fcitx.fcitx5.android.input.overlay

import android.hardware.display.DisplayManager

/** Android 12 hidden display flags required for a private display to own focus and host an IME. */
internal object OverlayVirtualDisplayPolicy {
    const val FLAG_SUPPORTS_TOUCH = 1 shl 6
    const val FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 shl 9
    const val FLAG_TRUSTED = 1 shl 10
    const val FLAG_OWN_DISPLAY_GROUP = 1 shl 11

    const val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
        FLAG_SUPPORTS_TOUCH or
        FLAG_TRUSTED or
        FLAG_OWN_DISPLAY_GROUP
}
