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

    /**
     * The desktop English layout represents a physical keyboard. Sending its printable keys
     * through Fcitx can leave text in the local engine without producing commitText for proxy
     * editors, so bypass Fcitx. Chinese layouts still require the complete preedit pipeline,
     * except while Caps Lock is active: a physical Caps Lock press means uppercase letters
     * must not enter lowercase pinyin composition.
     */
    fun shouldSendPrintableDirectly(
        chineseInputMethod: Boolean,
        capsLock: Boolean = false
    ): Boolean = !chineseInputMethod || capsLock

    /** Resolve the physical key represented by a one-character desktop key definition. */
    fun shortcutKeySym(text: String): KeySym? {
        val character = text.singleOrNull() ?: return null
        val sym = KeySym(character.lowercaseChar().code)
        return sym.takeIf { it.keyCode != android.view.KeyEvent.KEYCODE_UNKNOWN }
    }

    /** Resolve the physical main key carried by a desktop shortcut action. */
    fun shortcutKeySym(action: KeyAction): KeySym? = when (action) {
        is KeyAction.FcitxKeyAction -> shortcutKeySym(action.act)
        is KeyAction.SymAction -> action.sym.takeIf {
            it.sym == FcitxKeyMapping.FcitxKey_space
        }
        else -> null
    }

    fun applyLetterCase(
        text: String,
        shift: Boolean,
        capsLock: Boolean
    ): String {
        val isLetter = text.length == 1 && text[0].isLetter()
        if (!isLetter) return text
        // Caps Lock is a physical-keyboard state: when it is on, letters are uppercase in
        // every IME. DesktopKeyboard bypasses Fcitx for these keys so they never enter preedit.
        return if (shift.xor(capsLock)) text.uppercase() else text.lowercase()
    }
}
