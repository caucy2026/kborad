/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyState
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopKeyPolicyTest {
    @Test
    fun allVisibleControlKeysAreDirectWhenThereIsNoPreedit() {
        val syms = buildList {
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

        syms.forEach { sym ->
            assertTrue(DesktopKeyPolicy.isRawControl(sym))
            assertTrue(DesktopKeyPolicy.shouldSendDirectly(sym, preeditEmpty = true))
        }
    }

    @Test
    fun compositionSensitiveKeysStayInFcitxWhileComposing() {
        listOf(
            FcitxKeyMapping.FcitxKey_Escape,
            FcitxKeyMapping.FcitxKey_BackSpace,
            FcitxKeyMapping.FcitxKey_Return,
            FcitxKeyMapping.FcitxKey_Left,
            FcitxKeyMapping.FcitxKey_Right,
            FcitxKeyMapping.FcitxKey_Up,
            FcitxKeyMapping.FcitxKey_Down
        ).forEach { sym ->
            assertFalse(DesktopKeyPolicy.shouldSendDirectly(sym, preeditEmpty = false))
        }
    }

    @Test
    fun capsTabAndFunctionKeysCannotBeSwallowedByComposition() {
        assertTrue(
            DesktopKeyPolicy.shouldSendDirectly(
                FcitxKeyMapping.FcitxKey_Caps_Lock,
                preeditEmpty = false
            )
        )
        assertTrue(
            DesktopKeyPolicy.shouldSendDirectly(
                FcitxKeyMapping.FcitxKey_Tab,
                preeditEmpty = false
            )
        )
        (FcitxKeyMapping.FcitxKey_F1..FcitxKeyMapping.FcitxKey_F12).forEach { sym ->
            assertTrue(DesktopKeyPolicy.shouldSendDirectly(sym, preeditEmpty = false))
        }
    }

    @Test
    fun capsAndShiftUsePhysicalKeyboardXorForEnglishLetters() {
        assertEquals("a", DesktopKeyPolicy.applyLetterCase("a", false, false, false))
        assertEquals("A", DesktopKeyPolicy.applyLetterCase("a", false, true, false))
        assertEquals("A", DesktopKeyPolicy.applyLetterCase("a", true, false, false))
        assertEquals("a", DesktopKeyPolicy.applyLetterCase("a", true, true, false))
    }

    @Test
    fun capsDoesNotBreakLowercaseChinesePinyin() {
        assertEquals("a", DesktopKeyPolicy.applyLetterCase("a", false, true, true))
        assertEquals("A", DesktopKeyPolicy.applyLetterCase("a", true, true, true))
    }

    @Test
    fun desktopShortcutModifiersBypassFcitxButShiftAloneDoesNot() {
        assertTrue(DesktopKeyPolicy.hasShortcutModifier(setOf(KeyState.Ctrl)))
        assertTrue(DesktopKeyPolicy.hasShortcutModifier(setOf(KeyState.Alt)))
        assertTrue(DesktopKeyPolicy.hasShortcutModifier(setOf(KeyState.Meta)))
        assertTrue(
            DesktopKeyPolicy.hasShortcutModifier(setOf(KeyState.Meta, KeyState.Shift))
        )
        assertFalse(DesktopKeyPolicy.hasShortcutModifier(setOf(KeyState.Shift)))
        assertFalse(DesktopKeyPolicy.hasShortcutModifier(emptySet()))
    }

    @Test
    fun shortcutCharactersResolveToTheirPhysicalMainKeys() {
        assertEquals(KeyEvent.KEYCODE_C, DesktopKeyPolicy.shortcutKeySym("c")?.keyCode)
        assertEquals(KeyEvent.KEYCODE_V, DesktopKeyPolicy.shortcutKeySym("v")?.keyCode)
        assertEquals(KeyEvent.KEYCODE_A, DesktopKeyPolicy.shortcutKeySym("a")?.keyCode)
        assertEquals(KeyEvent.KEYCODE_3, DesktopKeyPolicy.shortcutKeySym("3")?.keyCode)
        assertEquals(KeyEvent.KEYCODE_MINUS, DesktopKeyPolicy.shortcutKeySym("-")?.keyCode)
        assertEquals(null, DesktopKeyPolicy.shortcutKeySym("Enter"))
    }

    @Test
    fun shortcutMainKeysAreAlwaysDirectEvenThoughTheyAreNotRawControls() {
        listOf("a", "c", "v", "3").forEach { text ->
            val sym = requireNotNull(DesktopKeyPolicy.shortcutKeySym(text))
            assertFalse(DesktopKeyPolicy.isRawControl(sym.sym))
            assertTrue(
                DesktopKeyPolicy.shouldSendDirectly(
                    sym.sym,
                    preeditEmpty = false,
                    shortcutChord = true
                )
            )
            assertFalse(
                DesktopKeyPolicy.shouldSendDirectly(
                    sym.sym,
                    preeditEmpty = true,
                    shortcutChord = false
                )
            )
        }
    }

    @Test
    fun shortcutControlKeysBypassActiveComposition() {
        listOf(
            FcitxKeyMapping.FcitxKey_Return,
            FcitxKeyMapping.FcitxKey_BackSpace,
            FcitxKeyMapping.FcitxKey_Left,
            FcitxKeyMapping.FcitxKey_Right
        ).forEach { sym ->
            assertTrue(
                DesktopKeyPolicy.shouldSendDirectly(
                    sym,
                    preeditEmpty = false,
                    shortcutChord = true
                )
            )
        }
    }
}
