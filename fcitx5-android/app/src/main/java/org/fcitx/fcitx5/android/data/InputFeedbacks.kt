/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import android.media.AudioManager
import android.media.AudioAttributes
import android.media.SoundPool
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
    private var rippleSoundPool: SoundPool? = null
    @Volatile
    private var rippleSoundIds = IntArray(0)
    @Volatile
    private var rippleSoundsReady = false
    private val rippleSoundPrepareStarted = AtomicBoolean(false)
    private var nextRippleSound = 0
    private var activeRippleStream = 0

    /**
     * Decode the short CC0 field recordings before the first desktop-aquarium key press. SoundPool
     * keeps the samples resident and exposes a low-latency path without synthesizing an electronic
     * chirp on the interaction thread.
     */
    fun prepareRippleSound() {
        if (!soundEffectsEnabled() || rippleSoundPool != null) return
        if (!rippleSoundPrepareStarted.compareAndSet(false, true)) return
        runCatching {
            val pool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            var loadedCount = 0
            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0 && ++loadedCount == RIPPLE_SOUND_RESOURCES.size) {
                    rippleSoundsReady = true
                }
            }
            rippleSoundPool = pool
            rippleSoundIds = RIPPLE_SOUND_RESOURCES.map { pool.load(appContext, it, 1) }
                .toIntArray()
        }.onFailure {
            rippleSoundPool?.release()
            rippleSoundPool = null
            rippleSoundIds = IntArray(0)
            rippleSoundPrepareStarted.set(false)
            Timber.w(it, "Failed to prepare aquarium ripple sound")
        }
    }

    fun prepareRippleSoundAsync() {
        if (rippleSoundPool != null || rippleSoundPrepareStarted.get()) return
        Thread({ prepareRippleSound() }, "kboard-ripple-audio").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun rippleSound() {
        if (!soundEffectsEnabled() || physicalKeyboardSoundSuppressed) return
        val pool = rippleSoundPool ?: run {
            prepareRippleSoundAsync()
            return
        }
        if (!rippleSoundsReady || rippleSoundIds.isEmpty()) return
        val soundIndex = nextRippleSound
        nextRippleSound = (nextRippleSound + 1) % rippleSoundIds.size
        runCatching {
            // One water contact per key-down. Stop the preceding tail before playing the next
            // sample so rapid typing never turns into layered noise or a second release sound.
            if (activeRippleStream != 0) pool.stop(activeRippleStream)
            val configuredVolume = soundOnKeyPressVolume
            val volume = if (configuredVolume == 0) {
                RIPPLE_DEFAULT_VOLUME
            } else {
                configuredVolume / 100f * RIPPLE_DEFAULT_VOLUME
            }
            activeRippleStream = pool.play(
                rippleSoundIds[soundIndex],
                volume,
                volume,
                1,
                0,
                RIPPLE_PLAYBACK_RATES[soundIndex]
            )
        }
    }

    private const val PHYSICAL_KEY_UP_VOLUME_SCALE = 0.38f
    private const val RIPPLE_DEFAULT_VOLUME = 0.30f
    private val RIPPLE_SOUND_RESOURCES = intArrayOf(
        R.raw.aquarium_water_touch_1,
        R.raw.aquarium_water_touch_2,
        R.raw.aquarium_water_touch_3,
        R.raw.aquarium_water_touch_4
    )
    private val RIPPLE_PLAYBACK_RATES = floatArrayOf(0.98f, 1.01f, 0.96f, 1.03f)

}
