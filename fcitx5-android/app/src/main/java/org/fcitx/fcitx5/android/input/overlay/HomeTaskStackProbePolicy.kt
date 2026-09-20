package org.fcitx.fcitx5.android.input.overlay

internal object HomeTaskStackProbePolicy {
    private const val HOME = "android.intent.category.HOME"
    private const val SECONDARY_HOME = "android.intent.category.SECONDARY_HOME"

    fun isTargetHome(taskDisplayId: Int, overlayDisplayId: Int, categories: Set<String>): Boolean =
        taskDisplayId == overlayDisplayId && (HOME in categories || SECONDARY_HOME in categories)

    fun ownsCandidate(
        eventRequestId: Long,
        eventSessionId: String,
        eventDisplayId: Int,
        currentRequestId: Long?,
        currentSessionId: String?,
        currentDisplayId: Int?,
    ): Boolean = eventRequestId == currentRequestId &&
        eventSessionId == currentSessionId && eventDisplayId == currentDisplayId
}
