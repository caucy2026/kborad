/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.audioManager
import org.fcitx.fcitx5.android.utils.getSystemSettings
import org.fcitx.fcitx5.android.utils.vibrator
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import timber.log.Timber

object InputFeedbacks {

    enum class InputFeedbackMode(override val stringRes: Int) : ManagedPreferenceEnum {
        FollowingSystem(R.string.following_system_settings),
        Enabled(R.string.enabled),
        Disabled(R.string.disabled);
    }

    private var systemSoundEffects = false
    private var systemHapticFeedback = false

    fun syncSystemPrefs() {
        systemSoundEffects = getSystemSettings<Int>(Settings.System.SOUND_EFFECTS_ENABLED) == 1
        // it says "Replaced by using android.os.VibrationAttributes.USAGE_TOUCH"
        // but gives no clue about how to use it, and this one still works
        @Suppress("DEPRECATION")
        systemHapticFeedback = getSystemSettings<Int>(Settings.System.HAPTIC_FEEDBACK_ENABLED) == 1
    }

    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    private val soundOnKeyPress by keyboardPrefs.soundOnKeyPress
    private val soundOnKeyPressVolume by keyboardPrefs.soundOnKeyPressVolume
    private val hapticOnKeyPress by keyboardPrefs.hapticOnKeyPress
    private val hapticOnKeyUp by keyboardPrefs.hapticOnKeyUp
    private val buttonPressVibrationMilliseconds by keyboardPrefs.buttonPressVibrationMilliseconds
    private val buttonLongPressVibrationMilliseconds by keyboardPrefs.buttonLongPressVibrationMilliseconds
    private val buttonPressVibrationAmplitude by keyboardPrefs.buttonPressVibrationAmplitude
    private val buttonLongPressVibrationAmplitude by keyboardPrefs.buttonLongPressVibrationAmplitude

    private val vibrator = appContext.vibrator

    private val hasAmplitudeControl =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && vibrator.hasAmplitudeControl()

    fun hapticFeedback(view: View, longPress: Boolean = false, keyUp: Boolean = false) {
        when (hapticOnKeyPress) {
            InputFeedbackMode.Enabled -> {}
            InputFeedbackMode.Disabled -> return
            InputFeedbackMode.FollowingSystem -> if (!systemHapticFeedback) return
        }
        if (keyUp && !hapticOnKeyUp) return
        val duration: Long
        val amplitude: Int
        val hfc: Int
        if (longPress) {
            duration = buttonLongPressVibrationMilliseconds.toLong()
            amplitude = buttonLongPressVibrationAmplitude
            hfc = HapticFeedbackConstants.LONG_PRESS
        } else {
            duration = buttonPressVibrationMilliseconds.toLong()
            amplitude = buttonPressVibrationAmplitude
            hfc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && keyUp) {
                HapticFeedbackConstants.KEYBOARD_RELEASE
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        }

        // there is `VibrationEffect.DEFAULT_AMPLITUDE` but no default duration;
        // also `VibrationEffect.createOneShot()` only accepts positive duration.
        // so changing amplitude without changing duration makes no sense
        if (duration != 0L) {
            // on Android 13, if system haptic feedback was disabled, `vibrator.vibrate()` won't work
            // but `view.performHapticFeedback()` with `FLAG_IGNORE_GLOBAL_SETTING` still works
            if (hasAmplitudeControl && amplitude != 0) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ve = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(ve)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            var flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            if (hapticOnKeyPress == InputFeedbackMode.Enabled) {
                // it says "Starting TIRAMISU only privileged apps can ignore user settings for touch feedback"
                // but we still seem to be able to use `FLAG_IGNORE_GLOBAL_SETTING`
                @Suppress("DEPRECATION")
                flags = flags or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            }
            view.performHapticFeedback(hfc, flags)
        }
    }

    enum class SoundEffect {
        Standard, SpaceBar, Delete, Return
    }

    private val audioManager = appContext.audioManager

    private fun soundEffectsEnabled(): Boolean {
        when (soundOnKeyPress) {
            InputFeedbackMode.Enabled -> {}
            InputFeedbackMode.Disabled -> return false
            InputFeedbackMode.FollowingSystem -> if (!systemSoundEffects) return false
        }
        return true
    }

    private fun audioEffect(effect: SoundEffect): Int = when (effect) {
        SoundEffect.Standard -> AudioManager.FX_KEYPRESS_STANDARD
        SoundEffect.SpaceBar -> AudioManager.FX_KEYPRESS_SPACEBAR
        SoundEffect.Delete -> AudioManager.FX_KEYPRESS_DELETE
        SoundEffect.Return -> AudioManager.FX_KEYPRESS_RETURN
    }

    private fun playSoundEffect(effect: SoundEffect, volumeScale: Float) {
        if (!soundEffectsEnabled() || physicalKeyboardSoundSuppressed) return
        val fx = audioEffect(effect)
        val configuredVolume = soundOnKeyPressVolume
        val volume = if (configuredVolume == 0) {
            if (volumeScale == 1f) -1f else volumeScale
        } else {
            configuredVolume / 100f * volumeScale
        }
        audioManager.playSoundEffect(fx, volume)
    }

    fun soundEffect(effect: SoundEffect) {
        playSoundEffect(effect, 1f)
    }

    @Volatile
    private var physicalKeyboardSoundSuppressed = false

    fun setPhysicalKeyboardSoundSuppressed(suppressed: Boolean) {
        physicalKeyboardSoundSuppressed = suppressed
    }

    fun physicalKeyDown(effect: SoundEffect) {
        playSoundEffect(effect, 1f)
    }

    fun physicalKeyUp() {
        // The system standard sample is the tightest available top-out prototype.
        playSoundEffect(SoundEffect.Standard, PHYSICAL_KEY_UP_VOLUME_SCALE)
    }

    @Volatile
    private var rippleSoundTracks: Array<AudioTrack>? = null
    private val rippleSoundPrepareStarted = AtomicBoolean(false)
    private var nextRippleTrack = 0

    /**
     * Warm the small static PCM buffers before the first desktop-aquarium key press.
     * Generation is procedural so the APK does not need to decode an audio asset on the input path.
     */
    fun prepareRippleSound() {
        if (!soundEffectsEnabled() || rippleSoundTracks != null) return
        if (!rippleSoundPrepareStarted.compareAndSet(false, true)) return
        rippleSoundTracks = runCatching {
            Array(RIPPLE_TRACK_COUNT) { createRippleSoundTrack(it) }
        }.onFailure {
            Timber.w(it, "Failed to prepare aquarium ripple sound")
        }.getOrNull()
    }

    fun prepareRippleSoundAsync() {
        if (rippleSoundTracks != null || rippleSoundPrepareStarted.get()) return
        Thread({ prepareRippleSound() }, "kboard-ripple-audio").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun rippleSound() {
        if (!soundEffectsEnabled() || physicalKeyboardSoundSuppressed) return
        val tracks = rippleSoundTracks ?: run {
            prepareRippleSoundAsync()
            return
        }
        val track = tracks[nextRippleTrack]
        nextRippleTrack = (nextRippleTrack + 1) % tracks.size
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            val configuredVolume = soundOnKeyPressVolume
            val volume = if (configuredVolume == 0) {
                RIPPLE_DEFAULT_VOLUME
            } else {
                configuredVolume / 100f * RIPPLE_DEFAULT_VOLUME
            }
            track.setVolume(volume)
            track.play()
        }
    }

    private fun createRippleSoundTrack(variant: Int): AudioTrack {
        val samples = ShortArray((RIPPLE_SAMPLE_RATE * RIPPLE_DURATION_SECONDS).toInt())
        val pitch = 0.96 + variant * 0.026
        var noiseState = 0x6D2B79F5 xor (variant * 0x13579B)
        var lowNoise = 0.0
        fun nextNoise(): Double {
            noiseState = noiseState xor (noiseState shl 13)
            noiseState = noiseState xor (noiseState ushr 17)
            noiseState = noiseState xor (noiseState shl 5)
            return (noiseState.toLong() and 0x7fffffffL) / 1073741824.0 - 1.0
        }
        samples.indices.forEach { index ->
            val t = index.toDouble() / RIPPLE_SAMPLE_RATE
            val attack = sin(PI * 0.5 * (t / 0.0022).coerceAtMost(1.0))
            val release = if (t < 0.27) 1.0 else {
                val tail = ((t - 0.27) / (RIPPLE_DURATION_SECONDS - 0.27)).coerceIn(0.0, 1.0)
                0.5 + 0.5 * kotlin.math.cos(PI * tail)
            }
            val whiteNoise = nextNoise()
            lowNoise += (whiteNoise - lowNoise) * 0.075
            val surfaceNoise = lowNoise
            val contactNoise = whiteNoise - lowNoise
            val contact = contactNoise * (1.0 - exp(-620.0 * t)) * exp(-145.0 * t)
            val bubblePhase = 2.0 * PI * (
                430.0 * pitch * t + 1640.0 * t * t - 1850.0 * t * t * t
                )
            val bubble = sin(bubblePhase + 0.12) * exp(-31.0 * t)
            val waterBodyPhase = 2.0 * PI * (168.0 * pitch * t - 42.0 * t * t)
            val waterBody = sin(waterBodyPhase + 0.38) * exp(-12.5 * t)
            val sheet = surfaceNoise * (1.0 - exp(-95.0 * t)) * exp(-18.0 * t)
            val satelliteDelay = 0.086 + variant * 0.004
            val satelliteTime = t - satelliteDelay
            val satellite = if (satelliteTime >= 0.0) {
                sin(2.0 * PI * (520.0 * pitch * satelliteTime +
                    720.0 * satelliteTime * satelliteTime)) * exp(-38.0 * satelliteTime)
            } else 0.0
            val sample = attack * release * (
                0.20 * contact + 0.38 * bubble + 0.25 * waterBody +
                    0.11 * sheet + 0.06 * satellite
                )
            samples[index] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RIPPLE_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .build()
            .also { it.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) }
    }

    private const val PHYSICAL_KEY_UP_VOLUME_SCALE = 0.38f
    private const val RIPPLE_SAMPLE_RATE = 44_100
    private const val RIPPLE_DURATION_SECONDS = 0.42
    private const val RIPPLE_TRACK_COUNT = 4
    private const val RIPPLE_DEFAULT_VOLUME = 0.56f

}
