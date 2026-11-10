/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input

import org.fcitx.fcitx5.android.core.FcitxEvent

/**
 * 未记录过选择时用拼音。用户切换后的语言只在选择变化时写入本地，
 * 密码框等能力变化、以及本次启动为了恢复选择而做的激活，都不覆盖已保存的值。
 */
object InputLanguagePolicy {
    const val DEFAULT_INPUT_METHOD = "pinyin"
    const val ENGLISH_KEYBOARD = "keyboard-us"

    fun allowsRestore(hasTextField: Boolean, blocksInputMethod: Boolean): Boolean =
        hasTextField && !blocksInputMethod

    /**
     * @param enabled 当前已启用的输入法。引擎尚未给出列表时返回 null，调用方稍后重试。
     * 已保存但不在列表中的名称回退到拼音，再不行则保持现状。
     */
    fun resolve(saved: String?, enabled: Set<String>): String? {
        if (enabled.isEmpty()) return null
        val preferred = saved?.takeIf { it.isNotBlank() } ?: DEFAULT_INPUT_METHOD
        if (preferred in enabled) return preferred
        if (DEFAULT_INPUT_METHOD in enabled) return DEFAULT_INPUT_METHOD
        return null
    }

    fun shouldApply(current: String, target: String): Boolean = current != target

    fun shouldPersist(reason: FcitxEvent.SwitchInputMethodEvent.Reason): Boolean =
        reason != FcitxEvent.SwitchInputMethodEvent.Reason.CapabilityChanged

    /** 恢复动作自己触发的切换，以及与已保存值相同的选择，都不再写盘。 */
    fun shouldSave(restoreTarget: String?, selected: String, alreadyStored: String?): Boolean {
        if (selected.isBlank()) return false
        if (restoreTarget != null && restoreTarget == selected) return false
        return alreadyStored != selected
    }

    fun isChinese(uniqueName: String): Boolean {
        val name = uniqueName.lowercase()
        return name == DEFAULT_INPUT_METHOD ||
            name.contains("pinyin") ||
            name.contains("chinese") ||
            name.contains("shuangpin") ||
            name.contains("wubi") ||
            name.contains("cangjie") ||
            name.startsWith("zh")
    }

    fun spaceLabel(uniqueName: String): String? = when {
        isChinese(uniqueName) -> "拼音"
        uniqueName.equals(ENGLISH_KEYBOARD, ignoreCase = true) -> "English"
        else -> null
    }
}

/**
 * 进程内只恢复一次。服务因切屏被重建时，原生引擎里的当前语言仍然有效，不能再次覆盖。
 */
internal object InputLanguageSession {
    @Volatile
    var applied: Boolean = false

    @Volatile
    var choice: String? = null

    @Volatile
    var restoreTarget: String? = null

    fun tryMarkApplied(): Boolean = synchronized(this) {
        if (applied) return false
        applied = true
        true
    }
}
