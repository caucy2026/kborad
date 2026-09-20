package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOutputRoutePolicyTest {
    @Test
    fun `ordinary keyboard in physical overlay commits through overlay route`() {
        assertTrue(VoiceOutputRoutePolicy.useDirectCommit(false, true))
        assertFalse(VoiceOutputRoutePolicy.useEditorComposition(false, true))
    }

    @Test
    fun `floating keyboard in physical overlay commits through overlay route`() {
        assertTrue(VoiceOutputRoutePolicy.useDirectCommit(false, true))
    }

    @Test
    fun `ordinary keyboard in a local editor keeps composing preview`() {
        assertFalse(VoiceOutputRoutePolicy.useDirectCommit(false, false))
        assertTrue(VoiceOutputRoutePolicy.useEditorComposition(false, false))
    }

    @Test
    fun `global keyboard always commits directly`() {
        assertTrue(VoiceOutputRoutePolicy.useDirectCommit(true, false))
        assertFalse(VoiceOutputRoutePolicy.useEditorComposition(true, false))
    }
}
