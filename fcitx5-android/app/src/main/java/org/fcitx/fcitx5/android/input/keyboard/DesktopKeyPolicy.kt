/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeySym

/** Routing and letter-case rules used only by the full desktop keyboard. */
internal object DesktopKeyPolicy {
    private val rawControlKeySyms = buildSet {
        add(FcitxKeyMapping.FcitxKey_Escape)
        addAll(FcitxKeyMapping.FcitxKey_F1..FcitxKeyMapping.FcitxKey_F12)
        add(FcitxKeyMapping.FcitxKey_BackSpace)
        add(FcitxKeyMapping.FcitxKey_Tab)
        add(FcitxKeyMapping.FcitxKey_Caps_Lock)
        add(FcitxKeyMapping.FcitxKey_Return)
        add(FcitxKeyMapping.FcitxKey_Left)
        add(FcitxKeyMapping.FcitxKey_Right)
        add(FcitxKeyMapping.FcitxKey_Up)
        add(FcitxKeyMapping.FcitxKey_Down)
    }

    // These keys have no useful role in a Chinese preedit and must never be swallowed by Fcitx.
    private val alwaysDirectKeySyms = buildSet {
        addAll(FcitxKeyMapping.FcitxKey_F1..FcitxKeyMapping.FcitxKey_F12)
        add(FcitxKeyMapping.FcitxKey_Tab)
        add(FcitxKeyMapping.FcitxKey_Caps_Lock)
    }

    fun isRawControl(sym: Int): Boolean = sym in rawControlKeySyms

    fun shouldSendDirectly(
        sym: Int,
        preeditEmpty: Boolean,
        shortcutChord: Boolean = false
    ): Boolean = shortcutChord ||
        (sym in rawControlKeySyms && (preeditEmpty || sym in alwaysDirectKeySyms))

    /**
     * Ctrl/Alt/Meta shortcuts must reach the target editor as physical-style key events instead
     * of entering the local Fcitx composition pipeline. Shift alone remains a text modifier.
     */
    fun hasShortcutModifier(states: Set<KeyState>): Boolean =
        states.any { it == KeyState.Ctrl || it == KeyState.Alt || it == KeyState.Meta }

    /** Resolve the physical key represented by a one-character desktop key definition. */
    fun shortcutKeySym(text: String): KeySym? {
        val character = text.singleOrNull() ?: return null
        val sym = KeySym(character.lowercaseChar().code)
        return sym.takeIf { it.keyCode != android.view.KeyEvent.KEYCODE_UNKNOWN }
    }

    fun applyLetterCase(
        text: String,
        shift: Boolean,
        capsLock: Boolean,
        chineseInputMethod: Boolean
    ): String {
        val isLetter = text.length == 1 && text[0].isLetter()
        if (!isLetter) return text
        val capsAffectsText = capsLock && !chineseInputMethod
        return if (shift.xor(capsAffectsText)) text.uppercase() else text.lowercase()
    }
}
