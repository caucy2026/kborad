package org.fcitx.fcitx5.android.input.overlay

internal object OverlayRequestPolicy {
    fun safeCanvasHeight(targetHeight: Int, requestedHeight: Int): Int =
        requestedHeight.coerceAtMost(targetHeight * SAFE_HEIGHT_PERCENT / 100)

    fun isValid(
        requestId: Long,
        sessionId: String,
        sourceDisplayId: Int,
        targetDisplayId: Int,
        targetWidth: Int,
        targetHeight: Int,
        keyboardHeight: Int,
        onlineDisplayIds: Set<Int>
    ): Boolean = requestId > 0 &&
        sessionId.isNotBlank() &&
        sourceDisplayId >= 0 &&
        targetDisplayId >= 0 &&
        sourceDisplayId != targetDisplayId &&
        sourceDisplayId in onlineDisplayIds &&
        targetDisplayId in onlineDisplayIds &&
        targetWidth > 0 &&
        targetHeight > 0 &&
        keyboardHeight in 1..targetHeight

    private const val SAFE_HEIGHT_PERCENT = 52
}
