/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import timber.log.Timber

/**
 * One-shot IME token relay for Android 12 multi-display devices.
 *
 * The vendor display policy only affects newly created IME tokens. The primary KBoard service
 * switches here after the policy broadcast completes; this service receives the new token and
 * immediately selects the primary service again. No user text is handled by this relay.
 */
class DisplaySwitchInputMethodService : InputMethodService() {

    // This service only relays the IME token; it never owns a keyboard UI. The framework can
    // send show requests before attachToken here as well as to the primary service.
    override fun onShowInputRequested(flags: Int, configChange: Boolean) = false

    override fun showWindow(showInput: Boolean) = Unit

    private val mainHandler = Handler(Looper.getMainLooper())
    private var returnPosted = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (returnPosted) return
        returnPosted = true
        mainHandler.post {
            runCatching {
                switchInputMethod(InputMethodUtil.componentName)
            }.onFailure {
                Timber.e(it, "Failed to return from display-switch IME relay")
            }
        }
    }

    override fun onFinishInput() {
        mainHandler.removeCallbacksAndMessages(null)
        returnPosted = false
        super.onFinishInput()
    }

    override fun onDestroy() {
        // Vendor multi-display teardown does not always pair onStartInput/onFinishInput.
        mainHandler.removeCallbacksAndMessages(null)
        returnPosted = false
        super.onDestroy()
    }
}
