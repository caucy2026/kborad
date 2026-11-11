/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

import org.gradle.api.JavaVersion

object Versions {

    val java = JavaVersion.VERSION_11
    const val compileSdk = 36
    const val minSdk = 23
    const val targetSdk = 36

    const val defaultCMake = "3.31.6"
    const val defaultNDK = "28.0.13004108"
    const val defaultBuildTools = "36.1.0"

    // NOTE: increase this value to bump version code
    const val baseVersionCode = 29
    const val baseVersionName = "1.4.12"

    val supportedABIs = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    const val fallbackABI = "arm64-v8a"

    fun calculateVersionCode(abi: String = fallbackABI): Int {
        // 1.4.12 的 arm64 包已经用过 292。撤回那次提交后 arm64 用 293，x86 与 x86_64 顺延，避免版本号撞车。
        val abiId = when (abi) {
            "armeabi-v7a" -> 1
            "arm64-v8a" -> 3
            "x86" -> 4
            "x86_64" -> 5
            else -> 0
        }
        return baseVersionCode * 10 + abiId
    }
}
