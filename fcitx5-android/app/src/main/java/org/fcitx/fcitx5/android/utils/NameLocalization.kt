/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

/**
 * Maps Fcitx internal English names to Chinese display names.
 * Add new mappings as needed when new input methods or addons are introduced.
 */
object NameLocalization {

    private val imeNameMap = mapOf(
        "Pinyin" to "拼音",
        "Shuangpin" to "双拼",
        "Wubi" to "五笔",
        "Wubi Pinyin" to "五笔拼音",
        "Dianbaoma" to "电报码",
        "Ziranma" to "自然码",
        "English" to "英文",
        "Keyboard" to "键盘",
        "Keyboard-English" to "英文键盘",
    )

    private val addonNameMap = mapOf(
        "Android Frontend" to "Android 前端",
        "Android Keyboard" to "Android 键盘",
        "Simplified and Traditional Chinese Translation" to "简繁转换",
        "Clipboard" to "剪贴板",
        "Full width character" to "全角字符",
        "Lua IME API" to "Lua 输入法接口",
        "Input method selector" to "输入法选择器",
        "Lua Addon Loader" to "Lua 附加组件加载器",
        "Android Toast & Notification" to "Android 通知",
        "Pinyin" to "拼音",
        "Extra Pinyin functionality" to "拼音扩展功能",
        "Punctuation" to "标点符号",
        "Quick Phrase" to "快速输入",
        "Spell" to "拼写检查",
        "Table" to "码表",
        "Unicode" to "Unicode",
        "Notifications" to "通知",
    )

    /** Attempt to translate an input method English display name to Chinese. */
    fun imeName(name: String): String = imeNameMap[name] ?: name

    /** Attempt to translate an addon English display name to Chinese. */
    fun addonName(name: String): String = addonNameMap[name] ?: name
}
