package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardWindowTaskGateTest {

    @Test
    fun `task from active keyboard generation can run`() {
        val gate = KeyboardWindowTaskGate()
        val generation = gate.captureGeneration()

        assertTrue(gate.canRun(generation))
    }

    @Test
    fun `task captured before dispose cannot run afterwards`() {
        val gate = KeyboardWindowTaskGate()
        val retiredGeneration = gate.captureGeneration()

        gate.retire()

        assertFalse(gate.canRun(retiredGeneration))
    }
}
