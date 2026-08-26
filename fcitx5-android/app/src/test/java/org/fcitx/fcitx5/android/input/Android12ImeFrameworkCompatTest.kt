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

    private fun missingSettingsObserverFailure(
        message: String = "Attempt to invoke direct method 'boolean android.inputmethodservice.InputMethodService\$SettingsObserver.shouldShowImeWithHardKeyboard()' on a null object reference",
        className: String = "android.inputmethodservice.InputMethodService",
        methodName: String = "onShowInputRequested"
    ) = NullPointerException(message).apply {
        stackTrace = arrayOf(
            StackTraceElement(className, methodName, "InputMethodService.java", 2193)
        )
    }

    private fun missingWindowTokenFailure(
        message: String = "Window token is not set yet.",
        windowClassName: String = "android.inputmethodservice.SoftInputWindow",
        windowMethodName: String = "show",
        serviceClassName: String = "android.inputmethodservice.InputMethodService",
        serviceMethodName: String = "showWindow"
    ) = IllegalStateException(message).apply {
        stackTrace = arrayOf(
            StackTraceElement(windowClassName, windowMethodName, "SoftInputWindow.java", 273),
            StackTraceElement(serviceClassName, serviceMethodName, "InputMethodService.java", 2264)
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

    @Test
    fun rejectsExactAndroid12ShowAfterDestroyRace() {
        assertTrue(
            Android12ImeFrameworkCompat.canRejectShowAfterDestroy(
                31,
                missingSettingsObserverFailure()
            )
        )
    }

    @Test
    fun doesNotRejectShowRaceOnOtherAndroidVersions() {
        assertFalse(
            Android12ImeFrameworkCompat.canRejectShowAfterDestroy(
                32,
                missingSettingsObserverFailure()
            )
        )
    }

    @Test
    fun doesNotRejectUnrelatedNullPointerExceptions() {
        assertFalse(
            Android12ImeFrameworkCompat.canRejectShowAfterDestroy(
                31,
                missingSettingsObserverFailure(message = "different failure")
            )
        )
        assertFalse(
            Android12ImeFrameworkCompat.canRejectShowAfterDestroy(
                31,
                missingSettingsObserverFailure(className = "org.fcitx.SomeClass")
            )
        )
    }

    @Test
    fun rejectsExactAndroid12ShowBeforeAttachTokenRace() {
        assertTrue(
            Android12ImeFrameworkCompat.canRejectShowBeforeAttachToken(
                31,
                missingWindowTokenFailure()
            )
        )
    }

    @Test
    fun doesNotRejectTokenRaceOnOtherAndroidVersions() {
        assertFalse(
            Android12ImeFrameworkCompat.canRejectShowBeforeAttachToken(
                32,
                missingWindowTokenFailure()
            )
        )
    }

    @Test
    fun doesNotRejectUnrelatedIllegalStateExceptionsDuringShow() {
        assertFalse(
            Android12ImeFrameworkCompat.canRejectShowBeforeAttachToken(
                31,
                missingWindowTokenFailure(message = "different failure")
            )
        )
        assertFalse(
            Android12ImeFrameworkCompat.canRejectShowBeforeAttachToken(
                31,
                missingWindowTokenFailure(windowClassName = "org.fcitx.SomeWindow")
            )
        )
        assertFalse(
            Android12ImeFrameworkCompat.canRejectShowBeforeAttachToken(
                31,
                missingWindowTokenFailure(serviceMethodName = "onCreate")
            )
        )
    }
}
