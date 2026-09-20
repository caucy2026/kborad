/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

object ImeDisplaySwitchPolicy {
    enum class Route {
        SYSTEM,
        KEMI_EXPANDED_HOST
    }

    private const val KEMI_EXPANDED_KEYBOARD =
        "com.newlinksz.kemi.remote.EXPANDED_KEYBOARD"

    fun routeFor(privateImeOptions: String?): Route =
        if (privateImeOptions == KEMI_EXPANDED_KEYBOARD) {
            Route.KEMI_EXPANDED_HOST
        } else {
            Route.SYSTEM
        }
}
