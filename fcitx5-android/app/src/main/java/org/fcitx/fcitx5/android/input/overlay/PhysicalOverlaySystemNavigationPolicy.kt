package org.fcitx.fcitx5.android.input.overlay

internal object PhysicalOverlaySystemNavigationPolicy {
    fun shouldClose(reason: String?): Boolean = reason == "homekey" || reason == "recentapps"

    fun ownsEvent(
        eventRequestId: Long,
        eventSessionId: String,
        ownerRequestId: Long?,
        ownerSessionId: String?
    ): Boolean = eventRequestId == ownerRequestId && eventSessionId == ownerSessionId
}
