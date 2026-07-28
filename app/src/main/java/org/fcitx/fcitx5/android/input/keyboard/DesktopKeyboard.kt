/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.allViews
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.InputFeedbacks
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class DesktopKeyboard(context: Context, theme: Theme) :
    BaseKeyboard(context, theme, Layout, createHeader(context)) {

    init {
        setPadding(0, context.dp(4), 0, context.dp(12))
    }

    companion object {
        const val Name = "Desktop"
        private const val LayoutWidthInKeyUnits = 15f

        private fun Context.dp(value: Int) =
            (value * resources.displayMetrics.density).roundToInt()

        // Preedit is layered over this transparent composition area.
        private fun createHeader(context: Context) = View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        private val ShiftedSymbols = mapOf(
            "`" to "~", "1" to "!", "2" to "@", "3" to "#", "4" to "$",
            "5" to "%", "6" to "^", "7" to "&", "8" to "*", "9" to "(",
            "0" to ")", "-" to "_", "=" to "+", "[" to "{", "]" to "}",
            "\\" to "|", ";" to ":", "'" to "\"", "," to "<", "." to ">", "/" to "?"
        )

        private fun characterKey(label: String, width: Float) = KeyDef(
            KeyDef.Appearance.Text(
                displayText = label,
                textSize = 16f,
                percentWidth = width,
                border = KeyDef.Appearance.Border.On
            ),
            setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(label.lowercase())))
        )

        private fun shiftedSymbolKey(
            primary: String,
            secondary: String,
            width: Float
        ) = KeyDef(
            KeyDef.Appearance.AltText(
                displayText = primary,
                altText = secondary,
                textSize = 16f,
                percentWidth = width,
                variant = KeyDef.Appearance.Variant.Normal,
                border = KeyDef.Appearance.Border.On
            ),
            setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(primary)))
        )

        private fun languageKey(width: Float) = KeyDef(
            KeyDef.Appearance.Image(
                src = R.drawable.ic_baseline_language_24,
                percentWidth = width,
                variant = KeyDef.Appearance.Variant.Alternative,
                border = KeyDef.Appearance.Border.On
            ),
            setOf(KeyDef.Behavior.Press(KeyAction.LangSwitchAction))
        )

        // ── Layout: 6 rows, weights match kemi-bt-board globalKeyboardOverlay ──
        val Layout: List<List<KeyDef>> = listOf(
            // Row 0: ESC + F1-F12  (weightSum=13)
            listOf(
                DesktopSymKey("Esc", FcitxKeyMapping.FcitxKey_Escape, 1f / 13f),
                *IntRange(1, 12).map { f ->
                    DesktopSymKey("F$f", FcitxKeyMapping.FcitxKey_F1 + f - 1, 1f / 13f)
                }.toTypedArray()
            ),
            // Row 1: ` 1-0 - = Backspace  (weightSum=15)
            listOf(
                shiftedSymbolKey("`", "~", 1f / 15f),
                shiftedSymbolKey("1", "!", 1f / 15f),
                shiftedSymbolKey("2", "@", 1f / 15f),
                shiftedSymbolKey("3", "#", 1f / 15f),
                shiftedSymbolKey("4", "$", 1f / 15f),
                shiftedSymbolKey("5", "%", 1f / 15f),
                shiftedSymbolKey("6", "^", 1f / 15f),
                shiftedSymbolKey("7", "&", 1f / 15f),
                shiftedSymbolKey("8", "*", 1f / 15f),
                shiftedSymbolKey("9", "(", 1f / 15f),
                shiftedSymbolKey("0", ")", 1f / 15f),
                shiftedSymbolKey("-", "_", 1f / 15f),
                shiftedSymbolKey("=", "+", 1f / 15f),
                DesktopSymKey(
                    "Backspace", FcitxKeyMapping.FcitxKey_BackSpace, 2f / 15f,
                    repeat = true, soundEffect = InputFeedbacks.SoundEffect.Delete
                )
            ),
            // Row 2: Tab Q-P [ ] \  (weightSum=15)
            listOf(
                DesktopSymKey("Tab", FcitxKeyMapping.FcitxKey_Tab, 1.5f / 15f),
                *"QWERTYUIOP".map { characterKey(it.toString(), 1f / 15f) }.toTypedArray(),
                shiftedSymbolKey("[", "{", 1f / 15f),
                shiftedSymbolKey("]", "}", 1f / 15f),
                shiftedSymbolKey("\\", "|", 1.5f / 15f)
            ),
            // Row 3: Caps A-L ; ' Enter  (weightSum=15)
            listOf(
                DesktopSymKey("Caps", FcitxKeyMapping.FcitxKey_Caps_Lock, 1.8f / 15f),
                *"ASDFGHJKL".map { characterKey(it.toString(), 1f / 15f) }.toTypedArray(),
                shiftedSymbolKey(";", ":", 1f / 15f),
                shiftedSymbolKey("'", "\"", 1f / 15f),
                DesktopSymKey(
                    "Enter", FcitxKeyMapping.FcitxKey_Return, 2.2f / 15f,
                    soundEffect = InputFeedbacks.SoundEffect.Return
                )
            ),
            // Row 4: Shift Z-M , . / Shift  (weightSum=15)
            listOf(
                DesktopModifierKey("Shift", KeyState.Shift, 2.2f / 15f),
                *"ZXCVBNM".map { characterKey(it.toString(), 1f / 15f) }.toTypedArray(),
                shiftedSymbolKey(",", "<", 1f / 15f),
                shiftedSymbolKey(".", ">", 1f / 15f),
                shiftedSymbolKey("/", "?", 1f / 15f),
                DesktopModifierKey("Shift", KeyState.Shift, 2.8f / 15f)
            ),
            // Row 5: Ctrl Option 中/英 ──SPACE── ⌘ ← [↑/↓] →
            listOf(
                DesktopModifierKey("Ctrl", KeyState.Ctrl, 2.2f / 18.3f),
                DesktopModifierKey("Option", KeyState.Alt, 1.6f / 18.3f),
                languageKey(1.6f / 18.3f),
                DesktopSpaceKey(8f / 18.3f),
                DesktopModifierKey("\u2318", KeyState.Meta, 1.6f / 18.3f),
                DesktopSymKey("←", FcitxKeyMapping.FcitxKey_Left, 1f / 18.3f, repeat = true),
                KeyDef(
                    KeyDef.Appearance.VerticalGroup(
                        listOf(
                            DesktopSymKey("↑", FcitxKeyMapping.FcitxKey_Up, 1f, repeat = true),
                            DesktopSymKey("↓", FcitxKeyMapping.FcitxKey_Down, 1f, repeat = true)
                        ),
                        1.3f / 18.3f
                    ),
                    emptySet()
                ),
                DesktopSymKey("→", FcitxKeyMapping.FcitxKey_Right, 1f / 18.3f, repeat = true)
            )
        )
    }

    // ── Runtime state ──
    private val modifierStates = linkedSetOf<KeyState>()
    private val textKeys by lazy { allViews.filterIsInstance<TextKeyView>() }
    private var currentImeName: String = ""

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        when (action) {
            is KeyAction.ModifierAction -> {
                if (!modifierStates.add(action.state)) modifierStates.remove(action.state)
                updateModifierKeys()
                return
            }
            else -> {}
        }

        // Ctrl+Space → language switch
        if (action is KeyAction.SymAction &&
            action.sym == KeySym(FcitxKeyMapping.FcitxKey_space) &&
            KeyState.Ctrl in modifierStates
        ) {
            super.onAction(KeyAction.LangSwitchAction, source)
            modifierStates.clear()
            updateModifierKeys()
            return
        }

        val states = KeyStates(*(modifierStates + KeyState.Virtual).toTypedArray())
        val transformed = when (action) {
            is KeyAction.FcitxKeyAction -> {
                val shifted = KeyState.Shift in modifierStates
                val label = ShiftedSymbols[action.act]?.takeIf { shifted }
                    ?: if (shifted) action.act.uppercase() else action.act.lowercase()
                action.copy(act = label, states = states)
            }
            is KeyAction.SymAction -> action.copy(states = states)
            else -> action
        }
        super.onAction(transformed, source)
        if (modifierStates.isNotEmpty()) {
            modifierStates.clear()
            updateModifierKeys()
        }
    }

    override fun onAttach() {
        modifierStates.clear()
        updateModifierKeys()
        updateLetterKeys()
        updateSpaceLanguageLabel()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        currentImeName = ime.uniqueName
        updateSpaceLanguageLabel()
    }

    private fun updateSpaceLanguageLabel() {
        val langLabel = if (currentImeName.contains("pinyin", ignoreCase = true) ||
            currentImeName.contains("chinese", ignoreCase = true) ||
            currentImeName.contains("shuangpin", ignoreCase = true) ||
            currentImeName.contains("wubi", ignoreCase = true) ||
            currentImeName.contains("cangjie", ignoreCase = true) ||
            currentImeName.contains("zh", ignoreCase = true)
        ) "拼 音" else "English"
        findViewById<View>(R.id.button_space)?.let { space ->
            (space as? TextKeyView)?.mainText?.text = langLabel
        }
    }

    private fun updateModifierKeys() {
        textKeys.forEach { key ->
            val label = (key.def as? KeyDef.Appearance.Text)?.displayText ?: return@forEach
            val state = when (label) {
                "Ctrl" -> KeyState.Ctrl
                "Option" -> KeyState.Alt
                "\u2318" -> KeyState.Meta   // ⌘
                "Shift" -> KeyState.Shift
                else -> return@forEach
            }
            key.isSelected = state in modifierStates
        }
    }

    private fun updateLetterKeys() {
        textKeys.forEach { key ->
            val appearance = key.def as? KeyDef.Appearance.Text ?: return@forEach
            val label = appearance.displayText
            if (label.length == 1 && label[0].isLetter()) {
                key.mainText.text = label.uppercase()
            }
        }
    }

    fun firstRowTopOnScreen(): Int? {
        if (!isLaidOut || childCount < 2) return null
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1] + getChildAt(1).top
    }

    fun operationButtonCentersOnScreen(): Pair<Int, Int>? {
        if (!isLaidOut) return null
        val option = textKeys.firstOrNull { key ->
            (key.def as? KeyDef.Appearance.Text)?.displayText == "Option"
        } ?: return null
        val command = textKeys.firstOrNull { key ->
            (key.def as? KeyDef.Appearance.Text)?.displayText == "\u2318"
        } ?: return null
        fun View.centerXOnScreen(): Int {
            val location = IntArray(2)
            getLocationOnScreen(location)
            return location[0] + width / 2
        }
        return option.centerXOnScreen() to command.centerXOnScreen()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val topPadding = context.dp(4)
        val bottomPadding = context.dp(12)
        val horizontalPadding = 0
        val availableHeight = h - topPadding - bottomPadding
        val rowHeight = (w - horizontalPadding * 2) / LayoutWidthInKeyUnits
        val compositionHeight = (availableHeight - rowHeight * 6f)
            .roundToInt()
            .coerceAtLeast(0)
        getChildAt(0).updateLayoutParams<LayoutParams> {
            height = compositionHeight
        }
        setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
    }
}