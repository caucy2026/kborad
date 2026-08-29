/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.input.keyboard.aquarium.DesktopAquariumView
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.GestureType
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.OnGestureListener
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class DesktopKeyboard private constructor(
    context: Context,
    theme: Theme,
    private val aquariumView: DesktopAquariumView,
    private val aquariumTransitionOverlay: ImageView,
    private val compositionHeader: FrameLayout,
    private val touchpadView: DesktopTouchpadView
) :
    BaseKeyboard(
        context,
        theme,
        Layout,
        compositionHeader,
        KeyVisualMetrics(horizontalMarginDp = 2, verticalMarginDp = 2, radiusDp = 8f),
        (DESKTOP_OPERATION_WATER_HEIGHT_DP * context.resources.displayMetrics.density).roundToInt()
    ) {

    constructor(context: Context, theme: Theme) : this(
        context,
        theme,
        DesktopAquariumView(context),
        createAquariumTransitionOverlay(context),
        createHeader(context),
        DesktopTouchpadView(context)
    )

    init {
        // BaseKeyboard creates headers at its generic 48dp height. Desktop mode has a dedicated
        // fixed touch surface; set it once during construction instead of mutating LayoutParams
        // from onMeasure(), which caused a 30Hz requestLayout loop beside the TextureView.
        compositionHeader.layoutParams.height = context.dp(DESKTOP_TOUCHPAD_HEIGHT_DP)
        addView(
            aquariumView,
            0,
            ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        )
        addView(
            aquariumTransitionOverlay,
            1,
            ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        )
        aquariumView.bindTransitionOverlay(aquariumTransitionOverlay)
        compositionHeader.addView(
            touchpadView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setBackgroundColor(DESKTOP_DECK_COLOR)
        setPadding(0, 0, 0, 0)
        aquariumView.isClickable = true
        allViews.filterIsInstance<KeyView>().forEach {
            it.setPhysicalKeyStyle(true)
            if (it.def.viewId == R.id.button_backspace) {
                // Backspace is a destructive key: distinguish it at rest without changing its
                // size, hit target, label contrast, or the shared press/release animation.
                it.setAquariumDepthStyle(
                    enabled = true,
                    normalTopColor = DESKTOP_BACKSPACE_TOP_COLOR,
                    normalBottomColor = DESKTOP_BACKSPACE_BOTTOM_COLOR,
                    normalStrokeColor = DESKTOP_BACKSPACE_STROKE_COLOR
                )
            } else {
                it.setAquariumDepthStyle(true)
            }
            it.keyDownSoundEnabled = false
            it.physicalReleaseSoundEnabled = false
        }
        configureHeldModifierKeys()
        touchpadView.onMouseMove = { dx, dy ->
            onAction(KeyAction.RemoteMouseMoveAction(dx, dy))
        }
        touchpadView.onMouseButton = { button, down ->
            onAction(KeyAction.RemoteMouseButtonAction(button, down))
        }
        touchpadView.onSystemKey = { keyCode, down ->
            onAction(KeyAction.RemoteSystemKeyAction(keyCode, down))
        }
        InputFeedbacks.prepareRippleSoundAsync()
    }

    /**
     * Keep the six physical-key rows at their designed height when the IME is hosted on a
     * display with a bottom navigation bar. The desktop header is deliberately the flexible
     * region: taking the system inset from its large touchpad is substantially more usable than
     * squeezing every key row or clipping the bottom controls.
     */
    fun setSystemBottomInset(bottomInsetPx: Int) {
        val targetHeight = (
            context.dp(DESKTOP_TOUCHPAD_HEIGHT_DP) - bottomInsetPx.coerceAtLeast(0)
        ).coerceAtLeast(context.dp(DESKTOP_TOUCHPAD_MIN_HEIGHT_DP))
        if (compositionHeader.layoutParams.height == targetHeight) return
        compositionHeader.layoutParams.height = targetHeight
        compositionHeader.requestLayout()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // An injected mouse can be positioned over this IME when editor and keyboard share a
        // display. The keyboard must never consume it as a new virtual-pad/key gesture.
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        // The header is a real mouse surface, not pond space. Do not mix mouse gestures with
        // aquarium feeding/ripple feedback or play a water sound on every cursor movement.
        if (event.y >= compositionHeader.bottom) {
            handleAquariumTouch(
                event,
                event.x / width.coerceAtLeast(1),
                event.y / height.coerceAtLeast(1)
            )
        }
        // Observation only: key hit testing, gestures, repeat and text input keep their original
        // event stream. The clickable aquarium consumes otherwise-empty pond space.
        return super.dispatchTouchEvent(event)
    }

    fun onExternalPondTouch(event: MotionEvent): Boolean {
        val location = IntArray(2)
        aquariumView.getLocationOnScreen(location)
        handleAquariumTouch(
            event,
            (event.rawX - location[0]) / aquariumView.width.coerceAtLeast(1),
            (event.rawY - location[1]) / aquariumView.height.coerceAtLeast(1)
        )
        return true
    }

    private fun handleAquariumTouch(event: MotionEvent, normalizedX: Float, normalizedY: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                aquariumView.touchDownAt(normalizedX, normalizedY)
                InputFeedbacks.rippleSound()
            }
            MotionEvent.ACTION_MOVE -> aquariumView.moveTouchTo(normalizedX, normalizedY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> aquariumView.releaseTouch()
        }
    }

    companion object {
        const val Name = "Desktop"
        private const val DESKTOP_DECK_COLOR = 0xFF061827.toInt()
        private const val DESKTOP_OPERATION_WATER_HEIGHT_DP = 56
        // The full-height desktop window contributes this extra band to pointer travel. The six
        // physical-key rows remain at the proven 2026-08-27 size.
        private const val DESKTOP_TOUCHPAD_HEIGHT_DP = 272
        private const val DESKTOP_TOUCHPAD_MIN_HEIGHT_DP = 180
        private const val DESKTOP_ACTIVE_LANGUAGE_COLOR = 0xFF4285F4.toInt()
        private const val DESKTOP_KEY_TEXT_COLOR = 0xFFF4F8FC.toInt()
        // Warm destructive-key palette: visibly distinct from the blue-gray alphanumeric keys,
        // while remaining dark enough for the shared white legend and aquarium background.
        private const val DESKTOP_BACKSPACE_TOP_COLOR = 0xB06E3540.toInt()
        private const val DESKTOP_BACKSPACE_BOTTOM_COLOR = 0x98431E2A.toInt()
        private const val DESKTOP_BACKSPACE_STROKE_COLOR = 0xFFE17882.toInt()

        // These keys model a physical desktop keyboard and must leave the IME as standard
        // Android KeyEvents. Marking them Virtual makes the service's text-oriented branch
        // consume keys without Unicode (Esc/F-keys/Caps/Tab/Up/Down) before a remote host can
        // receive them. Keep this list local to DesktopKeyboard so ordinary layouts retain their
        // composition-aware virtual-key behaviour.
        private val RawDesktopControlKeySyms = buildSet {
            add(FcitxKeyMapping.FcitxKey_Escape)
            addAll(FcitxKeyMapping.FcitxKey_F1..FcitxKeyMapping.FcitxKey_F12)
            add(FcitxKeyMapping.FcitxKey_BackSpace)
            add(FcitxKeyMapping.FcitxKey_Tab)
            add(FcitxKeyMapping.FcitxKey_Caps_Lock)
            add(FcitxKeyMapping.FcitxKey_Return)
            add(FcitxKeyMapping.FcitxKey_Left)
            add(FcitxKeyMapping.FcitxKey_Right)
            add(FcitxKeyMapping.FcitxKey_Up)
            add(FcitxKeyMapping.FcitxKey_Down)
        }

        private val ShortcutModifiers = setOf(KeyState.Ctrl, KeyState.Alt, KeyState.Meta)

        private fun Context.dp(value: Int) =
            (value * resources.displayMetrics.density).roundToInt()

        // Preedit is layered over this transparent composition area.
        private fun createHeader(context: Context) = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        private fun createAquariumTransitionOverlay(context: Context) = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
            isFocusable = false
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
                textSize = 18f,
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
                textSize = 17f,
                percentWidth = width,
                variant = KeyDef.Appearance.Variant.Normal,
                border = KeyDef.Appearance.Border.On
            ),
            setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(primary)))
        )

        private fun languageKey(width: Float) = KeyDef(
            KeyDef.Appearance.Text(
                displayText = "中/英",
                textSize = 15f,
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
                    repeat = true,
                    viewId = R.id.button_backspace,
                    soundEffect = InputFeedbacks.SoundEffect.Delete
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
            // Row 3: Enter lives in the large operation row. Redistribute its former width
            // proportionally across Caps, A-L and punctuation so this row has no dead gap.
            listOf(
                DesktopSymKey("Caps", FcitxKeyMapping.FcitxKey_Caps_Lock, 1.8f / 12.8f),
                *"ASDFGHJKL".map { characterKey(it.toString(), 1f / 12.8f) }.toTypedArray(),
                shiftedSymbolKey(";", ":", 1f / 12.8f),
                shiftedSymbolKey("'", "\"", 1f / 12.8f)
            ),
            // Row 4: Shift Z-M , . / ↑ Shift  (weightSum=15). Keep Up full-height instead of
            // squeezing it into half of the bottom row; this begins a familiar inverted-T pad.
            listOf(
                DesktopModifierKey("Shift", 2.2f / 15f),
                *"ZXCVBNM".map { characterKey(it.toString(), 1f / 15f) }.toTypedArray(),
                shiftedSymbolKey(",", "<", 1f / 15f),
                shiftedSymbolKey(".", ">", 1f / 15f),
                // The 1.26/1.40/1.14 split puts ↑ on the exact horizontal center of ↓ in the
                // 18.3-unit bottom row while keeping both Slash and right Shift easy to hit.
                shiftedSymbolKey("/", "?", 1.26f / 15f),
                DesktopSymKey("↑", FcitxKeyMapping.FcitxKey_Up, 1.4f / 15f, repeat = true),
                DesktopModifierKey("Shift", 1.14f / 15f)
            ),
            // Row 5: Ctrl Alt 中/英 ──SPACE── ⌘ ← ↓ →. Each arrow is a full-height 1.5-unit
            // target; the space bar remains the largest key while no desktop control is removed.
            listOf(
                DesktopModifierKey("Ctrl", 2.2f / 18.3f),
                DesktopModifierKey("Alt", 1.6f / 18.3f),
                languageKey(1.6f / 18.3f),
                DesktopSpaceKey(6.8f / 18.3f),
                DesktopModifierKey("\u2318", 1.6f / 18.3f),
                DesktopSymKey("←", FcitxKeyMapping.FcitxKey_Left, 1.5f / 18.3f, repeat = true),
                DesktopSymKey("↓", FcitxKeyMapping.FcitxKey_Down, 1.5f / 18.3f, repeat = true),
                DesktopSymKey("→", FcitxKeyMapping.FcitxKey_Right, 1.5f / 18.3f, repeat = true)
            )
        )

        // Cross-application conventional shortcuts. The target application remains the authority:
        // these labels preview the real modifier+key event that KBoard sends, not an app command.
        private val CtrlShortcutHints = mapOf(
            "A" to "全选", "B" to "粗体", "C" to "复制", "D" to "收藏",
            "F" to "查找", "G" to "下一个", "H" to "替换", "I" to "斜体",
            "K" to "插入链接", "L" to "地址栏", "N" to "新建文档", "O" to "打开",
            "P" to "打印", "R" to "刷新", "S" to "保存", "T" to "新标签",
            "U" to "下划线", "V" to "粘贴", "W" to "关闭", "X" to "剪切",
            "Y" to "重做", "Z" to "撤销", "Tab" to "下一标签",
            "Backspace" to "删除整词", "←" to "上一词", "→" to "下一词",
            "↑" to "段落开头", "↓" to "段落结尾", " " to "切换中英",
            "-" to "缩小", "=" to "放大", "0" to "重置缩放"
        )

        private val CtrlShiftShortcutHints = CtrlShortcutHints + mapOf(
            "T" to "恢复标签", "N" to "无痕窗口", "V" to "纯文本粘贴",
            "S" to "另存为", "Z" to "重做", "Tab" to "上一标签",
            "←" to "选到词首", "→" to "选到词尾",
            "↑" to "选到段首", "↓" to "选到段尾"
        )

        private val AltShortcutHints = mapOf(
            "Tab" to "切换窗口", "F4" to "关闭窗口", "Enter" to "属性",
            "←" to "后退", "→" to "前进", "↑" to "上一级",
            "↓" to "展开菜单", " " to "窗口菜单"
        )

        private val CmdShortcutHints = mapOf(
            "A" to "全选", "B" to "粗体", "C" to "复制", "F" to "查找",
            "H" to "隐藏", "I" to "斜体", "K" to "插入链接", "L" to "地址栏",
            "M" to "最小化", "N" to "新建文档", "O" to "打开", "P" to "打印",
            "Q" to "退出", "R" to "刷新", "S" to "保存", "T" to "新标签",
            "U" to "下划线", "V" to "粘贴", "W" to "关闭", "X" to "剪切",
            "Z" to "撤销", "Tab" to "切换应用", " " to "系统搜索",
            "←" to "行首", "→" to "行尾", "↑" to "文首", "↓" to "文尾",
            "-" to "缩小", "=" to "放大", "0" to "重置缩放"
        )

        private val CmdShiftShortcutHints = CmdShortcutHints + mapOf(
            "3" to "全屏截图", "4" to "区域截图", "5" to "截图工具",
            "T" to "恢复标签", "N" to "新建文件夹", "S" to "另存为",
            "Z" to "重做", "Tab" to "反向切换",
            "←" to "选到行首", "→" to "选到行尾",
            "↑" to "选到文首", "↓" to "选到文尾"
        )

        private val ShiftShortcutHints = mapOf(
            "Tab" to "反向切换", "Enter" to "换行", "F10" to "右键菜单"
        )
    }

    // ── Runtime state ──
    private val modifierStates = linkedSetOf<KeyState>()
    private val heldModifierKeys = linkedMapOf<TextKeyView, KeyState>()
    private val textKeys by lazy { allViews.filterIsInstance<TextKeyView>() }
    private var currentImeName: String = ""
    private var currentImeLanguageCode: String = ""

    fun sendEnterFromOperationBar() {
        onAction(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Return)))
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        // Ctrl+Space → language switch
        if (action is KeyAction.SymAction &&
            action.sym == KeySym(FcitxKeyMapping.FcitxKey_space) &&
            KeyState.Ctrl in modifierStates
        ) {
            super.onAction(KeyAction.LangSwitchAction, source)
            return
        }

        val isRawDesktopControlKey = action is KeyAction.SymAction &&
                action.sym.sym in RawDesktopControlKeySyms
        val states = if (
            modifierStates.any { it in ShortcutModifiers } || isRawDesktopControlKey
        ) {
            KeyStates(*modifierStates.toTypedArray())
        } else {
            KeyStates(*(modifierStates + KeyState.Virtual).toTypedArray())
        }
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
    }

    override fun onAttach() {
        super.onAttach()
        releaseHeldModifierKeys()
        heldModifierKeys.clear()
        modifierStates.clear()
        updateModifierKeys()
        updateShortcutHints()
        updateLetterKeys()
        updateSpaceLanguageLabel()
        aquariumView.activate()
        InputFeedbacks.prepareRippleSoundAsync()
    }

    override fun onDetach() {
        // A layout/window transition can happen while a finger is still down. Forward every
        // outstanding UP before clearing the visuals so the remote host cannot retain Ctrl/⌘.
        releaseHeldModifierKeys()
        heldModifierKeys.clear()
        modifierStates.clear()
        updateModifierKeys()
        updateShortcutHints()
        touchpadView.releaseInputState()
        aquariumView.deactivate()
        super.onDetach()
    }

    override fun dispose() {
        touchpadView.dispose()
        super.dispose()
    }

    fun onImeWindowShown() {
        aquariumView.activate()
    }

    fun onImeWindowHidden() {
        aquariumView.deactivate()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        currentImeName = ime.uniqueName
        currentImeLanguageCode = ime.languageCode
        updateSpaceLanguageLabel()
    }

    private fun updateSpaceLanguageLabel() {
        val chineseActive = currentImeLanguageCode.startsWith("zh", ignoreCase = true) ||
            currentImeName.contains("pinyin", ignoreCase = true) ||
            currentImeName.contains("chinese", ignoreCase = true) ||
            currentImeName.contains("shuangpin", ignoreCase = true) ||
            currentImeName.contains("wubi", ignoreCase = true) ||
            currentImeName.contains("cangjie", ignoreCase = true) ||
            currentImeName.contains("zh", ignoreCase = true)
        val langLabel = if (chineseActive) "拼 音" else "English"
        findViewById<View>(R.id.button_space)?.let { space ->
            (space as? TextKeyView)?.mainText?.setLayoutStableText(langLabel)
        }
        textKeys.firstOrNull { key ->
            (key.def as? KeyDef.Appearance.Text)?.displayText == "中/英"
        }?.mainText?.let { languageText ->
            val label = if (chineseActive) "中/英" else "英/中"
            languageText.setLayoutStableText(
                label,
                intArrayOf(
                    DESKTOP_ACTIVE_LANGUAGE_COLOR,
                    DESKTOP_KEY_TEXT_COLOR,
                    DESKTOP_KEY_TEXT_COLOR
                )
            )
        }
    }

    private fun updateModifierKeys() {
        textKeys.forEach { key ->
            val label = (key.def as? KeyDef.Appearance.Text)?.displayText ?: return@forEach
            val state = when (label) {
                "Ctrl" -> KeyState.Ctrl
                "Alt" -> KeyState.Alt
                "\u2318" -> KeyState.Meta   // ⌘
                "Shift" -> KeyState.Shift
                else -> return@forEach
            }
            key.isSelected = state in modifierStates
        }
    }

    private fun configureHeldModifierKeys() {
        // This is called from the constructor init block. Do not access the textKeys lazy
        // delegate here: Kotlin initializes that property later in source order.
        allViews.filterIsInstance<TextKeyView>().forEach { key ->
            val state = modifierStateFor(key) ?: return@forEach
            key.onGestureListener = OnGestureListener { _, event ->
                when (event.type) {
                    GestureType.Down -> {
                        if (key !in heldModifierKeys) {
                            val stateAlreadyHeld = state in heldModifierKeys.values
                            heldModifierKeys[key] = state
                            // The layout contains two Shift keys. The second physical touch must
                            // not create a duplicate Android modifier DOWN.
                            if (!stateAlreadyHeld) {
                                onAction(KeyAction.ModifierStateAction(state, down = true))
                            }
                        }
                    }
                    GestureType.Up -> {
                        val removed = heldModifierKeys.remove(key)
                        // Keep Shift down until the final held Shift key is released. ACTION_CANCEL
                        // is normalized to Up by CustomGestureView and follows this same path.
                        if (removed != null && state !in heldModifierKeys.values) {
                            onAction(KeyAction.ModifierStateAction(state, down = false))
                        }
                    }
                    GestureType.Move -> return@OnGestureListener false
                }
                modifierStates.clear()
                modifierStates.addAll(heldModifierKeys.values)
                updateModifierKeys()
                updateShortcutHints()
                // Modifier observation must not consume the touch; CustomGestureView still owns
                // pressed visuals, sound and multi-pointer dispatch.
                false
            }
        }
    }

    private fun releaseHeldModifierKeys() {
        heldModifierKeys.values.toSet().forEach { state ->
            onAction(KeyAction.ModifierStateAction(state, down = false))
        }
    }

    private fun modifierStateFor(key: TextKeyView): KeyState? =
        when ((key.def as? KeyDef.Appearance.Text)?.displayText) {
            "Ctrl" -> KeyState.Ctrl
            "Alt" -> KeyState.Alt
            "\u2318" -> KeyState.Meta
            "Shift" -> KeyState.Shift
            else -> null
        }

    private fun updateShortcutHints() {
        val hints = when {
            KeyState.Ctrl in modifierStates && KeyState.Shift in modifierStates ->
                CtrlShiftShortcutHints
            KeyState.Meta in modifierStates && KeyState.Shift in modifierStates ->
                CmdShiftShortcutHints
            KeyState.Ctrl in modifierStates -> CtrlShortcutHints
            KeyState.Meta in modifierStates -> CmdShortcutHints
            KeyState.Alt in modifierStates -> AltShortcutHints
            KeyState.Shift in modifierStates -> ShiftShortcutHints
            else -> emptyMap()
        }
        textKeys.forEach { key ->
            val label = (key.def as? KeyDef.Appearance.Text)?.displayText.orEmpty()
            // The desktop space reserves "English" as stable measure text, but the shortcut
            // table deliberately uses a single blank as the semantic space-key identifier.
            val semanticLabel = if (key.id == R.id.button_space) " " else label
            key.setShortcutHint(hints[semanticLabel])
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
        if (!isLaidOut) return null
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1] + compositionHeader.bottom
    }

    fun operationButtonCentersOnScreen(): Pair<Int, Int>? {
        if (!isLaidOut) return null
        val option = textKeys.firstOrNull { key ->
            (key.def as? KeyDef.Appearance.Text)?.displayText == "Alt"
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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // ConstraintLayout respects the key-row bottom padding, but the pond must cover it.
        // GLSurfaceView uses an independent compositor surface on this Android 12 build. Keep
        // that surface physically below the mouse header so it cannot cover the touchpad Canvas.
        val pondTop = compositionHeader.bottom.coerceIn(0, bottom - top)
        aquariumView.layout(0, pondTop, right - left, bottom - top)
        aquariumTransitionOverlay.layout(0, pondTop, right - left, bottom - top)
        // The aquarium and its transition overlay are inserted at indices 0 and 1, so the
        // existing header and key rows already stay above them. Calling bringToFront() from
        // onLayout() asks ConstraintLayout for another layout while it is still laying out,
        // producing a permanent requestLayout loop on the Android 12 dual-screen build.
    }

}
