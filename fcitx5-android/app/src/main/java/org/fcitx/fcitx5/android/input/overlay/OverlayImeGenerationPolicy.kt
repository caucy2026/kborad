package org.fcitx.fcitx5.android.input.overlay

internal object OverlayImeGenerationPolicy {
    fun shouldMigrate(ownerGeneration: Int?, currentGeneration: Int): Boolean =
        ownerGeneration != null && ownerGeneration != currentGeneration
}
