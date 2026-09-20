package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTaskStackProbePolicyTest {
    @Test
    fun acceptsHomeOnCurrentOverlayDisplay() {
        assertTrue(HomeTaskStackProbePolicy.isTargetHome(2, 2, setOf("android.intent.category.HOME")))
        assertTrue(HomeTaskStackProbePolicy.isTargetHome(2, 2, setOf("android.intent.category.SECONDARY_HOME")))
    }

    @Test
    fun acceptsOnlyTheSameRequestSessionAndDisplayAtExecutionTime() {
        assertTrue(HomeTaskStackProbePolicy.ownsCandidate(51L, "session-a", 2, 51L, "session-a", 2))
        assertFalse(HomeTaskStackProbePolicy.ownsCandidate(51L, "session-a", 2, 52L, "session-a", 2))
        assertFalse(HomeTaskStackProbePolicy.ownsCandidate(51L, "session-a", 2, 51L, "session-b", 2))
        assertFalse(HomeTaskStackProbePolicy.ownsCandidate(51L, "session-a", 2, 51L, "session-a", 0))
        assertFalse(HomeTaskStackProbePolicy.ownsCandidate(51L, "session-a", 2, null, null, null))
    }

    @Test
    fun rejectsOtherDisplayAndOrdinaryTasks() {
        assertFalse(HomeTaskStackProbePolicy.isTargetHome(0, 2, setOf("android.intent.category.HOME")))
        assertFalse(HomeTaskStackProbePolicy.isTargetHome(2, 2, setOf("android.intent.category.DEFAULT")))
        assertFalse(HomeTaskStackProbePolicy.isTargetHome(2, 2, emptySet()))
    }
}
