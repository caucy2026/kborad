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
    private const val InputMethodServiceClass =
        "android.inputmethodservice.InputMethodService"
    private const val SoftInputWindowClass =
        "android.inputmethodservice.SoftInputWindow"
    private const val MissingWindowTokenMessage =
        "Window token is not set yet."
    private const val MissingSettingsObserverMessage =
        "android.inputmethodservice.InputMethodService\$SettingsObserver.shouldShowImeWithHardKeyboard()"

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

    /**
     * Android 12 can dispatch a queued show request after InputMethodService.onDestroy() has
     * already cleared its private SettingsObserver. Reject only that stale framework callback;
     * a subsequent request will be delivered to the newly initialized service instance.
     */
    fun canRejectShowAfterDestroy(sdkInt: Int, error: NullPointerException): Boolean =
        sdkInt == Android12ApiLevel &&
            error.message?.contains(MissingSettingsObserverMessage) == true &&
            error.stackTrace.any {
                it.className == InputMethodServiceClass && it.methodName == "onShowInputRequested"
            }

    /**
     * Android 12 can deliver showSoftInput to a newly created InputMethodService before
     * attachToken. SoftInputWindow cannot be shown in that state. Drop only this exact platform
     * failure; the next request after attachToken can show the same service normally.
     */
    fun canRejectShowBeforeAttachToken(sdkInt: Int, error: IllegalStateException): Boolean =
        sdkInt == Android12ApiLevel &&
            error.message == MissingWindowTokenMessage &&
            error.stackTrace.any {
                it.className == SoftInputWindowClass && it.methodName == "show"
            } &&
            error.stackTrace.any {
                it.className == InputMethodServiceClass && it.methodName == "showWindow"
            }
}
