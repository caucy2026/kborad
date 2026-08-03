/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class TextKeyboard(
    context: Context,
    theme: Theme,
    layout: List<List<KeyDef>> = Layout,
    private val alwaysShowLanguageKey: Boolean = false
) : BaseKeyboard(context, theme, layout) {

    enum class CapsState { None, Once, Lock }

    companion object {
        const val Name = "Text"
        const val FloatingName = "FloatingText"

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                TabKey(0.1f),
                AlphabetKey("Q", "1", 0.08f),
                AlphabetKey("W", "2", 0.08f),
                AlphabetKey("E", "3", 0.08f),
                AlphabetKey("R", "4", 0.08f),
                AlphabetKey("T", "5", 0.08f),
                AlphabetKey("Y", "6", 0.08f),
                AlphabetKey("U", "7", 0.08f),
                AlphabetKey("I", "8", 0.08f),
                AlphabetKey("O", "9", 0.08f),
                AlphabetKey("P", "0", 0.08f),
                BackspaceKey(0.1f)
            ),
            listOf(
                CapsLockKey(0.12f),
                PlainAlphabetKey("A", 0.081f),
                PlainAlphabetKey("S", 0.081f),
                PlainAlphabetKey("D", 0.081f),
                PlainAlphabetKey("F", 0.081f),
                PlainAlphabetKey("G", 0.081f),
                PlainAlphabetKey("H", 0.081f),
                PlainAlphabetKey("J", 0.081f),
                PlainAlphabetKey("K", 0.081f),
                PlainAlphabetKey("L", 0.081f),
                MainReturnKey(0.151f)
            ),
            listOf(
                CapsKey(0.16f),
                PlainAlphabetKey("Z", 0.08f),
                PlainAlphabetKey("X", 0.08f),
                PlainAlphabetKey("C", 0.08f),
                PlainAlphabetKey("V", 0.08f),
                PlainAlphabetKey("B", 0.08f),
                PlainAlphabetKey("N", 0.08f),
                PlainAlphabetKey("M", 0.08f),
                CommaKey(0.08f, KeyDef.Appearance.Variant.Normal),
                SymbolKey(".", 0.08f),
                SymbolKey("'", 0.08f),
                SpacerKey(0.04f)
            ),
            listOf(
                LayoutSwitchKey("?123", PickerWindow.Key.Symbol.name, 0.08f),
                ImagePickerSwitchKey(
                    R.drawable.ic_baseline_tag_faces_24,
                    PickerWindow.Key.Emoji,
                    0.08f,
                    KeyDef.Appearance.Variant.Alternative
                ),
                LanguageKey(0.08f),
                SpaceKey(),
                CursorKey(
                    R.drawable.ic_baseline_keyboard_arrow_left_24,
                    FcitxKeyMapping.FcitxKey_Left
                ),
                CursorKey(
                    R.drawable.ic_baseline_keyboard_arrow_right_24,
                    FcitxKeyMapping.FcitxKey_Right
                ),
                LayoutSwitchKey("?123", PickerWindow.Key.Symbol.name, 0.08f)
            )
        )

        val FloatingLayout: List<List<KeyDef>> = listOf(
            listOf(
                AlphabetKey("Q", "1", 0.1f, textSize = 22f),
                AlphabetKey("W", "2", 0.1f, textSize = 22f),
                AlphabetKey("E", "3", 0.1f, textSize = 22f),
                AlphabetKey("R", "4", 0.1f, textSize = 22f),
                AlphabetKey("T", "5", 0.1f, textSize = 22f),
                AlphabetKey("Y", "6", 0.1f, textSize = 22f),
                AlphabetKey("U", "7", 0.1f, textSize = 22f),
                AlphabetKey("I", "8", 0.1f, textSize = 22f),
                AlphabetKey("O", "9", 0.1f, textSize = 22f),
                AlphabetKey("P", "0", 0.1f, textSize = 22f)
            ),
            listOf(
                SpacerKey(0.05f),
                PlainAlphabetKey("A", 0.1f, textSize = 22f),
                PlainAlphabetKey("S", 0.1f, textSize = 22f),
                PlainAlphabetKey("D", 0.1f, textSize = 22f),
                PlainAlphabetKey("F", 0.1f, textSize = 22f),
                PlainAlphabetKey("G", 0.1f, textSize = 22f),
                PlainAlphabetKey("H", 0.1f, textSize = 22f),
                PlainAlphabetKey("J", 0.1f, textSize = 22f),
                PlainAlphabetKey("K", 0.1f, textSize = 22f),
                PlainAlphabetKey("L", 0.1f, textSize = 22f),
                SpacerKey(0.05f)
            ),
            listOf(
                CapsKey(0.13f),
                PlainAlphabetKey("Z", 0.1f, textSize = 22f),
                PlainAlphabetKey("X", 0.1f, textSize = 22f),
                PlainAlphabetKey("C", 0.1f, textSize = 22f),
                PlainAlphabetKey("V", 0.1f, textSize = 22f),
                PlainAlphabetKey("B", 0.1f, textSize = 22f),
                PlainAlphabetKey("N", 0.1f, textSize = 22f),
                PlainAlphabetKey("M", 0.1f, textSize = 22f),
                BackspaceKey(0.17f)
            ),
            listOf(
                ImagePickerSwitchKey(
                    R.drawable.ic_baseline_tag_faces_24,
                    PickerWindow.Key.Emoji,
                    0.14f,
                    KeyDef.Appearance.Variant.Alternative
                ),
                LanguageKey(0.14f),
                SpaceKey(),
                LayoutSwitchKey(
                    "?123", PickerWindow.Key.Symbol.name, 0.14f,
                    border = KeyDef.Appearance.Border.Special,
                    viewId = R.id.button_punctuation
                ),
                ReturnKey(0.14f)
            )
        )
    }

    val caps: ImageKeyView by lazy { findViewById(R.id.button_caps) }
    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val quickphrase: ImageKeyView by lazy { findViewById(R.id.button_quickphrase) }
    private val lang: ImageKeyView? by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    private val keepLettersUppercase by AppPrefs.getInstance().keyboard.keepLettersUppercase

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    private val textKeys: List<TextKeyView> by lazy {
        allViews.filterIsInstance(TextKeyView::class.java).toList()
    }

    private var capsState: CapsState = CapsState.None

    private fun transformAlphabet(c: String): String {
        return when (capsState) {
            CapsState.None -> c.lowercase()
            else -> c.uppercase()
        }
    }

    private var punctuationMapping: Map<String, String> = mapOf()
    private fun transformPunctuation(p: String) = punctuationMapping.getOrDefault(p, p)

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.FcitxKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard -> {
                    when (capsState) {
                        CapsState.None -> {
                            transformed = action.copy(act = action.act.lowercase())
                        }
                        CapsState.Once -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.Shift)
                            )
                            switchCapsState()
                        }
                        CapsState.Lock -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                            )
                        }
                    }
                }
                KeyActionListener.Source.Popup -> {
                    if (capsState == CapsState.Once) {
                        switchCapsState()
                    }
                }
            }
            is KeyAction.CapsAction -> switchCapsState(action.lock)
            else -> {}
        }
        super.onAction(transformed, source)
    }

    override fun onAttach() {
        capsState = CapsState.None
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        if (`return`.def.variant != KeyDef.Appearance.Variant.Alternative) {
            `return`.img.imageResource = returnDrawable
        }
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        punctuationMapping = mapping
        updatePunctuationKeys()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = buildString {
            append(
                if (
                    ime.displayName.equals("Pinyin", ignoreCase = true) ||
                    ime.uniqueName.equals("pinyin", ignoreCase = true)
                ) "拼音" else ime.displayName
            )
            ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" ($it)") }
        }
        if (capsState != CapsState.None) {
            switchCapsState()
        }
    }

    private fun transformPopupPreview(c: String): String {
        if (c.length != 1) return c
        if (c[0].isLetter()) return transformAlphabet(c)
        return transformPunctuation(c)
    }

    override fun onPopupAction(action: PopupAction) {
        val newAction = when (action) {
            is PopupAction.PreviewAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.PreviewUpdateAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.ShowKeyboardAction -> {
                when (action.keyboard) {
                    is KeyDef.Popup.Keyboard.Preset -> {
                        val label = action.keyboard.label
                        if (label.length == 1 && label[0].isLetter())
                            action.copy(
                                keyboard = action.keyboard.copy(label = transformAlphabet(label))
                            )
                        else action
                    }
                    is KeyDef.Popup.Keyboard.Explicit -> action
                }
            }
            else -> action
        }
        super.onPopupAction(newAction)
    }

    private fun switchCapsState(lock: Boolean = false) {
        capsState =
            if (lock) {
                when (capsState) {
                    CapsState.Lock -> CapsState.None
                    else -> CapsState.Lock
                }
            } else {
                when (capsState) {
                    CapsState.None -> CapsState.Once
                    else -> CapsState.None
                }
            }
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    private fun updateCapsButtonIcon() {
        caps.img.apply {
            imageResource = when (capsState) {
                CapsState.None -> R.drawable.ic_capslock_none
                CapsState.Once -> R.drawable.ic_capslock_once
                CapsState.Lock -> R.drawable.ic_capslock_lock
            }
        }
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang?.visibility = if (alwaysShowLanguageKey || visible) View.VISIBLE else View.GONE
    }

    private fun updateAlphabetKeys() {
        textKeys.forEach {
            val appearance = it.def as? KeyDef.Appearance.Text ?: return@forEach
            it.mainText.text = appearance.displayText.let { str ->
                if (str.length != 1 || !str[0].isLetter()) return@forEach
                if (keepLettersUppercase) str.uppercase() else transformAlphabet(str)
            }
        }
    }

    private fun updatePunctuationKeys() {
        textKeys.forEach {
            if (it is AltTextKeyView) {
                it.def as KeyDef.Appearance.AltText
                it.altText.text = transformPunctuation(it.def.altText)
            } else {
                it.def as KeyDef.Appearance.Text
                it.mainText.text = it.def.displayText.let { str ->
                    if (str.isEmpty() || str[0].run { isLetter() || isWhitespace() }) return@forEach
                    transformPunctuation(str)
                }
            }
        }
    }

}