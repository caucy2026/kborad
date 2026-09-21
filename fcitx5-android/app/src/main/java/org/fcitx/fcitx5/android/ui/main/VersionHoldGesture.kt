package org.fcitx.fcitx5.android.ui.main

/** Uses monotonic uptime; cancellation permanently ends the current gesture. */
internal class VersionHoldGesture {
    private var started: Long? = null
    private var fired = false
    fun down(now: Long) { started = now; fired = false }
    fun fire(now: Long): Boolean {
        val start = started ?: return false
        if (fired || now - start < 8_000L) return false
        fired = true
        return true
    }
    fun up(): Boolean {
        val click = started != null && !fired
        cancel()
        return click
    }
    fun cancel() { started = null; fired = false }
}
