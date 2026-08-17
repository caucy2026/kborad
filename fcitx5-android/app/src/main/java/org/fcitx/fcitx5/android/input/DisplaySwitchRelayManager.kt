/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import timber.log.Timber

/** Enables KBoard's same-package, one-shot IME relay on platform-signed V900 builds. */
object DisplaySwitchRelayManager {

    fun ensureEnabled(context: Context): Boolean {
        val relayId = ComponentName(context, DisplaySwitchInputMethodService::class.java)
            .flattenToShortString()
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ).orEmpty()
        if (containsIme(current, relayId)) return true

        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("Cannot auto-enable display-switch relay: WRITE_SECURE_SETTINGS denied")
            return false
        }

        // Preserve every existing IME and subtype entry; only append our own relay.
        val updated = if (current.isBlank()) relayId else "$current:$relayId"
        val written = Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS,
            updated
        )
        if (written) {
            Timber.i("Enabled same-package display-switch IME relay")
        } else {
            Timber.e("Failed to enable same-package display-switch IME relay")
        }
        return written
    }

    internal fun containsIme(enabledInputMethods: String, imeId: String): Boolean =
        enabledInputMethods.split(':').any { it.substringBefore(';') == imeId }
}
