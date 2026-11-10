package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAvailabilityPolicyTest {

    @Test
    fun `single display rejects desktop`() {
        assertFalse(
            DesktopAvailabilityPolicy.allowDesktop(
                physicalOverlayActive = false,
                onlineDisplayCount = 1,
                imeDisplayId = 0,
                editorDisplayId = 0
            )
        )
    }

    @Test
    fun `same screen dual display rejects desktop`() {
        assertFalse(
            DesktopAvailabilityPolicy.allowDesktop(
                physicalOverlayActive = false,
                onlineDisplayCount = 2,
                imeDisplayId = 0,
                editorDisplayId = 0
            )
        )
    }

    @Test
    fun `cross screen dual display allows desktop`() {
        assertTrue(
            DesktopAvailabilityPolicy.allowDesktop(
                physicalOverlayActive = false,
                onlineDisplayCount = 2,
                imeDisplayId = 0,
                editorDisplayId = 2
            )
        )
    }

    @Test
    fun `unknown editor display rejects desktop`() {
        assertFalse(
            DesktopAvailabilityPolicy.allowDesktop(
                physicalOverlayActive = false,
                onlineDisplayCount = 2,
                imeDisplayId = 0,
                editorDisplayId = null
            )
        )
    }

    @Test
    fun `physical overlay allows desktop even on single display`() {
        assertTrue(
            DesktopAvailabilityPolicy.allowDesktop(
                physicalOverlayActive = true,
                onlineDisplayCount = 1,
                imeDisplayId = 0,
                editorDisplayId = null
            )
        )
    }
}
