package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayImeStartGateTest {
    @Test
    fun waitsForAttachAndWindowFocusThenRestartsUntilInputConnectionExists() {
        val gate = OverlayImeStartGate(maxAttempts = 3)

        assertEquals(OverlayImeStartGate.Action.WAIT, gate.nextAction())
        gate.attached = true
        assertEquals(OverlayImeStartGate.Action.WAIT, gate.nextAction())
        gate.windowFocused = true
        assertEquals(OverlayImeStartGate.Action.RESTART_INPUT, gate.nextAction())
        gate.inputConnectionReady = true
        assertEquals(OverlayImeStartGate.Action.RESTART_AND_SHOW, gate.nextAction())
    }

    @Test
    fun stopsAfterBoundedAttemptsAndStopsImmediatelyAfterAcceptedShow() {
        val gate = OverlayImeStartGate(maxAttempts = 2).apply {
            attached = true
            windowFocused = true
        }

        assertEquals(OverlayImeStartGate.Action.RESTART_INPUT, gate.nextAction())
        assertEquals(OverlayImeStartGate.Action.RESTART_INPUT, gate.nextAction())
        assertEquals(OverlayImeStartGate.Action.EXHAUSTED, gate.nextAction())

        val accepted = OverlayImeStartGate().apply {
            attached = true
            windowFocused = true
            inputConnectionReady = true
        }
        assertEquals(OverlayImeStartGate.Action.RESTART_AND_SHOW, accepted.nextAction())
        accepted.markShowAccepted()
        assertTrue(accepted.completed)
        assertEquals(OverlayImeStartGate.Action.WAIT, accepted.nextAction())
    }
}
