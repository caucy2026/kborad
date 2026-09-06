/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Android12ImeFrameworkCompatTest {

    private fun frameworkFailure(
        message: String = "onBindInput can be called only after onInitialize().",
        className: String = "android.inputmethodservice.ImsConfigurationTracker",
        methodName: String = "onBindInput"
    ) = IllegalStateException(message).apply {
        stackTrace = arrayOf(
            StackTraceElement(className, methodName, "ImsConfigurationTracker.java", 66)
        )
    }

    @Test
    fun ignoresExactAndroid12FrameworkRace() {
        assertTrue(
            Android12ImeFrameworkCompat.canIgnoreBindBeforeInitialize(31, frameworkFailure())
        )
    }

    @Test
    fun doesNotIgnoreTheRaceOnOtherAndroidVersions() {
        assertFalse(
            Android12ImeFrameworkCompat.canIgnoreBindBeforeInitialize(32, frameworkFailure())
        )
    }

    @Test
    fun doesNotIgnoreOtherIllegalStateExceptions() {
        assertFalse(
            Android12ImeFrameworkCompat.canIgnoreBindBeforeInitialize(
                31,
                frameworkFailure(message = "different failure")
            )
        )
        assertFalse(
            Android12ImeFrameworkCompat.canIgnoreBindBeforeInitialize(
                31,
                frameworkFailure(className = "org.fcitx.SomeClass")
            )
        )
    }

}
