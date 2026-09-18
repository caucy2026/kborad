package org.fcitx.fcitx5.android.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringAccessGateTest {

    @Test
    fun `password prompt is requested on seventh version tap`() {
        val gate = EngineeringAccessGate()

        repeat(6) {
            assertFalse(gate.onVersionTapped())
        }

        assertTrue(gate.onVersionTapped())
    }

    @Test
    fun `wrong password does not unlock engineering settings`() {
        val gate = EngineeringAccessGate()

        assertFalse(gate.unlock("0000"))
        assertFalse(gate.isUnlocked)
    }

    @Test
    fun `engineering password unlocks settings for current process`() {
        val gate = EngineeringAccessGate()

        assertTrue(gate.unlock("2580"))
        assertTrue(gate.isUnlocked)
    }
}
