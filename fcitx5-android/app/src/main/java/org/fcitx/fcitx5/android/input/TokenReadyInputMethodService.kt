/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputBinding
import android.view.inputmethod.InputMethodManager
import org.fcitx.fcitx5.android.utils.inputMethodManager
import timber.log.Timber

/** Gate before framework showWindow mutates visibility or calls SoftInputWindow.show(). */
open class TokenReadyInputMethodService : InputMethodService() {
    private val showGate = ImeShowGate()
    private val showHandler = Handler(Looper.getMainLooper())
    private var retired = false

    private fun canShow(): Boolean {
        if (retired) return false
        // Scope the vendor-framework workaround to Android 12. Other releases retain the
        // platform InputMethodImpl and visibility behavior unchanged.
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.S) return true
        return showGate.ready && currentInputStarted && currentInputConnection != null &&
            window?.window?.attributes?.token != null
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCreateInputMethodInterface() =
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            object : InputMethodImpl() {
                override fun attachToken(token: IBinder) {
                    if (retired) return
                    super.attachToken(token)
                    showGate.tokenAttached()
                    resumePendingShow()
                }

                override fun bindInput(binding: InputBinding) {
                    if (retired) return
                    showGate.bindInput()
                    try {
                        super.bindInput(binding)
                    } catch (error: IllegalStateException) {
                        if (!Android12ImeFrameworkCompat.canIgnoreBindBeforeInitialize(
                                Build.VERSION.SDK_INT,
                                error
                            )
                        ) {
                            throw error
                        }
                        Timber.w(
                            error,
                            "Ignored Android 12 bindInput-before-initialize framework race"
                        )
                    }
                }

                override fun showSoftInput(flags: Int, resultReceiver: ResultReceiver?) {
                    if (canShow() && showGate.request(flags)) {
                        super.showSoftInput(flags, resultReceiver)
                    } else {
                        showGate.request(flags)
                        // Complete the original request once. A later request goes through IMMS
                        // again, obtaining its own show token instead of replaying stale context.
                        resultReceiver?.send(
                            if (isInputViewShown) {
                                InputMethodManager.RESULT_UNCHANGED_SHOWN
                            } else {
                                InputMethodManager.RESULT_UNCHANGED_HIDDEN
                            },
                            null
                        )
                        Timber.d(
                            "Deferred/rejected IME show: " +
                                "ready=${showGate.ready} retired=$retired"
                        )
                    }
                }

                override fun hideSoftInput(flags: Int, resultReceiver: ResultReceiver?) {
                    cancelPendingShow()
                    if (!retired) {
                        super.hideSoftInput(flags, resultReceiver)
                    } else {
                        resultReceiver?.send(InputMethodManager.RESULT_UNCHANGED_HIDDEN, null)
                    }
                }
            }
        } else {
            super.onCreateInputMethodInterface()
        }

    private fun resumePendingShow() {
        val generation = showGate.generation
        // Leave initialize/startInput before requesting IMMS visibility; this is an event-loop
        // handoff, not a timed retry. Teardown or hide invalidates it before it can run.
        showHandler.post {
            if (!canShow()) return@post
            val imeFlags = showGate.takePending(generation) ?: return@post
            val flags = ImeShowGate.toManagerFlags(imeFlags)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) requestShowSelf(flags)
            else {
                @Suppress("DEPRECATION")
                inputMethodManager.showSoftInputFromInputMethod(window.window!!.attributes.token, flags)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        showGate.startInput(currentInputConnection != null)
        resumePendingShow()
    }

    protected fun cancelPendingShow() {
        showHandler.removeCallbacksAndMessages(null)
        showGate.cancel()
    }

    protected fun retireImeWindow() {
        retired = true
        showHandler.removeCallbacksAndMessages(null)
        showGate.destroy()
    }

    override fun onFinishInput() {
        cancelPendingShow()
        showGate.finishInput()
        super.onFinishInput()
    }

    override fun onUnbindInput() {
        cancelPendingShow()
        showGate.finishInput()
        super.onUnbindInput()
    }

    override fun requestHideSelf(flags: Int) {
        cancelPendingShow()
        if (!retired) super.requestHideSelf(flags)
    }

    override fun hideWindow() {
        cancelPendingShow()
        if (!retired) super.hideWindow()
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean =
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            canShow() && super.onShowInputRequested(flags, configChange)
        } else {
            !retired && super.onShowInputRequested(flags, configChange)
        }

    override fun showWindow(showInput: Boolean) {
        if (!retired && (Build.VERSION.SDK_INT != Build.VERSION_CODES.S || canShow())) {
            super.showWindow(showInput)
        }
    }

    override fun onDestroy() {
        retireImeWindow()
        super.onDestroy()
    }
}
