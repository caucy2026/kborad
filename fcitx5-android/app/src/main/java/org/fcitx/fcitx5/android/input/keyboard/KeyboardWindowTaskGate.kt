package org.fcitx.fcitx5.android.input.keyboard

import java.util.concurrent.atomic.AtomicLong

/** Prevents queued keyboard work from outliving its owning InputView generation. */
internal class KeyboardWindowTaskGate {
    private val generation = AtomicLong(0L)

    @Volatile
    private var retired = false

    fun captureGeneration(): Long = generation.get()

    fun canRun(capturedGeneration: Long): Boolean =
        !retired && generation.get() == capturedGeneration

    fun retire() {
        retired = true
        generation.incrementAndGet()
    }
}
