/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

/**
 * Drops implausibly fast, sequential key transitions produced by the V900 hardware driver.
 *
 * Normal fast typing often overlaps: the next key goes down before the previous key goes up.
 * That produces a negative release-to-press interval and is deliberately preserved. Repeats,
 * modifiers, virtual keys and non-printing control keys are outside this filter as well.
 */
internal class HardwareKeyAnomalyFilter(
    private val minimumReleaseToPressMillis: Long = DEFAULT_MINIMUM_RELEASE_TO_PRESS_MILLIS
) {
    data class Key(
        val deviceId: Int,
        val keyCode: Int
    )

    private data class Release(
        val key: Key,
        val eventTime: Long,
        val printing: Boolean,
        val modifier: Boolean
    )

    private val acceptedDown = mutableSetOf<Key>()
    private val suppressedDown = mutableSetOf<Key>()
    private val lastReleaseByDevice = mutableMapOf<Int, Release>()

    fun shouldDropDown(
        key: Key,
        eventTime: Long,
        printing: Boolean,
        modifier: Boolean,
        repeatCount: Int
    ): Boolean {
        if (key in suppressedDown) return true
        if (repeatCount > 0 || key in acceptedDown) return false

        val previous = lastReleaseByDevice[key.deviceId]
        val gap = previous?.let { eventTime - it.eventTime }
        val drop = previous != null &&
            previous.key.keyCode != key.keyCode &&
            previous.printing && printing &&
            !previous.modifier && !modifier &&
            gap != null && gap >= 0L && gap < minimumReleaseToPressMillis

        if (drop) {
            suppressedDown += key
        } else {
            acceptedDown += key
        }
        return drop
    }

    fun shouldDropUp(
        key: Key,
        eventTime: Long,
        printing: Boolean,
        modifier: Boolean
    ): Boolean {
        if (suppressedDown.remove(key)) return true
        if (acceptedDown.remove(key)) {
            lastReleaseByDevice[key.deviceId] = Release(key, eventTime, printing, modifier)
        }
        return false
    }

    fun reset() {
        acceptedDown.clear()
        suppressedDown.clear()
        lastReleaseByDevice.clear()
    }

    companion object {
        const val DEFAULT_MINIMUM_RELEASE_TO_PRESS_MILLIS = 12L
    }
}
