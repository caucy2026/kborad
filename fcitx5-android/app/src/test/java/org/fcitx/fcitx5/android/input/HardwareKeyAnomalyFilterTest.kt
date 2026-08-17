/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyAnomalyFilterTest {
    private val filter = HardwareKeyAnomalyFilter()
    private val a = HardwareKeyAnomalyFilter.Key(4, 29)
    private val b = HardwareKeyAnomalyFilter.Key(4, 30)

    @Test
    fun dropsDifferentPrintingKeyInsideTwelveMillisecondsAndItsRelease() {
        assertFalse(filter.shouldDropDown(a, 100, true, false, 0))
        assertFalse(filter.shouldDropUp(a, 180, true, false))
        assertTrue(filter.shouldDropDown(b, 191, true, false, 0))
        assertTrue(filter.shouldDropDown(b, 195, true, false, 1))
        assertTrue(filter.shouldDropUp(b, 220, true, false))
    }

    @Test
    fun acceptsBoundaryAndOverlappingFastTyping() {
        assertFalse(filter.shouldDropDown(a, 100, true, false, 0))
        assertFalse(filter.shouldDropUp(a, 180, true, false))
        assertFalse(filter.shouldDropDown(b, 192, true, false, 0))

        filter.reset()
        assertFalse(filter.shouldDropDown(a, 300, true, false, 0))
        assertFalse(filter.shouldDropDown(b, 340, true, false, 0))
        assertFalse(filter.shouldDropUp(a, 350, true, false))
    }

    @Test
    fun preservesRepeatModifierAndNonPrintingKeys() {
        assertFalse(filter.shouldDropDown(a, 100, true, false, 0))
        assertFalse(filter.shouldDropUp(a, 180, true, false))
        assertFalse(filter.shouldDropDown(a, 181, true, false, 1))

        filter.reset()
        assertFalse(filter.shouldDropDown(a, 200, true, false, 0))
        assertFalse(filter.shouldDropUp(a, 280, true, false))
        assertFalse(filter.shouldDropDown(b, 281, true, true, 0))

        filter.reset()
        assertFalse(filter.shouldDropDown(a, 300, false, false, 0))
        assertFalse(filter.shouldDropUp(a, 380, false, false))
        assertFalse(filter.shouldDropDown(b, 381, true, false, 0))
    }

    @Test
    fun tracksDevicesIndependently() {
        assertFalse(filter.shouldDropDown(a, 100, true, false, 0))
        assertFalse(filter.shouldDropUp(a, 180, true, false))
        assertFalse(
            filter.shouldDropDown(
                HardwareKeyAnomalyFilter.Key(5, b.keyCode),
                181,
                true,
                false,
                0
            )
        )
    }
}
