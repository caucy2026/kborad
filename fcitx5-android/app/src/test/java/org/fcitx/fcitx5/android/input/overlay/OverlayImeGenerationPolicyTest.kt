package org.fcitx.fcitx5.android.input.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayImeGenerationPolicyTest {
    @Test
    fun migratesOnlyWhenActiveOwnerUsesDifferentImeGeneration() {
        assertTrue(OverlayImeGenerationPolicy.shouldMigrate(10, 11))
        assertFalse(OverlayImeGenerationPolicy.shouldMigrate(10, 10))
        assertFalse(OverlayImeGenerationPolicy.shouldMigrate(null, 11))
    }
}
