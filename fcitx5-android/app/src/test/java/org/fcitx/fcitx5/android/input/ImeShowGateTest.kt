/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input

import org.junit.Assert.*
import org.junit.Test

class ImeShowGateTest {
    @Test fun noTokenNeverShows() {
        val gate = ImeShowGate()
        gate.startInput(true)
        assertFalse(gate.request(1))
        assertNull(gate.takePending(gate.generation))
    }

    @Test fun lateTokenResumesLatestIntentOnce() {
        val gate = ImeShowGate()
        gate.startInput(true)
        assertFalse(gate.request(1))
        assertFalse(gate.request(2))
        gate.tokenAttached()
        assertEquals(2, gate.takePending(gate.generation))
        assertNull(gate.takePending(gate.generation))
    }

    @Test fun tokenAloneCannotShowWithoutInputConnection() {
        val gate = ImeShowGate()
        gate.tokenAttached()
        gate.startInput(false)
        assertFalse(gate.request(1))
        assertNull(gate.takePending(gate.generation))
        gate.startInput(true)
        assertEquals(1, gate.takePending(gate.generation))
    }

    @Test fun focusLossInvalidatesOldAndLateRequests() {
        val gate = ImeShowGate()
        gate.startInput(true)
        gate.request(1)
        val old = gate.generation
        gate.finishInput()
        assertFalse(gate.request(2))
        gate.tokenAttached()
        gate.startInput(true)
        assertNull(gate.takePending(old))
        assertNull(gate.takePending(gate.generation))
        assertTrue(gate.request(3))
    }

    @Test fun destroyAndSupersedeAreTerminal() {
        val gate = ImeShowGate()
        gate.request(1)
        val old = gate.generation
        gate.destroy()
        gate.tokenAttached()
        gate.startInput(true)
        assertFalse(gate.request(2))
        assertFalse(gate.ready)
        assertNull(gate.takePending(old))
    }

    @Test fun hideCancelsQueuedReplayEvenAfterReadiness() {
        val gate = ImeShowGate()
        gate.request(1)
        gate.tokenAttached()
        gate.startInput(true)
        val queued = gate.generation
        gate.cancel()
        assertNull(gate.takePending(queued))
        assertNull(gate.takePending(gate.generation))
    }

    @Test fun twentyShowHideCyclesHaveNoStaleReplay() {
        val gate = ImeShowGate()
        gate.tokenAttached()
        repeat(20) {
            gate.startInput(true)
            assertTrue(gate.request(it))
            val queued = gate.generation
            gate.cancel()
            gate.finishInput()
            assertFalse(gate.request(99))
            assertNull(gate.takePending(queued))
        }
    }
    @Test fun newBindingCannotBorrowPreviousInputReadiness() {
        val gate = ImeShowGate()
        gate.tokenAttached()
        gate.startInput(true)
        gate.bindInput()
        assertFalse(gate.request(1))
        gate.startInput(true)
        assertNull(gate.takePending(gate.generation))
        assertTrue(gate.request(2))
    }

    @Test fun cancelledPreTokenRequestCannotBeRevivedByLateShow() {
        val gate = ImeShowGate()
        gate.startInput(true)
        gate.request(1)
        val queued = gate.generation
        gate.cancel()
        assertFalse(gate.request(2))
        gate.tokenAttached()
        assertNull(gate.takePending(queued))
        assertNull(gate.takePending(gate.generation))
    }
    @Test fun replayPreservesImplicitExplicitAndForcedPolicy() {
        assertEquals(1, ImeShowGate.toManagerFlags(0))
        assertEquals(0, ImeShowGate.toManagerFlags(1))
        assertEquals(2, ImeShowGate.toManagerFlags(2))
        assertEquals(2, ImeShowGate.toManagerFlags(3))
    }
}
