package org.fcitx.fcitx5.android.input

import org.fcitx.fcitx5.android.core.FcitxEvent.SwitchInputMethodEvent.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputLanguagePolicyTest {
    private val enabled = setOf("keyboard-us", "pinyin")

    @Test
    fun `missing choice resolves to pinyin`() {
        assertEquals("pinyin", InputLanguagePolicy.resolve(null, enabled))
        assertEquals("pinyin", InputLanguagePolicy.resolve("", enabled))
    }

    @Test
    fun `saved english is kept`() {
        assertEquals("keyboard-us", InputLanguagePolicy.resolve("keyboard-us", enabled))
    }

    @Test
    fun `unknown saved name falls back to pinyin`() {
        assertEquals("pinyin", InputLanguagePolicy.resolve("rime", enabled))
    }

    @Test
    fun `empty enabled list waits`() {
        assertNull(InputLanguagePolicy.resolve(null, emptySet()))
    }

    @Test
    fun `same language is not applied again`() {
        assertFalse(InputLanguagePolicy.shouldApply("pinyin", "pinyin"))
        assertTrue(InputLanguagePolicy.shouldApply("keyboard-us", "pinyin"))
    }

    @Test
    fun `password and empty editors do not restore`() {
        assertFalse(InputLanguagePolicy.allowsRestore(hasTextField = false, blocksInputMethod = false))
        assertFalse(InputLanguagePolicy.allowsRestore(hasTextField = true, blocksInputMethod = true))
        assertTrue(InputLanguagePolicy.allowsRestore(hasTextField = true, blocksInputMethod = false))
    }

    @Test
    fun `capability change is not stored`() {
        assertFalse(InputLanguagePolicy.shouldPersist(Reason.CapabilityChanged))
        assertTrue(InputLanguagePolicy.shouldPersist(Reason.Trigger))
        assertTrue(InputLanguagePolicy.shouldPersist(Reason.AltTrigger))
        assertTrue(InputLanguagePolicy.shouldPersist(Reason.Enumerate))
        assertTrue(InputLanguagePolicy.shouldPersist(Reason.Other))
    }

    @Test
    fun `restore and unchanged selection do not rewrite storage`() {
        assertFalse(InputLanguagePolicy.shouldSave("pinyin", "pinyin", null))
        assertFalse(InputLanguagePolicy.shouldSave(null, "keyboard-us", "keyboard-us"))
        assertFalse(InputLanguagePolicy.shouldSave(null, "", null))
        assertTrue(InputLanguagePolicy.shouldSave(null, "keyboard-us", "pinyin"))
        assertTrue(InputLanguagePolicy.shouldSave("pinyin", "keyboard-us", "pinyin"))
    }
}
