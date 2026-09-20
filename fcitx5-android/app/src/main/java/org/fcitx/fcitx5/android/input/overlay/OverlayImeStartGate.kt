package org.fcitx.fcitx5.android.input.overlay

internal class OverlayImeStartGate(private val maxAttempts: Int = 6) {
    enum class Action { WAIT, RESTART_INPUT, RESTART_AND_SHOW, EXHAUSTED }

    var attached = false
    var windowFocused = false
    var inputConnectionReady = false
    var completed = false
        private set
    private var attempts = 0

    fun nextAction(): Action {
        if (completed || !attached || !windowFocused) return Action.WAIT
        if (attempts >= maxAttempts) return Action.EXHAUSTED
        attempts += 1
        return if (inputConnectionReady) Action.RESTART_AND_SHOW else Action.RESTART_INPUT
    }

    fun markShowAccepted() {
        completed = true
    }
}
