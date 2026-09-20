package org.fcitx.fcitx5.android.input.overlay

import kotlin.math.min
import kotlin.math.roundToInt

internal object OverlayDesktopLayoutPolicy {
    data class Result(
        val keyboardHeightPx: Int,
        val operationHeightPx: Int,
        val touchpadHeightPx: Int,
        val rowHeightPx: Int,
        val verticalInsetPx: Int
    ) {
        val usedHeightPx: Int
            get() = operationHeightPx + touchpadHeightPx + rowHeightPx * ROW_COUNT + verticalInsetPx
    }

    fun calculate(
        canvasWidthPx: Int,
        canvasHeightPx: Int,
        physicalTargetHeightPx: Int,
        density: Float
    ): Result {
        require(canvasWidthPx > 0 && canvasHeightPx > 0)
        require(physicalTargetHeightPx >= canvasHeightPx)
        val operation = (56f * density).roundToInt()
        val verticalInset = (8f * density).roundToInt()
        val minimumTouchpad = (48f * density).roundToInt()
        val minimumRow = (32f * density).roundToInt()
        val preferredRow = canvasWidthPx / 15
        val rowBudget = (canvasHeightPx - operation - verticalInset - minimumTouchpad)
            .coerceAtLeast(minimumRow * ROW_COUNT)
        val row = min(preferredRow, rowBudget / ROW_COUNT).coerceAtLeast(minimumRow)
        val touchpad = (canvasHeightPx - operation - verticalInset - row * ROW_COUNT)
            .coerceAtLeast(minimumTouchpad)
        return Result(canvasHeightPx, operation, touchpad, row, verticalInset)
    }

    private const val ROW_COUNT = 6
}
