/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeDisplaySwitchPolicyTest {

    @Test
    fun expandedKemiEditorUsesHostCoordinatedSwitch() {
        assertEquals(
            ImeDisplaySwitchPolicy.Route.KEMI_EXPANDED_HOST,
            ImeDisplaySwitchPolicy.routeFor("com.newlinksz.kemi.remote.EXPANDED_KEYBOARD")
        )
    }

    @Test
    fun ordinaryEditorUsesSystemSwitch() {
        assertEquals(
            ImeDisplaySwitchPolicy.Route.SYSTEM,
            ImeDisplaySwitchPolicy.routeFor(null)
        )
        assertEquals(
            ImeDisplaySwitchPolicy.Route.SYSTEM,
            ImeDisplaySwitchPolicy.routeFor("com.example.UNRELATED")
        )
    }

    @Test
    fun similarMarkerDoesNotEnterExpandedHostRoute() {
        assertEquals(
            ImeDisplaySwitchPolicy.Route.SYSTEM,
            ImeDisplaySwitchPolicy.routeFor("com.newlinksz.kemi.remote.EXPANDED_KEYBOARD_OTHER")
        )
    }
}
