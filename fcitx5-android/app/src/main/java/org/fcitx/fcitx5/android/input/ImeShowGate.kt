/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input

/** Main-thread state. A deferred intent belongs only to the current input lifetime. */
internal class ImeShowGate {
    private var alive = true
    private var acceptsPending = true
    private var inputReady = false
    private var tokenReady = false
    private var pendingFlags: Int? = null
    var generation = 0L
        private set

    val ready get() = alive && inputReady && tokenReady

    fun request(flags: Int): Boolean {
        if (ready) {
            cancel()
            return true
        }
        if (alive && acceptsPending) pendingFlags = flags
        return false
    }

    fun bindInput() {
        // A new binding must not borrow the previous editor's ready state.
        if (inputReady) finishInput()
    }

    fun tokenAttached() { if (alive) tokenReady = true }

    fun startInput(connectionReady: Boolean) {
        if (!alive) return
        inputReady = connectionReady
        acceptsPending = true
    }

    fun takePending(expectedGeneration: Long): Int? {
        if (expectedGeneration != generation || !ready) return null
        return pendingFlags.also { pendingFlags = null }
    }

    fun cancel() { pendingFlags = null; acceptsPending = false; generation++ }

    fun finishInput() {
        cancel()
        inputReady = false
        acceptsPending = false
    }

    fun destroy() {
        finishInput()
        alive = false
        tokenReady = false
    }
    companion object {
        /** InputMethod.SHOW_EXPLICIT (1) is the inverse of IMM.SHOW_IMPLICIT (1). */
        fun toManagerFlags(imeFlags: Int): Int = when {
            imeFlags and 2 != 0 -> 2 // SHOW_FORCED in both APIs
            imeFlags and 1 != 0 -> 0 // explicit request
            else -> 1 // implicit request
        }
    }
}
