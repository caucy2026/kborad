package org.fcitx.fcitx5.android.input

internal object ExpandedKeyboardSwitchPolicy {
    const val EDITOR_MARKER = "com.newlinksz.kemi.remote.EXPANDED_KEYBOARD"
    const val SWITCH_ACTION = "com.newlinksz.kemi.remote.SWITCH_EXPANDED_KEYBOARD"

    fun shouldRequestHostRehome(privateImeOptions: String?): Boolean =
        privateImeOptions == EDITOR_MARKER
}
