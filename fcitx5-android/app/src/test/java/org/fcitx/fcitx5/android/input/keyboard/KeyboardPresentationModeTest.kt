package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardPresentationModeTest {
    @Test
    fun `invalid persisted value falls back to normal keyboard`() {
        assertEquals(KeyboardPresentationMode.Normal, KeyboardPresentationMode.decode("legacy"))
    }

    @Test
    fun `each explicit mode survives persistence round trip`() {
        listOf(
            KeyboardPresentationMode.Normal,
            KeyboardPresentationMode.Floating,
            KeyboardPresentationMode.Desktop
        ).forEach { mode ->
            assertEquals(mode, KeyboardPresentationMode.decode(mode.persistedValue))
        }
    }

    @Test
    fun `mode selects the matching first keyboard layout`() {
        assertEquals(TextKeyboard.Name, KeyboardPresentationMode.Normal.layoutName)
        assertEquals(TextKeyboard.FloatingName, KeyboardPresentationMode.Floating.layoutName)
        assertEquals(DesktopKeyboard.Name, KeyboardPresentationMode.Desktop.layoutName)
    }
}
