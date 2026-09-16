package org.fcitx.fcitx5.android.ui.main

internal class EngineeringAccessGate(
    private val requiredTaps: Int = 7,
    private val password: String = "2580",
) {
    private var tapCount = 0

    var isUnlocked: Boolean = false
        private set

    fun onVersionTapped(): Boolean {
        if (isUnlocked) return true
        tapCount += 1
        if (tapCount < requiredTaps) return false
        tapCount = 0
        return true
    }

    fun unlock(candidate: String): Boolean {
        isUnlocked = candidate == password
        return isUnlocked
    }
}

internal object EngineeringAccessSession {
    val gate = EngineeringAccessGate()
}
