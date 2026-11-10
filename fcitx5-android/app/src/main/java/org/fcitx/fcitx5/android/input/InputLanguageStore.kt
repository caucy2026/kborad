/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * 用户选择的输入法唯一名。输入法在开机未解锁时也会运行，因此固定使用设备加密存储。
 * 没有记录时不创建文件，调用方使用拼音默认值。读取或写入失败时返回空结果，不把异常抛给输入法。
 */
internal object InputLanguageStore {
    private const val PREFS = "kboard_input_language"
    private const val KEY = "unique_name"

    private fun protectedContext(context: Context): Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): String? {
        val protected = runCatching { prefs(protectedContext(context)) }.getOrNull() ?: return null
        protected.getString(KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val legacy = runCatching { prefs(context) }.getOrNull() ?: return null
        val saved = legacy.getString(KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        runCatching { protected.edit().putString(KEY, saved).commit() }
        return saved
    }

    fun preferred(context: Context): String =
        InputLanguageSession.choice?.takeIf { it.isNotBlank() }
            ?: read(context)
            ?: InputLanguagePolicy.DEFAULT_INPUT_METHOD

    fun saveIfChanged(context: Context, uniqueName: String): Boolean {
        if (!InputLanguagePolicy.shouldSave(null, uniqueName, read(context))) return false
        return runCatching {
            prefs(protectedContext(context)).edit().putString(KEY, uniqueName).commit()
        }.getOrDefault(false)
    }
}
