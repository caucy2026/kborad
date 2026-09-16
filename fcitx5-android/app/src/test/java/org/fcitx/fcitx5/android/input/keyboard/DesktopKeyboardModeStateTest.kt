package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopKeyboardModeStateTest {

    @Test
    fun `repeated normal mode still reapplies normal candidate styling`() {
        val state = DesktopKeyboardModeState()
        val appliedModes = mutableListOf<Boolean>()

        val changed = state.synchronize(false, appliedModes::add)

        assertFalse(changed)
        assertEquals(listOf(false), appliedModes)
    }

    @Test
    fun `mode change updates state and reapplies candidate styling`() {
        val state = DesktopKeyboardModeState()
        val appliedModes = mutableListOf<Boolean>()

        val changed = state.synchronize(true, appliedModes::add)

        assertTrue(changed)
        assertTrue(state.enabled)
        assertEquals(listOf(true), appliedModes)
    }
}
