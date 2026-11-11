/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.clipboard.ClipboardWindow
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val taskGate = KeyboardWindowTaskGate()
    private val service by manager.inputMethodService()
    private val inputView by manager.inputView()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key {}

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between picker
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            // disable animation switching between picker
            nextWindow !is PickerWindow
        }

    private lateinit var keyboardView: FrameLayout

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to TextKeyboard(context, theme),
            TextKeyboard.FloatingName to TextKeyboard(
                context,
                theme,
                TextKeyboard.FloatingLayout,
                alwaysShowLanguageKey = true
            ),
            // The desktop layout is part of the same keyboard surface. Reusing the active
            // theme keeps candidates, toolbar and keys in one coherent palette.
            DesktopKeyboard.Name to DesktopKeyboard(context, theme),
            MinimalKeyboard.Name to MinimalKeyboard(
                context,
                theme,
                onClipboard = { text -> service.commitTextFrom(inputView.overlayRequestId, text) },
                onReturnToPrevious = {
                    floatingMode = false
                    AppPrefs.getInstance().keyboard.floatingKeyboard.setValue(false)
                    rememberPresentationMode(KeyboardPresentationMode.Normal)
                    switchLayout(TextKeyboard.Name, remember = false)
                },
                onHide = service::requestHideSelfAfterTouch,
                onDrag = inputView::onMinimalKeyboardDrag
            ),
            NumberKeyboard.Name to NumberKeyboard(context, theme)
        )
    }
    private var currentKeyboardName = ""
    private val desktopModeState = DesktopKeyboardModeState()
    private var presentationMode = KeyboardPresentationMode.decode(
        AppPrefs.getInstance().internal.lastKeyboardPresentationMode.getValue()
    )
    private var floatingMode = presentationMode == KeyboardPresentationMode.Floating
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private val keyActionListener = KeyActionListener { it, source ->
        if (it is KeyAction.LayoutSwitchAction) {
            switchLayout(it.act)
        } else {
            commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    // This will be called EXACTLY ONCE
    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        // Android may recreate InputView between two show requests while the IME process stays
        // alive. Build the first drawable frame from the user's retained desktop-mode choice;
        // attaching TextKeyboard here and switching asynchronously in onStartInput caused a
        // visible one-frame flash of the ordinary keyboard in remote-desktop clients.
        attachLayout(presentationMode.layoutName)
        return keyboardView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.onInputMethodUpdate(fcitx.runImmediately { inputMethodEntryCached })
        }
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        val target = when (val requested = to.ifEmpty { lastSymbolType }) {
            TextKeyboard.Name if (floatingMode) -> TextKeyboard.FloatingName
            else -> requested
        }
        val generation = taskGate.captureGeneration()
        ContextCompat.getMainExecutor(service).execute {
            if (!taskGate.canRun(generation)) {
                Timber.d("Drop layout switch for retired KeyboardWindow: target=$target")
                return@execute
            }
            if (keyboards.containsKey(target)) {
                if (remember && target != TextKeyboard.Name) {
                    lastSymbolType = target
                }
                if (target == currentKeyboardName) {
                    // A recreated/rebound bar may still carry desktop-only visibility even when
                    // the keyboard layout name is already correct. Reapply the actual mode before
                    // treating a same-layout request as a no-op.
                    notifyBarLayoutChanged()
                    return@execute
                }
                detachCurrentLayout()
                attachLayout(target)
                // Synchronize ordinary/desktop visual state as part of the layout transaction.
                // Waiting for WindowManager.isAttached() leaves a race during IME rebinds and
                // cross-display moves: TextKeyboard is already visible, but KawaiiBar can retain
                // desktop quiet mode and hide the ordinary toolbar and its switch controls.
                // onAttached() deliberately repeats this idempotent synchronization after a
                // window reattach, but it must never be the only restoration path.
                notifyBarLayoutChanged()
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        val targetLayout = when (presentationMode) {
            KeyboardPresentationMode.Desktop -> DesktopKeyboard.Name
            KeyboardPresentationMode.Minimal -> MinimalKeyboard.Name
            else -> {
            when (info.inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
                InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
                else -> if (floatingMode) TextKeyboard.FloatingName else TextKeyboard.Name
            }
            }
        }
        switchLayout(targetLayout, remember = false)
    }

    fun setFloatingMode(enabled: Boolean) {
        if (floatingMode == enabled) return
        floatingMode = enabled
        if (currentKeyboardName == TextKeyboard.Name || currentKeyboardName == TextKeyboard.FloatingName) {
            switchLayout(if (enabled) TextKeyboard.FloatingName else TextKeyboard.Name, remember = false)
        }
    }

    fun showDesktopKeyboard() {
        rememberPresentationMode(KeyboardPresentationMode.Desktop)
        switchLayout(DesktopKeyboard.Name, remember = false)
    }

    fun showMinimalKeyboard() {
        if (currentKeyboardName == MinimalKeyboard.Name) return
        AppPrefs.getInstance().internal.previousKeyboardPresentationMode.setValue(
            presentationMode.persistedValue
        )
        AppPrefs.getInstance().internal.previousKeyboardLayoutName.setValue(currentKeyboardName)
        rememberPresentationMode(KeyboardPresentationMode.Minimal)
        switchLayout(MinimalKeyboard.Name, remember = false)
    }

    private fun restorePreviousKeyboard() {
        val restored = KeyboardPresentationMode.decodePrevious(
            AppPrefs.getInstance().internal.previousKeyboardPresentationMode.getValue()
        )
        floatingMode = restored == KeyboardPresentationMode.Floating
        when (restored) {
            KeyboardPresentationMode.Floating ->
                AppPrefs.getInstance().keyboard.floatingKeyboard.setValue(true)
            KeyboardPresentationMode.Normal ->
                AppPrefs.getInstance().keyboard.floatingKeyboard.setValue(false)
            KeyboardPresentationMode.Minimal,
            KeyboardPresentationMode.Desktop -> Unit
        }
        rememberPresentationMode(restored)
        val previousLayout = AppPrefs.getInstance().internal.previousKeyboardLayoutName.getValue()
        val targetLayout = when (restored) {
            KeyboardPresentationMode.Normal ->
                if (previousLayout == NumberKeyboard.Name) NumberKeyboard.Name else TextKeyboard.Name
            else -> restored.layoutName
        }
        switchLayout(targetLayout, remember = false)
    }

    internal fun overlayLayoutName(): String = currentKeyboardName

    internal fun restoreOverlayLayout(name: String?) {
        val restored = when (name) {
            DesktopKeyboard.Name -> KeyboardPresentationMode.Desktop
            MinimalKeyboard.Name -> KeyboardPresentationMode.Minimal
            TextKeyboard.FloatingName -> KeyboardPresentationMode.Floating
            TextKeyboard.Name -> KeyboardPresentationMode.Normal
            else -> KeyboardPresentationMode.decode(
                AppPrefs.getInstance().internal.lastKeyboardPresentationMode.getValue()
            )
        }
        presentationMode = restored
        floatingMode = restored == KeyboardPresentationMode.Floating
        when (restored) {
            KeyboardPresentationMode.Floating ->
                AppPrefs.getInstance().keyboard.floatingKeyboard.setValue(true)
            KeyboardPresentationMode.Normal ->
                AppPrefs.getInstance().keyboard.floatingKeyboard.setValue(false)
            KeyboardPresentationMode.Minimal,
            KeyboardPresentationMode.Desktop -> Unit
        }
        switchLayout(restored.layoutName, remember = false)
    }

    fun toggleDesktopKeyboard() {
        val target = if (currentKeyboardName == DesktopKeyboard.Name) {
            AppPrefs.getInstance().keyboard.floatingKeyboard.setValue(false)
            rememberPresentationMode(KeyboardPresentationMode.Normal)
            TextKeyboard.Name
        } else {
            rememberPresentationMode(KeyboardPresentationMode.Desktop)
            DesktopKeyboard.Name
        }
        switchLayout(target, remember = false)
    }

    fun selectFloatingKeyboard(enabled: Boolean) {
        val mode = if (enabled) KeyboardPresentationMode.Floating else KeyboardPresentationMode.Normal
        AppPrefs.getInstance().keyboard.floatingKeyboard.setValue(enabled)
        rememberPresentationMode(mode)
        floatingMode = enabled
        switchLayout(mode.layoutName, remember = false)
    }

    private fun rememberPresentationMode(mode: KeyboardPresentationMode) {
        presentationMode = mode
        AppPrefs.getInstance().internal.lastKeyboardPresentationMode.setValue(mode.persistedValue)
    }

    fun desktopFirstRowTopOnScreen(): Int? =
        (currentKeyboard as? DesktopKeyboard)?.firstRowTopOnScreen()

    fun desktopOperationButtonCentersOnScreen(): Pair<Int, Int>? =
        (currentKeyboard as? DesktopKeyboard)?.operationButtonCentersOnScreen()

    fun setDesktopSystemBottomInset(bottomInsetPx: Int) {
        (currentKeyboard as? DesktopKeyboard)?.setSystemBottomInset(bottomInsetPx)
    }

    fun onDesktopPondTouch(event: MotionEvent): Boolean =
        (currentKeyboard as? DesktopKeyboard)?.onExternalPondTouch(event) ?: false

    fun sendDesktopEnter() {
        (currentKeyboard as? DesktopKeyboard)?.sendEnterFromOperationBar()
    }

    fun sendDesktopScreenSwitch() {
        (currentKeyboard as? DesktopKeyboard)?.sendScreenSwitchFromOperationBar()
    }

    fun onImeWindowShown() {
        (currentKeyboard as? DesktopKeyboard)?.onImeWindowShown()
    }

    fun onImeWindowHidden() {
        (currentKeyboard as? DesktopKeyboard)?.onImeWindowHidden()
    }

    /** Permanently release every keyboard created for this InputView generation. */
    fun dispose() {
        taskGate.retire()
        currentKeyboard?.onDetach()
        keyboards.values.forEach { it.dispose() }
        if (::keyboardView.isInitialized) keyboardView.removeAllViews()
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        currentKeyboard?.onInputMethodUpdate(ime)
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
        }
        notifyBarLayoutChanged()
    }

    override fun onDetached() {
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    // Call this when
    // 1) the keyboard window was newly attached
    // 2) currently keyboard window is attached and switchLayout was used
    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(currentKeyboardName == NumberKeyboard.Name)
        val nextDesktopMode = currentKeyboardName == DesktopKeyboard.Name
        desktopModeState.synchronize(nextDesktopMode) {
            // The InputView/candidate surface may have been recreated while the logical keyboard
            // mode stayed the same, so mode styling must be synchronized on every attachment.
            inputView.setDesktopKeyboardMode(it)
        }
        val minimalKeyboard = currentKeyboard as? MinimalKeyboard
        inputView.setMinimalKeyboardMode(
            enabled = minimalKeyboard != null,
            voiceButton = minimalKeyboard?.voiceButton
        )
    }

    fun showMinimalVoiceTranscript(text: CharSequence?) {
        (currentKeyboard as? MinimalKeyboard)?.showVoiceTranscript(text)
    }
}
