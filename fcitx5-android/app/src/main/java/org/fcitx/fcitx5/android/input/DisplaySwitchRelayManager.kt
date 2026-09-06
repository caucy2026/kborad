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

        return runCatching {
            // The APK already owns WRITE_SECURE_SETTINGS on platform-signed V900 builds. Do not
            // invoke `/system/bin/ime`: its Binder command is restricted to the shell/root UID,
            // so a child process launched by KBoard still exits with code 255. Updating the
            // current user's secure setting through ContentResolver both preserves every existing
            // IME/subtype entry and notifies InputMethodManagerService's settings observer.
            val written = Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS,
                appendIme(current, relayId)
            )
            val enabled = written && containsIme(readEnabledImes(context), relayId)
            if (enabled) {
                Timber.i("Enabled same-package display-switch IME relay")
            } else {
                Timber.e("Failed to enable same-package display-switch IME relay: written=%s", written)
            }
            enabled
        }.getOrElse {
            Timber.e(it, "Failed to update enabled input methods for display-switch relay")
            false
        }
    }

    private fun readEnabledImes(context: Context): String =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ).orEmpty()

    internal fun containsIme(enabledInputMethods: String, imeId: String): Boolean =
        enabledInputMethods.split(':').any { it.substringBefore(';') == imeId }

    internal fun appendIme(enabledInputMethods: String, imeId: String): String = when {
        containsIme(enabledInputMethods, imeId) -> enabledInputMethods
        enabledInputMethods.isBlank() -> imeId
        else -> "$enabledInputMethods:$imeId"
    }
}
