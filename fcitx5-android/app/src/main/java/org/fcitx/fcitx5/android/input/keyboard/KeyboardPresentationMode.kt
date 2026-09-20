package org.fcitx.fcitx5.android.input.keyboard

enum class KeyboardPresentationMode(val persistedValue: String, val layoutName: String) {
    Normal("normal", TextKeyboard.Name),
    Floating("floating", TextKeyboard.FloatingName),
    Desktop("desktop", DesktopKeyboard.Name);

    companion object {
        fun decode(value: String?): KeyboardPresentationMode =
            entries.firstOrNull { it.persistedValue == value } ?: Normal
    }
}
