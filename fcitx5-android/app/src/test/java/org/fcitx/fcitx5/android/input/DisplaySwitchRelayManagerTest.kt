/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaySwitchRelayManagerTest {

    private val mainIme = "org.fcitx.fcitx5.android/.input.FcitxInputMethodService"
    private val relayIme = "org.fcitx.fcitx5.android/.input.DisplaySwitchInputMethodService"

    @Test
    fun appendRelayPreservesExistingImeAndSubtype() {
        val existing = "com.example/.Ime;subtypeA;subtypeB:$mainIme"

        assertEquals(
            "$existing:$relayIme",
            DisplaySwitchRelayManager.appendIme(existing, relayIme)
        )
    }

    @Test
    fun appendRelayDoesNotDuplicateExistingEntry() {
        val existing = "$mainIme:$relayIme"

        assertEquals(existing, DisplaySwitchRelayManager.appendIme(existing, relayIme))
    }

    @Test
    fun containsImeIgnoresSubtypeSuffix() {
        assertTrue(DisplaySwitchRelayManager.containsIme("$relayIme;subtype", relayIme))
        assertFalse(DisplaySwitchRelayManager.containsIme(mainIme, relayIme))
    }
}
