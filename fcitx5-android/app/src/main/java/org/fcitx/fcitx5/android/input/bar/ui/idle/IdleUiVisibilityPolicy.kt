package org.fcitx.fcitx5.android.input.bar.ui.idle

internal object IdleUiVisibilityPolicy {
    fun showAnimator(desktopQuietMode: Boolean, clipboard: Boolean): Boolean =
        !desktopQuietMode || clipboard
}
