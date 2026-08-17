/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

internal object Android12ImeFrameworkCompat {

    private const val Android12ApiLevel = 31
    private const val BindBeforeInitializeMessage =
        "onBindInput can be called only after onInitialize()."
    private const val ConfigurationTrackerClass =
        "android.inputmethodservice.ImsConfigurationTracker"

    /**
     * Android 12's ImsConfigurationTracker crashes when a vendor InputMethodManager sends
     * bindInput before initializeInternal. Newer AOSP releases ignore the same stale callback.
     * Match the platform exception narrowly so application failures are never hidden.
     */
    fun canIgnoreBindBeforeInitialize(sdkInt: Int, error: IllegalStateException): Boolean =
        sdkInt == Android12ApiLevel &&
            error.message == BindBeforeInitializeMessage &&
            error.stackTrace.any {
                it.className == ConfigurationTrackerClass && it.methodName == "onBindInput"
            }
}
