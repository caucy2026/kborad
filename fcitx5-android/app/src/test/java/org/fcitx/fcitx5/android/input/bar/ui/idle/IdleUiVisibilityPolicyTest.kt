package org.fcitx.fcitx5.android.input.bar.ui.idle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleUiVisibilityPolicyTest {
    @Test
    fun globalKeyboardStillShowsFreshClipboardSuggestion() {
        assertTrue(IdleUiVisibilityPolicy.showAnimator(desktopQuietMode = true, clipboard = true))
    }

    @Test
    fun globalKeyboardKeepsOtherIdleControlsQuiet() {
        assertFalse(IdleUiVisibilityPolicy.showAnimator(desktopQuietMode = true, clipboard = false))
        assertTrue(IdleUiVisibilityPolicy.showAnimator(desktopQuietMode = false, clipboard = false))
    }
}
