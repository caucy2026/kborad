/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.hardware.display.DisplayManager
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.core.ScancodeMapping
import org.fcitx.fcitx5.android.core.SubtypeManager
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.cursor.CursorRange
import org.fcitx.fcitx5.android.input.cursor.CursorTracker
import org.fcitx.fcitx5.android.input.keyboard.RemoteMouseInputProtocol
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.forceShowSelf
import org.fcitx.fcitx5.android.utils.inputMethodManager
import org.fcitx.fcitx5.android.utils.isTypeNull
import org.fcitx.fcitx5.android.utils.monitorCursorAnchor
import org.fcitx.fcitx5.android.utils.styledFloat
import org.fcitx.fcitx5.android.utils.withBatchEdit
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.resources.styledColor
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.math.max

class FcitxInputMethodService : LifecycleInputMethodService() {

    private lateinit var fcitx: FcitxConnection
    private val fcitxClientName =
        "${javaClass.name}@${System.identityHashCode(this)}"

    private var jobs = Channel<Job>(capacity = Channel.UNLIMITED)

    private val cachedKeyEvents = LruCache<Int, KeyEvent>(78)
    private var cachedKeyEventIndex = 0

    private val hardwareKeyAnomalyFilter = HardwareKeyAnomalyFilter()

    /**
     * Saves MetaState produced by hardware keyboard with "sticky" modifier keys, to clear them in order.
     * See also [InputConnection#clearMetaKeyStates(int)](https://developer.android.com/reference/android/view/inputmethod/InputConnection#clearMetaKeyStates(int))
     */
    private var lastMetaState: Int = 0

    /** Modifier DOWN events already forwarded to the current remote InputConnection. */
    private val pressedDesktopModifiers = linkedMapOf<KeyState, Long>()

    /** Mouse button DOWN events accepted by the current remote InputConnection. */
    private val pressedDesktopMouseButtons = linkedSetOf<String>()
    /** HOME/BACK DOWN events sent to the remote editor, paired with their original connection. */
    private val pressedDesktopRemoteNavigationKeys =
        linkedMapOf<Int, Pair<InputConnection, Long>>()

    /**
     * Bounded mouse-move handoff. Only one Binder call may be in flight; while it is blocked,
     * fresh deltas are accumulated into one capped slot instead of growing a main-thread queue.
     */
    private val desktopMouseMoveSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val desktopMouseMoveLock = Any()
    private var pendingDesktopMouseDx = 0
    private var pendingDesktopMouseDy = 0
    private var pendingDesktopMouseConnection: InputConnection? = null
    private val systemMouseInjector by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SystemMouseInjector(applicationContext)
    }

    private var pendingTouchHideRequest: Runnable? = null
    private var pendingTouchHideHost: View? = null
    private var pendingTouchHideSource: View? = null

    private lateinit var pkgNameCache: PackageNameCache

    private var decorViewRef: View? = null
    private val decorView: View
        get() = checkNotNull(decorViewRef)
    private var contentViewRef: FrameLayout? = null
    private val contentView: FrameLayout
        get() = checkNotNull(contentViewRef)
    private var inputViewHost: FrameLayout? = null
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null
    private var inputViewGeneration = 0
    private var ownedResourcesReleased = false

    private val navbarMgr = NavigationBarManager()
    private val inputDeviceMgr = InputDeviceManager { isVirtualKeyboard ->
        postFcitxJob {
            setCandidatePagingMode(if (isVirtualKeyboard) 0 else 1)
        }
        currentInputConnection?.monitorCursorAnchor(!isVirtualKeyboard)
        if (isVirtualKeyboard) {
            hideStatusIcon()
        } else {
            showStatusIcon(StatusIconMapping.fromEntry(fcitx.runImmediately { inputMethodEntryCached }))
        }
        window.window?.let {
            navbarMgr.evaluate(it, isVirtualKeyboard)
        }
    }

    private var capabilityFlags = CapabilityFlags.DefaultFlags

    private val selection = CursorTracker()

    val currentInputSelection: CursorRange
        get() = selection.latest

    private val composing = CursorRange()
    private var composingText = FormattedText.Empty
    private var voiceComposingStart = -1

    private fun resetComposingState() {
        composing.clear()
        composingText = FormattedText.Empty
    }

    private var cursorUpdateIndex: Int = 0

    private var highlightColor: Int = 0x66008577 // material_deep_teal_500 with alpha 0.4

    private val prefs = AppPrefs.getInstance()
    private val inlineSuggestions by prefs.keyboard.inlineSuggestions
    private val ignoreSystemCursor by prefs.advanced.ignoreSystemCursor

    private val recreateInputViewPrefs: Array<ManagedPreference<*>> = arrayOf(
        prefs.keyboard.expandKeypressArea,
        prefs.advanced.disableAnimation,
        prefs.advanced.ignoreSystemWindowInsets,
    )

    private fun createInputView(theme: Theme): InputView {
        inputView?.dispose()
        inputViewHost?.removeAllViews()
        val newInputView = InputView(this, fcitx, theme)
        val newHost = FrameLayout(this).apply {
            addView(
                newInputView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        inputDeviceMgr.setInputView(newInputView)
        inputViewHost = newHost
        inputView = newInputView
        inputViewGeneration += 1
        Log.i(IME_LIFECYCLE_TAG, "created input view generation=$inputViewGeneration")
        return newInputView
    }

    private fun replaceInputView(theme: Theme): InputView {
        val newInputView = createInputView(theme)
        setInputView(checkNotNull(inputViewHost))
        return newInputView
    }

    private fun replaceCandidateView(theme: Theme): CandidatesView {
        val newCandidatesView = CandidatesView(this, fcitx, theme)
        // replace CandidatesView manually
        contentView.removeView(candidatesView)
        // put CandidatesView directly under content view
        contentView.addView(newCandidatesView)
        inputDeviceMgr.setCandidatesView(newCandidatesView)
        candidatesView = newCandidatesView
        return newCandidatesView
    }

    private fun replaceInputViews(theme: Theme) {
        navbarMgr.evaluate(window.window!!, inputDeviceMgr.isVirtualKeyboard)
        replaceInputView(theme)
        replaceCandidateView(theme)
    }

    @Keep
    private val recreateInputViewListener = ManagedPreference.OnChangeListener<Any> { _, _ ->
        replaceInputView(ThemeManager.activeTheme)
    }

    @Keep
    private val recreateCandidatesViewListener = ManagedPreferenceProvider.OnChangeListener {
        replaceCandidateView(ThemeManager.activeTheme)
    }

    @Keep
    private val onThemeChangeListener = ThemeManager.OnThemeChangeListener {
        replaceInputViews(it)
    }

    /**
     * Post a fcitx operation to [jobs] to be executed
     *
     * Unlike `fcitx.runOnReady` or `fcitx.launchOnReady` where
     * subsequent operations can start if the prior operation is not finished (suspended),
     * [postFcitxJob] ensures that operations are executed sequentially.
     */
    fun postFcitxJob(block: suspend FcitxAPI.() -> Unit): Job {
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                fcitx.runOnReady(block)
            } catch (_: FcitxDaemon.DisconnectedException) {
                // A queued operation can lose ownership when Android 12 replaces this service
                // before the queue reaches it. It belongs to the retired generation and must not
                // be forwarded to the replacement connection or treated as an application crash.
                Timber.d("Drop queued fcitx operation from retired IME generation")
            }
        }
        if (ownedResourcesReleased || jobs.trySend(job).isFailure) {
            job.cancel()
        }
        return job
    }

    override fun onCreate() {
        // Initialize InputMethodService and its window before connecting the native daemon.
        // This minimizes the interval in which a vendor IME callback can observe partial state.
        super.onCreate()
        claimProcessImeInstance()
        fcitx = FcitxDaemon.connect(fcitxClientName)
        lifecycleScope.launch {
            jobs.consumeEach {
                // Be explicit: join() currently starts a LAZY coroutine, but relying on that
                // subtle contract makes the ownership/serialization guarantee easy to break.
                it.start()
                it.join()
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            desktopMouseMoveSignal.consumeEach {
                dispatchPendingDesktopMouseMove()
            }
        }
        lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
        pkgNameCache = PackageNameCache(this)
        recreateInputViewPrefs.forEach {
            it.registerOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            postFcitxJob {
                SubtypeManager.syncWith(enabledIme())
            }
        }
        decorViewRef = window.window!!.decorView
        contentViewRef = decorView.findViewById(android.R.id.content)
        lastKnownConfig = resources.configuration
        Log.i(IME_LIFECYCLE_TAG, "created service instance=${System.identityHashCode(this)}")
    }

    /**
     * Android 12 dual-display firmware can construct the next InputMethodService instance before
     * delivering onDestroy() to the previous one. Retire that stale in-process owner explicitly,
     * otherwise every remote keyboard show leaves a full keyboard tree and lifecycle collectors.
     */
    private fun claimProcessImeInstance() {
        val previous = synchronized(PROCESS_IME_INSTANCE_LOCK) {
            val old = processImeInstance?.get()
            processImeInstance = WeakReference(this)
            old
        }
        if (previous != null && previous !== this) {
            previous.releaseOwnedResources("superseded")
        }
    }

    private fun releaseOwnedResources(reason: String) {
        if (ownedResourcesReleased) return
        retireImeWindow()
        ownedResourcesReleased = true
        Log.i(
            IME_LIFECYCLE_TAG,
            "release service instance=${System.identityHashCode(this)} reason=$reason"
        )
        cancelPendingTouchHideRequest()
        releaseDesktopInputStates()
        clearPendingDesktopMouseMove()
        desktopMouseMoveSignal.close()
        hardwareKeyAnomalyFilter.reset()
        cachedKeyEvents.evictAll()
        showingDialog?.dismiss()
        showingDialog = null
        inputView?.dispose()
        inputViewHost?.removeAllViews()
        candidatesView?.handleEvents = false
        contentViewRef?.let { oldContentView ->
            try {
                // Replace the private framework mInputView as well as our own reference so an
                // obsolete IME window cannot retain the heavyweight hierarchy.
                super.setInputView(View(applicationContext))
            } catch (error: RuntimeException) {
                Log.w(IME_LIFECYCLE_TAG, "failed to detach superseded input view", error)
            }
            oldContentView.removeView(candidatesView)
        }
        inputView = null
        inputViewHost = null
        candidatesView = null
        inputDeviceMgr.clearViews()
        jobs.close()
        lifecycleScope.coroutineContext.cancelChildren()
        recreateInputViewPrefs.forEach {
            it.unregisterOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.unregisterOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        FcitxDaemon.disconnect(fcitxClientName)
    }

    private fun handleFcitxEvent(event: FcitxEvent<*>) {
        when (event) {
            is FcitxEvent.CommitStringEvent -> {
                commitText(event.data.text, event.data.cursor)
            }
            is FcitxEvent.KeyEvent -> event.data.let event@{
                if (it.states.virtual) {
                    // KeyEvent from virtual keyboard
                    when (it.sym.sym) {
                        FcitxKeyMapping.FcitxKey_BackSpace -> handleBackspaceKey()
                        FcitxKeyMapping.FcitxKey_Return -> handleReturnKey()
                        FcitxKeyMapping.FcitxKey_Left -> handleArrowKey(KeyEvent.KEYCODE_DPAD_LEFT)
                        FcitxKeyMapping.FcitxKey_Right -> handleArrowKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                        else -> if (it.unicode > 0) {
                            commitText(Character.toString(it.unicode))
                        } else {
                            Timber.w("Unhandled Virtual KeyEvent: $it")
                        }
                    }
                } else {
                    // KeyEvent from physical keyboard (or input method engine forwardKey)
                    // use cached event if available
                    cachedKeyEvents.remove(it.timestamp)?.let { keyEvent ->
                        /**
                         * intercept the KeyEvent which would cause the default [android.text.method.QwertyKeyListener]
                         * to show a Gingerbread-style CharacterPickerDialog
                         */
                        if (keyEvent.unicodeChar == KeyCharacterMap.PICKER_DIALOG_INPUT.code) {
                            currentInputConnection?.sendKeyEvent(
                                KeyEvent(
                                    keyEvent.downTime, keyEvent.eventTime,
                                    keyEvent.action, keyEvent.keyCode,
                                    keyEvent.repeatCount, keyEvent.metaState, -1,
                                    keyEvent.scanCode, keyEvent.flags, keyEvent.source
                                )
                            )
                            return@event
                        }
                        currentInputConnection?.sendKeyEvent(keyEvent)
                        if (KeyEvent.isModifierKey(keyEvent.keyCode)) {
                            when (keyEvent.action) {
                                KeyEvent.ACTION_DOWN -> {
                                    // save current metaState when modifier key down
                                    lastMetaState = keyEvent.metaState
                                }
                                KeyEvent.ACTION_UP -> {
                                    // only clear metaState that would be missing when this modifier key up
                                    currentInputConnection?.clearMetaKeyStates(lastMetaState xor keyEvent.metaState)
                                    lastMetaState = keyEvent.metaState
                                }
                            }
                        }
                        return@event
                    }
                    // simulate key event
                    val keyCode = it.sym.keyCode
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        // recognized keyCode
                        val eventTime = SystemClock.uptimeMillis()
                        if (it.up) {
                            sendUpKeyEvent(eventTime, keyCode, it.states.metaState)
                        } else {
                            sendDownKeyEvent(eventTime, keyCode, it.states.metaState)
                        }
                    } else {
                        // no matching keyCode, commit character once on key down
                        if (!it.up && it.unicode > 0) {
                            commitText(Character.toString(it.unicode))
                        } else {
                            Timber.w("Unhandled Fcitx KeyEvent: $it")
                        }
                    }
                }
            }
            is FcitxEvent.ClientPreeditEvent -> {
                updateComposingText(event.data)
            }
            is FcitxEvent.DeleteSurroundingEvent -> {
                val (before, after) = event.data
                handleDeleteSurrounding(before, after)
            }
            is FcitxEvent.IMChangeEvent -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val im = event.data.uniqueName
                    val subtype = SubtypeManager.subtypeOf(im) ?: return
                    skipNextSubtypeChange = im
                    // [^1]: notify system that input method subtype has changed
                    switchInputMethod(InputMethodUtil.componentName, subtype)
                }
                if (inputDeviceMgr.evaluateOnInputMethodActivate()) {
                    showStatusIcon(StatusIconMapping.fromEntry(event.data))
                }
            }
            is FcitxEvent.SwitchInputMethodEvent -> {
                val (reason) = event.data
                if (reason != FcitxEvent.SwitchInputMethodEvent.Reason.CapabilityChanged &&
                    reason != FcitxEvent.SwitchInputMethodEvent.Reason.Other
                ) {
                    if (inputDeviceMgr.evaluateOnInputMethodSwitch()) {
                        // show inputView for [CandidatesView] when input method switched by user
                        forceShowSelf()
                    }
                }
            }
            else -> {}
        }
    }

    private fun handleDeleteSurrounding(before: Int, after: Int) {
        val ic = currentInputConnection ?: return
        if (before > 0) {
            selection.predictOffset(-before)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(before, after)
        } else {
            ic.deleteSurroundingText(before, after)
        }
    }

    private fun handleBackspaceKey() {
        val lastSelection = selection.latest
        if (lastSelection.isNotEmpty()) {
            selection.predict(lastSelection.start)
        } else if (lastSelection.start > 0) {
            selection.predictOffset(-1)
        }
        // In practice nobody (apart from ourselves) would set `privateImeOptions` to our
        // `DeleteSurroundingFlag`, leading to a behavior of simulating backspace key pressing
        // in almost every EditText.
        if (currentInputEditorInfo.privateImeOptions != DeleteSurroundingFlag ||
            currentInputEditorInfo.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
        ) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        if (lastSelection.isEmpty()) {
            if (lastSelection.start <= 0) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                currentInputConnection.deleteSurroundingTextInCodePoints(1, 0)
            } else {
                currentInputConnection.deleteSurroundingText(1, 0)
            }
        } else {
            currentInputConnection.commitText("", 0)
        }
    }

    private fun handleReturnKey() {
        currentInputEditorInfo.run {
            // RustDesk's Android text proxy forwards Enter through its key-event path. Its
            // editor-action path can report success even when a Windows peer receives nothing.
            // Use an explicit down/up pair for this package so the peer gets VK_ENTER exactly
            // once; keep standard editor actions for ordinary Android applications.
            if (packageName == KEMI_REMOTE_PACKAGE) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return
            }
            if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL ||
                imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)
            ) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return
            }
            if (actionLabel?.isNotEmpty() == true && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                performEditorActionOrEnter(actionId)
                return
            }
            when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_ACTION_NONE -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                else -> performEditorActionOrEnter(action)
            }
        }
    }

    /**
     * Some editors advertise an IME action but reject it at runtime. In that case the return
     * key must still behave like a physical Enter instead of becoming a silent no-op.
     */
    private fun performEditorActionOrEnter(action: Int) {
        if (!currentInputConnection.performEditorAction(action)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun handleArrowKey(keyCode: Int) {
        val type = currentInputEditorInfo.inputType and InputType.TYPE_MASK_CLASS
        val variation = currentInputEditorInfo.inputType and InputType.TYPE_MASK_VARIATION
        if (type == InputType.TYPE_NULL ||
            // confirm URL suggestion in browser location bar, see also https://bugzilla.mozilla.org/show_bug.cgi?id=1999915
            type == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI
        ) {
            sendDownUpKeyEvents(keyCode)
            return
        }
        val (start, end) = currentInputSelection
        val offset = if (start == end) 1 else 0
        val target = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> start - offset
            KeyEvent.KEYCODE_DPAD_RIGHT -> end + offset
            else -> return
        }
        currentInputConnection.setSelection(target, target)
    }

    fun commitText(text: String, cursor: Int = -1) {
        val ic = currentInputConnection ?: return
        // when composing text equals commit content, finish composing text as-is
        if (composing.isNotEmpty() && composingText.toString() == text) {
            val c = if (cursor == -1) text.length else cursor
            val target = composing.start + c
            resetComposingState()
            ic.withBatchEdit {
                if (selection.current.start != target) {
                    selection.predict(target)
                    ic.setSelection(target, target)
                }
                ic.finishComposingText()
            }
            return
        }
        // committed text should replace composing (if any), replace selected range (if any),
        // or simply prepend before cursor
        val start = if (composing.isEmpty()) selection.latest.start else composing.start
        resetComposingState()
        if (cursor == -1) {
            selection.predict(start + text.length)
            ic.commitText(text, 1)
        } else {
            val target = start + cursor
            selection.predict(target)
            ic.withBatchEdit {
                commitText(text, 1)
                setSelection(target, target)
            }
        }
    }

    fun beginVoiceComposing() {
        cancelVoiceComposing()
        finishComposing()
        voiceComposingStart = selection.latest.start
        postFcitxJob { reset() }
    }

    fun updateVoiceComposing(text: String) {
        if (text.isBlank() || voiceComposingStart < 0) return
        val ic = currentInputConnection ?: return
        val styledText = SpannableString(text).apply {
            setSpan(
                BackgroundColorSpan(highlightColor),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        selection.predict(voiceComposingStart + text.length)
        ic.setComposingText(styledText, 1)
    }

    fun commitVoiceComposing(text: String) {
        if (voiceComposingStart < 0) {
            commitText(text)
            return
        }
        val ic = currentInputConnection ?: return
        val start = voiceComposingStart
        voiceComposingStart = -1
        selection.predict(start + text.length)
        ic.commitText(text, 1)
    }

    fun cancelVoiceComposing() {
        if (voiceComposingStart < 0) return
        val ic = currentInputConnection
        val start = voiceComposingStart
        voiceComposingStart = -1
        selection.predict(start)
        ic?.withBatchEdit {
            setComposingText("", 1)
            finishComposingText()
        }
    }

    private fun sendDownKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
        connection: InputConnection? = currentInputConnection
    ): Boolean = connection?.sendKeyEvent(
        KeyEvent(
            eventTime,
            eventTime,
            KeyEvent.ACTION_DOWN,
            keyEventCode,
            0,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            ScancodeMapping.keyCodeToScancode(keyEventCode),
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
    ) == true

    private fun sendUpKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
        connection: InputConnection? = currentInputConnection
    ): Boolean = connection?.sendKeyEvent(
        KeyEvent(
            eventTime,
            SystemClock.uptimeMillis(),
            KeyEvent.ACTION_UP,
            keyEventCode,
            0,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            ScancodeMapping.keyCodeToScancode(keyEventCode),
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
    ) == true

    /**
     * Forward a desktop modifier edge without entering Fcitx or collapsing it into a chord.
     * The remote mouse channel can operate between this DOWN and the matching UP.
     */
    fun sendDesktopModifierKeyState(state: KeyState, down: Boolean) {
        val keyCode = when (state) {
            KeyState.Ctrl -> KeyEvent.KEYCODE_CTRL_LEFT
            KeyState.Alt -> KeyEvent.KEYCODE_ALT_LEFT
            KeyState.Shift -> KeyEvent.KEYCODE_SHIFT_LEFT
            KeyState.Meta -> KeyEvent.KEYCODE_META_LEFT
            else -> return
        }
        if (down) {
            if (state in pressedDesktopModifiers || currentInputConnection == null) return
            val downTime = SystemClock.uptimeMillis()
            pressedDesktopModifiers[state] = downTime
            val metaState = KeyStates(*pressedDesktopModifiers.keys.toTypedArray()).metaState
            sendDownKeyEvent(downTime, keyCode, metaState)
        } else {
            val downTime = pressedDesktopModifiers[state] ?: return
            // Build meta state before removal so the UP still identifies the released modifier.
            val metaState = KeyStates(*pressedDesktopModifiers.keys.toTypedArray()).metaState
            sendUpKeyEvent(downTime, keyCode, metaState)
            pressedDesktopModifiers.remove(state)
        }
    }

    private fun releaseDesktopModifierKeys() {
        pressedDesktopModifiers.keys.toList().asReversed().forEach { state ->
            sendDesktopModifierKeyState(state, down = false)
        }
    }

    /** Send one complete physical-style desktop key press to the current editor. */
    fun sendDesktopKeyPress(keyCode: Int) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN || ownedResourcesReleased) return
        val connection = currentInputConnection ?: return
        val downTime = SystemClock.uptimeMillis()
        val metaState = KeyStates(*pressedDesktopModifiers.keys.toTypedArray()).metaState
        sendDownKeyEvent(downTime, keyCode, metaState, connection)
        sendUpKeyEvent(downTime, keyCode, metaState, connection)
    }

    fun sendDesktopMouseMove(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0 || ownedResourcesReleased) return
        val connection = currentInputConnection ?: return
        synchronized(desktopMouseMoveLock) {
            if (pendingDesktopMouseConnection !== connection) {
                pendingDesktopMouseDx = 0
                pendingDesktopMouseDy = 0
                pendingDesktopMouseConnection = connection
            }
            pendingDesktopMouseDx = (pendingDesktopMouseDx + dx)
                .coerceIn(-MAX_PENDING_MOUSE_DELTA, MAX_PENDING_MOUSE_DELTA)
            pendingDesktopMouseDy = (pendingDesktopMouseDy + dy)
                .coerceIn(-MAX_PENDING_MOUSE_DELTA, MAX_PENDING_MOUSE_DELTA)
        }
        desktopMouseMoveSignal.trySend(Unit)
    }

    private fun dispatchPendingDesktopMouseMove() {
        val command = synchronized(desktopMouseMoveLock) {
            val connection = pendingDesktopMouseConnection ?: return
            val dx = pendingDesktopMouseDx
            val dy = pendingDesktopMouseDy
            pendingDesktopMouseDx = 0
            pendingDesktopMouseDy = 0
            Triple(connection, dx, dy)
        }
        if (command.second == 0 && command.third == 0) return
        val targetDisplayId = desktopMouseTargetDisplayId()
        if (systemMouseInjector.move(targetDisplayId, command.second, command.third)) {
            if (Log.isLoggable(REMOTE_MOUSE_LOG_TAG, Log.DEBUG)) {
                Log.d(
                    REMOTE_MOUSE_LOG_TAG,
                    "system move display=$targetDisplayId dx=${command.second} dy=${command.third}"
                )
            }
            return
        }
        val accepted = runCatching {
            command.first.performPrivateCommand(
                RemoteMouseInputProtocol.ACTION,
                Bundle().apply {
                    putString(RemoteMouseInputProtocol.EXTRA_TYPE, RemoteMouseInputProtocol.TYPE_MOVE)
                    putInt(RemoteMouseInputProtocol.EXTRA_DX, command.second)
                    putInt(RemoteMouseInputProtocol.EXTRA_DY, command.third)
                }
            )
        }.getOrElse { error ->
            Log.w(REMOTE_MOUSE_LOG_TAG, "mouse move dispatch failed", error)
            false
        }
        if (Log.isLoggable(REMOTE_MOUSE_LOG_TAG, Log.DEBUG)) {
            Log.d(
                REMOTE_MOUSE_LOG_TAG,
                "move dx=${command.second} dy=${command.third} accepted=$accepted"
            )
        }
    }

    private fun clearPendingDesktopMouseMove() {
        synchronized(desktopMouseMoveLock) {
            pendingDesktopMouseDx = 0
            pendingDesktopMouseDy = 0
            pendingDesktopMouseConnection = null
        }
    }

    fun sendDesktopMouseButtonState(button: String, down: Boolean) {
        if (button !in RemoteMouseInputProtocol.BUTTONS) return
        if (down && button in pressedDesktopMouseButtons) return
        if (!down && button !in pressedDesktopMouseButtons) return
        val targetDisplayId = desktopMouseTargetDisplayId()
        val systemAccepted = systemMouseInjector.button(targetDisplayId, button, down)
        val accepted = systemAccepted || currentInputConnection?.performPrivateCommand(
            RemoteMouseInputProtocol.ACTION,
            Bundle().apply {
                putString(RemoteMouseInputProtocol.EXTRA_TYPE, RemoteMouseInputProtocol.TYPE_BUTTON)
                putString(RemoteMouseInputProtocol.EXTRA_BUTTON, button)
                putBoolean(RemoteMouseInputProtocol.EXTRA_DOWN, down)
            }
        ) == true
        if (Log.isLoggable(REMOTE_MOUSE_LOG_TAG, Log.DEBUG)) {
            Log.d(REMOTE_MOUSE_LOG_TAG, "button=$button down=$down accepted=$accepted")
        }
        if (down) {
            if (accepted) pressedDesktopMouseButtons.add(button)
        } else {
            // A retired InputConnection may reject the final UP. The receiver independently
            // releases held buttons when its keyboard session closes, so never retain stale
            // local state and retry an old UP against a future editor.
            pressedDesktopMouseButtons.remove(button)
        }
    }

    fun sendDesktopSystemKeyState(keyCode: Int, down: Boolean) {
        if (keyCode !in DESKTOP_REMOTE_NAVIGATION_KEY_CODES) return
        if (down) {
            if (keyCode in pressedDesktopRemoteNavigationKeys) return
            val connection = currentInputConnection ?: return
            val downTime = SystemClock.uptimeMillis()
            if (sendDownKeyEvent(downTime, keyCode, connection = connection)) {
                pressedDesktopRemoteNavigationKeys[keyCode] = connection to downTime
            }
        } else {
            val state = pressedDesktopRemoteNavigationKeys.remove(keyCode) ?: return
            sendUpKeyEvent(state.second, keyCode, connection = state.first)
        }
    }

    private fun releaseDesktopSystemKeys() {
        pressedDesktopRemoteNavigationKeys.keys.toList().asReversed().forEach { keyCode ->
            sendDesktopSystemKeyState(keyCode, down = false)
        }
    }

    private fun releaseDesktopMouseButtons() {
        pressedDesktopMouseButtons.toList().asReversed().forEach { button ->
            sendDesktopMouseButtonState(button, down = false)
        }
    }

    private fun releaseDesktopInputStates() {
        releaseDesktopSystemKeys()
        releaseDesktopMouseButtons()
        releaseDesktopModifierKeys()
    }

    /**
     * A normal editor owns the same display as its IME. RustDesk deliberately hosts its proxy
     * editor on the opposite display, so route the physical mouse back to the source display that
     * requested the keyboard.
     */
    private fun desktopMouseTargetDisplayId(): Int {
        @Suppress("DEPRECATION")
        val imeDisplayId = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        if (currentInputEditorInfo.packageName !in CROSS_DISPLAY_EDITOR_PACKAGES) {
            return imeDisplayId
        }
        val requestedDisplayId = when (imeDisplayId) {
            android.view.Display.DEFAULT_DISPLAY -> SECONDARY_IME_DISPLAY_ID
            SECONDARY_IME_DISPLAY_ID -> android.view.Display.DEFAULT_DISPLAY
            else -> android.view.Display.DEFAULT_DISPLAY
        }
        val requestedDisplay = getSystemService(DisplayManager::class.java)
            ?.getDisplay(requestedDisplayId)
        return requestedDisplayId.takeIf {
            requestedDisplay?.isValid == true && requestedDisplay.state != android.view.Display.STATE_OFF
        } ?: imeDisplayId
    }

    /**
     * Defer IME window removal until the click's ACTION_UP has left the current dispatch stack.
     * This avoids the V900 ROM retargeting the tail of the gesture to KEMI's button underneath.
     */
    fun requestHideSelfAfterTouch(source: View) {
        cancelPendingShow()
        if (pendingTouchHideRequest != null) return
        source.isEnabled = false
        val host = source.rootView
        val request = Runnable {
            pendingTouchHideRequest = null
            pendingTouchHideHost = null
            pendingTouchHideSource = null
            source.isEnabled = true
            releaseDesktopInputStates()
            requestHideSelf(0)
        }
        pendingTouchHideRequest = request
        pendingTouchHideHost = host
        pendingTouchHideSource = source
        host.postDelayed(request, TOUCH_HIDE_DELAY_MS)
    }

    fun cancelPendingTouchHideRequest() {
        val request = pendingTouchHideRequest ?: return
        pendingTouchHideHost?.removeCallbacks(request)
        pendingTouchHideSource?.isEnabled = true
        pendingTouchHideRequest = null
        pendingTouchHideHost = null
        pendingTouchHideSource = null
    }

    fun deleteSelection() {
        val lastSelection = selection.latest
        if (lastSelection.isEmpty()) return
        selection.predict(lastSelection.start)
        currentInputConnection?.commitText("", 1)
    }

    fun sendCombinationKeyEvents(
        keyEventCode: Int,
        alt: Boolean = false,
        ctrl: Boolean = false,
        shift: Boolean = false
    ) {
        var metaState = 0
        if (alt) metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        val eventTime = SystemClock.uptimeMillis()
        if (alt) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        if (ctrl) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        if (shift) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDownKeyEvent(eventTime, keyEventCode, metaState)
        sendUpKeyEvent(eventTime, keyEventCode, metaState)
        if (shift) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        if (ctrl) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        if (alt) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
    }

    fun applySelectionOffset(offsetStart: Int, offsetEnd: Int = 0) {
        val lastSelection = selection.latest
        currentInputConnection?.also {
            val start = max(lastSelection.start + offsetStart, 0)
            val end = max(lastSelection.end + offsetEnd, 0)
            if (start > end) return
            selection.predict(start, end)
            it.setSelection(start, end)
        }
    }

    fun cancelSelection() {
        val lastSelection = selection.latest
        if (lastSelection.isEmpty()) return
        val end = lastSelection.end
        selection.predict(end)
        currentInputConnection?.setSelection(end, end)
    }

    private lateinit var lastKnownConfig: Configuration

    override fun onConfigurationChanged(newConfig: Configuration) {
        postFcitxJob { reset() }
        /**
         * skip keyboard|keyboardHidden changes, because we have [inputDeviceMgr]
         * skip uiMode (system light/dark mode) changes, because we have [onThemeChangeListener]
         * to replace InputView(s) when needed
         * [android.inputmethodservice.InputMethodService.onConfigurationChanged] would call
         * resetStateForNewConfiguration() which calls initViews() causes InputView(s) to be replaced again
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/inputmethodservice/InputMethodService.java#1984
         */
        val f = ActivityInfo.CONFIG_KEYBOARD or
                ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
                ActivityInfo.CONFIG_UI_MODE
        val diff = lastKnownConfig.diff(newConfig)
        Timber.d("onConfigurationChanged diff=$diff")
        /**
         * perform `super.onConfigurationChanged` only when `newConfig` diff fall outside "skipped" flags
         * we have to calculate the mask ourselves because nobody knows how `handledConfigChanges` works
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/inputmethodservice/InputMethodService.java#1876
         */
        if (diff and f != diff) {
            super.onConfigurationChanged(newConfig)
        }
        lastKnownConfig = newConfig
        inputView?.requestCurrentDisplayInsets("configuration")
    }

    override fun onWindowShown() {
        super.onWindowShown()
        inputView?.onImeWindowShown()
        inputView?.requestCurrentDisplayInsets("window_shown")
        try {
            highlightColor = styledColor(android.R.attr.colorAccent).alpha(0.4f)
        } catch (_: Exception) {
            Timber.w("Device does not support android.R.attr.colorAccent which it should have.")
        }
        InputFeedbacks.syncSystemPrefs()
    }

    override fun onWindowHidden() {
        cancelPendingTouchHideRequest()
        releaseDesktopInputStates()
        inputView?.onImeWindowHidden()
        super.onWindowHidden()
    }

    override fun onCreateInputView(): View? {
        inputView?.takeIf { it.reusableForImeShow }?.let {
            Log.i(IME_LIFECYCLE_TAG, "reused input view generation=$inputViewGeneration")
            // This Android 12 dual-display firmware requires the legacy/manual installation
            // contract used by KBoard: setInputView() is called by replaceInputView() and this
            // callback returns null. Returning the View makes the firmware repeatedly tear down
            // and rebind the whole InputMethodService. If the installed view is still reusable,
            // there is nothing to rebuild or reinstall here.
            return null
        }
        replaceInputViews(ThemeManager.activeTheme)
        return null
    }

    override fun setInputView(view: View) {
        // KBoard installs its input view manually and onCreateInputView() returns null. Keep the
        // framework call centralized here; onCreateInputView() avoids calling us again while the
        // current hierarchy remains reusable.
        super.setInputView(view)
        // input method layout has not changed in 11 years:
        // https://android.googlesource.com/platform/frameworks/base/+/ae3349e1c34f7aceddc526cd11d9ac44951e97b6/core/res/res/layout/input_method.xml
        // expand inputArea to fullscreen
        contentView.findViewById<FrameLayout>(android.R.id.inputArea)
            .updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        /**
         * expand InputView to fullscreen, since [android.inputmethodservice.InputMethodService.setInputView]
         * would set InputView's height to [ViewGroup.LayoutParams.WRAP_CONTENT]
         */
        view.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private var inputViewLocation = intArrayOf(0, 0)

    override fun onComputeInsets(outInsets: Insets) {
        if (inputDeviceMgr.isVirtualKeyboard) {
            inputView?.keyboardView?.getLocationInWindow(inputViewLocation)
            val touchableTop = inputViewLocation[1] - (inputView?.floatingResizeTouchInset ?: 0)
            outInsets.apply {
                contentTopInsets = inputViewLocation[1]
                visibleTopInsets = touchableTop
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        } else {
            val n = decorView.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
            val h = decorView.height - n
            outInsets.apply {
                contentTopInsets = h
                visibleTopInsets = h
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        }
    }

    // always show InputView since we delegate CandidatesView's visibility to it
    @SuppressLint("MissingSuperCall")
    override fun onEvaluateInputViewShown() = true

    fun superEvaluateInputViewShown() = super.onEvaluateInputViewShown()

    override fun onEvaluateFullscreenMode() = false

    fun toggleFloatingKeyboard(): Boolean = inputView?.toggleFloatingKeyboard() ?: false

    fun toggleImeDisplay() {
        val secondaryDisplay = getSystemService(DisplayManager::class.java)
            ?.getDisplay(SECONDARY_IME_DISPLAY_ID)
        if (secondaryDisplay == null) {
            Toast.makeText(this, R.string.secondary_display_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val relayIme = ComponentName(this, DisplaySwitchInputMethodService::class.java)
            .flattenToShortString()
        if (inputMethodManager.enabledInputMethodList.none { it.id == relayIme }) {
            Timber.e("Display-switch IME relay is not enabled")
            Toast.makeText(this, R.string.screen_switch_relay_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        @Suppress("DEPRECATION")
        val currentDisplayId = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        val moveToSecondary = currentDisplayId != SECONDARY_IME_DISPLAY_ID
        val mode = if (moveToSecondary) DISPLAY_IME_MODE_LOCAL else DISPLAY_IME_MODE_FALLBACK
        val policyIntent = Intent(ACTION_SET_DISPLAY_IME_POLICY)
            .setPackage(DISPLAY_IME_POLICY_PACKAGE)
            .putExtra(EXTRA_DISPLAY_ID, SECONDARY_IME_DISPLAY_ID)
            .putExtra(EXTRA_MODE, mode)
        Timber.i(
            "Requested IME display switch: current=%d target=%d mode=%s",
            currentDisplayId,
            if (moveToSecondary) SECONDARY_IME_DISPLAY_ID else android.view.Display.DEFAULT_DISPLAY,
            mode
        )

        // Android 12 applies this vendor policy when it creates a new IME token. Wait until the
        // ordered vendor receiver has finished, then bind the one-shot KBoard relay. This avoids
        // racing token creation against the policy update.
        sendOrderedBroadcast(
            policyIntent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    // The ordered vendor policy callback can arrive after the dual-display
                    // firmware has already replaced this service generation.
                    if (!ownedResourcesReleased) switchInputMethod(relayIme)
                }
            },
            null,
            Activity.RESULT_OK,
            null,
            null
        )
    }

    private fun forwardKeyEvent(event: KeyEvent): Boolean {
        // reason to use a self increment index rather than timestamp:
        // KeyUp and KeyDown events actually can happen on the same time
        val timestamp = cachedKeyEventIndex++
        cachedKeyEvents.put(timestamp, event)
        val sym = KeySym.fromKeyEvent(event)
        if (sym != null) {
            val states = KeyStates.fromKeyEvent(event)
            val up = event.action == KeyEvent.ACTION_UP
            postFcitxJob {
                sendKey(sym, states, event.scanCode, up, timestamp)
            }
            return true
        }
        Timber.d("Skipped KeyEvent: $event")
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isPhysicalHardwareKey(event) && hardwareKeyAnomalyFilter.shouldDropDown(
                HardwareKeyAnomalyFilter.Key(event.deviceId, keyCode),
                event.eventTime,
                event.isPrintingKey,
                KeyEvent.isModifierKey(keyCode),
                event.repeatCount
            )
        ) {
            Timber.w(
                "Dropped abnormal hardware key down: device=%d keyCode=%d after < %dms",
                event.deviceId,
                keyCode,
                HardwareKeyAnomalyFilter.DEFAULT_MINIMUM_RELEASE_TO_PRESS_MILLIS
            )
            return true
        }
        // request to show floating CandidatesView when pressing physical keyboard
        if (inputDeviceMgr.evaluateOnKeyDown(event, this)) {
            postFcitxJob {
                focus(true)
            }
            forceShowSelf()
        }
        return forwardKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isPhysicalHardwareKey(event) && hardwareKeyAnomalyFilter.shouldDropUp(
                HardwareKeyAnomalyFilter.Key(event.deviceId, keyCode),
                event.eventTime,
                event.isPrintingKey,
                KeyEvent.isModifierKey(keyCode)
            )
        ) {
            return true
        }
        return forwardKeyEvent(event) || super.onKeyUp(keyCode, event)
    }

    private fun isPhysicalHardwareKey(event: KeyEvent): Boolean =
        event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD &&
            event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY == 0

    // Added in API level 14, deprecated in 29
    // it's needed because editors still use it even on API 36
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        inputDeviceMgr.evaluateOnViewClicked(this)
    }

    @RequiresApi(34)
    override fun onUpdateEditorToolType(toolType: Int) {
        super.onUpdateEditorToolType(toolType)
        inputDeviceMgr.evaluateOnUpdateEditorToolType(toolType, this)
    }

    private var firstBindInput = true

    override fun onBindInput() {
        val uid = currentInputBinding.uid
        val pkgName = pkgNameCache.forUid(uid)
        Timber.d("onBindInput: uid=$uid pkg=$pkgName")
        postFcitxJob {
            // ensure InputContext has been created before focusing it
            activate(uid, pkgName)
        }
        if (firstBindInput) {
            firstBindInput = false
            // only use input method from subtype for the first `onBindInput`, because
            // 1. fcitx has `ShareInputState` option, thus reading input method from subtype
            //    everytime would ruin `ShareInputState=Program`
            // 2. im from subtype should be read once, when user changes input method from other
            //    app to a subtype of ours via system input method picker (on 34+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val subtype = inputMethodManager.currentInputMethodSubtype ?: return
                val im = SubtypeManager.inputMethodOf(subtype)
                postFcitxJob {
                    activateIme(im)
                }
            }
        }
    }

    /**
     * When input method changes internally (eg. via language switch key or keyboard shortcut),
     * we want to notify system that subtype has changed (see [^1]), then ignore the incoming
     * [onCurrentInputMethodSubtypeChanged] callback.
     * Input method should only be changed when user changes subtype in system input method picker
     * manually.
     */
    private var skipNextSubtypeChange: String? = null

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val im = SubtypeManager.inputMethodOf(newSubtype)
            Timber.d("onCurrentInputMethodSubtypeChanged: im=$im")
            // don't change input method if this "subtype change" was our notify to system
            // see [^1]
            if (skipNextSubtypeChange == im) {
                skipNextSubtypeChange = null
                return
            }
            postFcitxJob {
                activateIme(im)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        hardwareKeyAnomalyFilter.reset()
        // update selection as soon as possible
        // sometimes when restarting input, onUpdateSelection happens before onStartInput, and
        // initialSel{Start,End} is outdated. but it's the client app's responsibility to send
        // right cursor position, try to workaround this would simply introduce more bugs.
        selection.resetTo(attribute.initialSelStart, attribute.initialSelEnd)
        resetComposingState()
        val flags = CapabilityFlags.fromEditorInfo(attribute)
        capabilityFlags = flags
        // EditorInfo may change between onStartInput and onStartInputView
        inputDeviceMgr.notifyOnStartInput(attribute)
        Timber.d("onStartInput: initialSel=${selection.current}, restarting=$restarting")
        val isNullType = attribute.isTypeNull()
        // wait until InputContext created/activated
        postFcitxJob {
            if (restarting) {
                // when input restarts in the same editor, focus out to clear previous state
                focus(false)
                // try focus out before changing CapabilityFlags,
                // to avoid confusing state of different text fields
            }
            // EditorInfo can be different in onStartInput and onStartInputView,
            // especially in browsers
            setCapFlags(flags)
            // for hardware keyboard, focus to allow switching input methods before onStartInputView
            if (!isNullType) {
                focus(true)
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        Timber.d("onStartInputView: restarting=$restarting")
        inputView?.requestCurrentDisplayInsets("start_input_view")
        postFcitxJob {
            focus(true)
        }
        if (inputDeviceMgr.evaluateOnStartInputView(info, this)) {
            // because onStartInputView will always be called after onStartInput,
            // editorInfo and capFlags should be up-to-date
            inputView?.startInput(info, capabilityFlags, restarting)
        } else {
            if (currentInputConnection?.monitorCursorAnchor() != true) {
                if (!decorLocationUpdated) {
                    updateDecorLocation()
                }
                // anchor CandidatesView to bottom-left corner in case InputConnection does not
                // support monitoring CursorAnchorInfo
                candidatesView?.updateCursorAnchor(contentSize)
            }
            showStatusIcon(StatusIconMapping.fromEntry(fcitx.runImmediately { inputMethodEntryCached }))
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        // onUpdateSelection can left behind when user types quickly enough, eg. long press backspace
        cursorUpdateIndex += 1
        Timber.d("onUpdateSelection: old=[$oldSelStart,$oldSelEnd] new=[$newSelStart,$newSelEnd] cand=[$candidatesStart,$candidatesEnd]")
        handleCursorUpdate(
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
            cursorUpdateIndex
        )
        inputView?.updateSelection(newSelStart, newSelEnd)
    }

    private val contentSize = floatArrayOf(0f, 0f)
    private val decorLocation = floatArrayOf(0f, 0f)
    private val decorLocationInt = intArrayOf(0, 0)
    private var decorLocationUpdated = false

    private fun updateDecorLocation() {
        contentSize[0] = contentView.width.toFloat()
        contentSize[1] = contentView.height.toFloat()
        decorView.getLocationOnScreen(decorLocationInt)
        decorLocation[0] = decorLocationInt[0].toFloat()
        decorLocation[1] = decorLocationInt[1].toFloat()
        // contentSize and decorLocation can be completely wrong,
        // when measuring right after the very first onStartInputView() of an IMS' lifecycle
        if (contentSize[0] > 0 && contentSize[1] > 0) {
            decorLocationUpdated = true
        }
    }

    private val anchorPosition = floatArrayOf(0f, 0f, 0f, 0f)

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        val bounds = info.getCharacterBounds(0)
        if (bounds != null) {
            // anchor to start of composing span instead of insertion mark if available
            val horizontal =
                if (candidatesView?.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition[0] = horizontal
            anchorPosition[1] = bounds.bottom
            anchorPosition[2] = horizontal
            anchorPosition[3] = bounds.top
        } else {
            anchorPosition[0] = info.insertionMarkerHorizontal
            anchorPosition[1] = info.insertionMarkerBottom
            anchorPosition[2] = info.insertionMarkerHorizontal
            anchorPosition[3] = info.insertionMarkerTop
        }
        // avoid calling `decorView.getLocationOnScreen` repeatedly
        if (!decorLocationUpdated) {
            updateDecorLocation()
        }
        if (anchorPosition.any(Float::isNaN)) {
            // anchor candidates view to bottom-left corner in case CursorAnchorInfo is invalid
            candidatesView?.updateCursorAnchor(contentSize)
            return
        }
        // params of `Matrix.mapPoints` must be [x0, y0, x1, y1]
        info.matrix.mapPoints(anchorPosition)
        val (xOffset, yOffset) = decorLocation
        anchorPosition[0] -= xOffset
        anchorPosition[1] -= yOffset
        anchorPosition[2] -= xOffset
        anchorPosition[3] -= yOffset
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    private fun handleCursorUpdate(
        newSelStart: Int,
        newSelEnd: Int,
        newComposingStart: Int,
        newComposingEnd: Int,
        updateIndex: Int
    ) {
        if (selection.consume(newSelStart, newSelEnd)) {
            // try restore composing range in case it was dropped by InputFilter
            // but only when prediction matches, since InputFilter can also change editor content
            // ref:
            // https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/widget/Editor.java#2083
            // https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/widget/TextView.java#7351
            if (newComposingStart == -1 && newComposingEnd == -1 && composing.isNotEmpty()) {
                currentInputConnection?.setComposingRegion(composing.start, composing.end)
            }
            return // do nothing if prediction matches
        } else {
            // cursor update can't match any prediction: it's treated as a user input
            selection.resetTo(newSelStart, newSelEnd)
        }
        // skip selection range update, we only care about selection cursor (zero width) here
        if (newSelStart != newSelEnd) return
        // do reset if composing is empty && input panel is not empty
        if (composing.isEmpty()) {
            postFcitxJob {
                if (!isEmpty()) {
                    Timber.d("handleCursorUpdate: reset")
                    reset()
                }
            }
            return
        }
        // check if cursor inside composing text
        if (composing.contains(newSelStart)) {
            if (ignoreSystemCursor) return
            // fcitx cursor position is relative to client preedit (composing text)
            val position = newSelStart - composing.start
            // move fcitx cursor when cursor position changed
            if (position != composingText.cursor) {
                // cursor in InvokeActionEvent counts by "UTF-8 characters"
                val codePointPosition = composingText.codePointCountUntil(position)
                postFcitxJob {
                    if (updateIndex != cursorUpdateIndex) return@postFcitxJob
                    Timber.d("handleCursorUpdate: move fcitx cursor to $codePointPosition")
                    moveCursor(codePointPosition)
                }
            }
        } else {
            Timber.d("handleCursorUpdate: focus out/in")
            resetComposingState()
            // cursor outside composing range, finish composing as-is
            currentInputConnection?.finishComposingText()
            // `fcitx.reset()` here would commit preedit after new cursor position
            // since we have `ClientUnfocusCommit`, focus out and in would do the trick
            postFcitxJob {
                focusOutIn()
            }
        }
    }

    // because setComposingText(text, cursor) can only put cursor at end of composing,
    // sometimes onUpdateSelection would receive event with wrong cursor position.
    // those events need to be filtered.
    // because of https://android.googlesource.com/platform/frameworks/base.git/+/refs/tags/android-11.0.0_r45/core/java/android/view/inputmethod/BaseInputConnection.java#851
    // it's not possible to set cursor inside composing text
    private fun updateComposingText(text: FormattedText) {
        val ic = currentInputConnection ?: return
        val lastSelection = selection.latest
        ic.beginBatchEdit()
        if (composingText.spanEquals(text)) {
            // composing text content is up-to-date
            // update cursor only when it's not empty AND cursor position is valid
            if (text.length > 0 && text.cursor >= 0) {
                val p = text.cursor + composing.start
                if (p != lastSelection.start) {
                    Timber.d("updateComposingText: set Android selection ($p, $p)")
                    ic.setSelection(p, p)
                    selection.predict(p)
                }
            }
        } else {
            // composing text content changed
            Timber.d("updateComposingText: '$text' lastSelection=$lastSelection")
            if (text.isEmpty()) {
                if (composing.isEmpty()) {
                    // do not reset saved selection range when incoming composing
                    // and saved composing range are both empty:
                    // composing.start is invalid when it's empty.
                    selection.predict(lastSelection.start)
                } else {
                    // clear composing text, put cursor at start of original composing
                    selection.predict(composing.start)
                    composing.clear()
                }
                ic.setComposingText("", 1)
            } else {
                val start = if (composing.isEmpty()) lastSelection.start else composing.start
                composing.update(start, start + text.length)
                // skip cursor reposition when:
                // - preedit cursor is at the end
                // - cursor position is invalid
                if (text.cursor == text.length || text.cursor < 0) {
                    selection.predict(composing.end)
                    ic.setComposingText(text.toSpannedString(highlightColor), 1)
                } else {
                    val p = text.cursor + composing.start
                    selection.predict(p)
                    ic.setComposingText(text.toSpannedString(highlightColor), 1)
                    ic.setSelection(p, p)
                }
            }
            Timber.d("updateComposingText: composing=$composing")
        }
        composingText = text
        ic.endBatchEdit()
    }

    /**
     * Finish composing text and leave cursor position as-is.
     * Also updates internal composing state of [FcitxInputMethodService].
     */
    fun finishComposing() {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) return
        composing.clear()
        composingText = FormattedText.Empty
        ic.finishComposingText()
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        // ignore inline suggestion when disabled by user || using physical keyboard with floating candidates view
        if (!inlineSuggestions || !inputDeviceMgr.isVirtualKeyboard) return null
        val theme = ThemeManager.activeTheme
        val chipDrawable =
            if (theme.isDark) R.drawable.bkg_inline_suggestion_dark else R.drawable.bkg_inline_suggestion_light
        val chipBg = Icon.createWithResource(this, chipDrawable).setTint(theme.keyTextColor)
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackgroundColor(Color.TRANSPARENT)
                    .setPadding(0, 0, 0, 0)
                    .build()
            )
            .setChipStyle(
                ViewStyle.Builder()
                    .setBackground(chipBg)
                    .setPadding(dp(10), 0, dp(10), 0)
                    .build()
            )
            .setTitleStyle(
                TextViewStyle.Builder()
                    .setLayoutMargin(dp(4), 0, dp(4), 0)
                    .setTextColor(theme.keyTextColor)
                    .setTextSize(14f)
                    .build()
            )
            .setSubtitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(theme.altKeyTextColor)
                    .setTextSize(12f)
                    .build()
            )
            .setStartIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(theme.altKeyTextColor))
                    .build()
            )
            .setEndIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(theme.altKeyTextColor))
                    .build()
            )
            .build()
        val styleBundle = UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()
        val spec = InlinePresentationSpec
            .Builder(Size(0, 0), Size(Int.MAX_VALUE, Int.MAX_VALUE))
            .setStyle(styleBundle)
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inlineSuggestions || !inputDeviceMgr.isVirtualKeyboard) return false
        return inputView?.handleInlineSuggestions(response) == true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cancelPendingShow()
        Timber.d("onFinishInputView: finishingInput=$finishingInput")
        cancelPendingTouchHideRequest()
        releaseDesktopInputStates()
        inputView?.onImeWindowHidden()
        decorLocationUpdated = false
        inputDeviceMgr.onFinishInputView()
        currentInputConnection?.apply {
            finishComposingText()
            monitorCursorAnchor(false)
        }
        resetComposingState()
        postFcitxJob {
            focusOutIn()
        }
        hideStatusIcon()
        showingDialog?.dismiss()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("onFinishInput")
        cancelPendingTouchHideRequest()
        clearPendingDesktopMouseMove()
        releaseDesktopInputStates()
        postFcitxJob {
            focus(false)
        }
        capabilityFlags = CapabilityFlags.DefaultFlags
    }

    override fun onUnbindInput() {
        // InputMethodService clears currentInputBinding during super.onUnbindInput(). Capture
        // the editor uid first so the native context is always deactivated for this session.
        val uid = currentInputBinding?.uid
        super.onUnbindInput()
        cancelPendingTouchHideRequest()
        clearPendingDesktopMouseMove()
        releaseDesktopInputStates()
        cachedKeyEvents.evictAll()
        cachedKeyEventIndex = 0
        cursorUpdateIndex = 0
        // currentInputBinding can be null on some devices under some special Multi-screen mode
        if (uid == null) return
        Timber.d("onUnbindInput: uid=$uid")
        postFcitxJob {
            deactivate(uid)
        }
    }

    override fun onDestroy() {
        releaseOwnedResources("framework_destroy")
        synchronized(PROCESS_IME_INSTANCE_LOCK) {
            if (processImeInstance?.get() === this) {
                processImeInstance = null
            }
        }
        super.onDestroy()
        // Android 12 dual-display firmware may keep an obsolete IInputMethodSessionWrapper and
        // deliver hide/update callbacks after onDestroy(). Keep the framework-owned window and
        // its lightweight internal Views intact: nulling private fields (or tearing down their
        // hierarchy reflectively) makes those valid late callbacks crash in
        // InputMethodService.updateFullscreenMode(). The heavyweight KBoard hierarchy was
        // already disposed and replaced with an application-context placeholder above.
        contentViewRef = null
        decorViewRef = null
    }

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        if (ownedResourcesReleased) {
            dialog.dismiss()
            return
        }
        showingDialog?.dismiss()
        dialog.window?.also {
            it.attributes.apply {
                token = decorView.windowToken
                type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            it.addFlags(
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            it.setDimAmount(styledFloat(android.R.attr.backgroundDimAmount))
        }
        dialog.setOnDismissListener {
            showingDialog = null
        }
        dialog.show()
        showingDialog = dialog
    }

    @Suppress("ConstPropertyName")
    companion object {
        private const val IME_LIFECYCLE_TAG = "KBoardImeLifecycle"
        private const val REMOTE_MOUSE_LOG_TAG = "KBoardRemoteMouse"
        private val DESKTOP_REMOTE_NAVIGATION_KEY_CODES = setOf(
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK
        )
        private const val MAX_PENDING_MOUSE_DELTA = 240
        private val PROCESS_IME_INSTANCE_LOCK = Any()
        private var processImeInstance: WeakReference<FcitxInputMethodService>? = null
        const val DeleteSurroundingFlag = "org.fcitx.fcitx5.android.DELETE_SURROUNDING"
        private const val ACTION_SET_DISPLAY_IME_POLICY =
            "com.newlink.action.SET_DISPLAY_IME_POLICY"
        private const val DISPLAY_IME_POLICY_PACKAGE = "com.newlink.device.ime"
        private const val KEMI_REMOTE_PACKAGE = "com.newlinksz.kemi.remote"
        private val CROSS_DISPLAY_EDITOR_PACKAGES = setOf(
            "com.carriez.flutter_hbb",
            KEMI_REMOTE_PACKAGE
        )
        private const val EXTRA_DISPLAY_ID = "display_id"
        private const val EXTRA_MODE = "mode"
        private const val DISPLAY_IME_MODE_LOCAL = "local"
        private const val DISPLAY_IME_MODE_FALLBACK = "fallback"
        private const val SECONDARY_IME_DISPLAY_ID = 2
        private const val TOUCH_HIDE_DELAY_MS = 100L
    }
}
