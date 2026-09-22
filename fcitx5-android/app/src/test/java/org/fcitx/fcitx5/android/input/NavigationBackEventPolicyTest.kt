package org.fcitx.fcitx5.android.input

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationBackEventPolicyTest {
    @Test
    fun virtualNavigationButtonBackIsReturnedToFocusedApp() {
        assertFalse(shouldConsumeImeNavigationBack(KeyEvent.FLAG_VIRTUAL_HARD_KEY, InputDevice.SOURCE_KEYBOARD))
    }

    @Test
    fun ordinarySystemBackIsReturnedToFocusedApp() {
        assertFalse(shouldConsumeImeNavigationBack(KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD))
    }

    @Test
    fun mouseBackIsReturnedToFocusedAppEvenIfVirtualFlagIsPresent() {
        assertFalse(
            shouldConsumeImeNavigationBack(KeyEvent.FLAG_VIRTUAL_HARD_KEY, InputDevice.SOURCE_MOUSE)
        )
    }
}
