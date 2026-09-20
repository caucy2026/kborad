package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedKeyboardSwitchPolicyTest {
    @Test
    fun `only the exact KEMI expanded editor marker uses host rehome protocol`() {
        assertTrue(
            ExpandedKeyboardSwitchPolicy.shouldRequestHostRehome(
                "com.newlinksz.kemi.remote.EXPANDED_KEYBOARD"
            )
        )
        assertFalse(ExpandedKeyboardSwitchPolicy.shouldRequestHostRehome(null))
        assertFalse(ExpandedKeyboardSwitchPolicy.shouldRequestHostRehome("EXPANDED_KEYBOARD"))
    }
}
