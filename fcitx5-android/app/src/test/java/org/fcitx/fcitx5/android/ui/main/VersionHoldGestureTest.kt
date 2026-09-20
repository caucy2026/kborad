package org.fcitx.fcitx5.android.ui.main

import org.junit.Assert.*
import org.junit.Test

class VersionHoldGestureTest {
    @Test fun firesOnlyAfterEightSecondsAndOnlyOnce() {
        val gesture = VersionHoldGesture()
        gesture.down(100)
        assertFalse(gesture.fire(8099))
        assertTrue(gesture.fire(8100))
        assertFalse(gesture.fire(9000))
        assertFalse(gesture.up())
    }
    @Test fun shortTapIsClick() {
        val gesture = VersionHoldGesture()
        gesture.down(0)
        assertTrue(gesture.up())
        assertFalse(gesture.fire(8000))
    }
    @Test fun cancellationPreventsBothClickAndLongPress() {
        val gesture = VersionHoldGesture()
        gesture.down(0)
        gesture.cancel()
        assertFalse(gesture.fire(8000))
        assertFalse(gesture.up())
        gesture.down(9000)
        assertFalse(gesture.fire(16999))
        assertTrue(gesture.fire(17000))
    }
}
