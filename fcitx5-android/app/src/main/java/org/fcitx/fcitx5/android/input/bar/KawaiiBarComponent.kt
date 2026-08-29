/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.bar

import android.graphics.Color
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ViewAnimator
import android.widget.inline.InlineContentView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent.CandidateListEvent
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.ClickToAttachWindow
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.ClickToDetachWindow
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.Hidden
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.BooleanKey.CandidateEmpty
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.BooleanKey.PreeditEmpty
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.CandidatesUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.ExtendedWindowAttached
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.PreeditUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.WindowDetached
import org.fcitx.fcitx5.android.input.bar.ui.CandidateUi
import org.fcitx.fcitx5.android.input.bar.ui.IdleUi
import org.fcitx.fcitx5.android.input.bar.ui.TitleUi
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.candidates.expanded.ExpandedCandidateStyle
import org.fcitx.fcitx5.android.input.candidates.expanded.window.FlexboxExpandedCandidateWindow
import org.fcitx.fcitx5.android.input.candidates.expanded.window.GridExpandedCandidateWindow
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.clipboard.ClipboardWindow
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.editing.TextEditingWindow
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.status.StatusAreaWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.input.voice.IflytekAsrClient
import org.fcitx.fcitx5.android.input.voice.VoicePermissionActivity
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.must
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import java.util.concurrent.Executor
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class KawaiiBarComponent : UniqueViewComponent<KawaiiBarComponent, FrameLayout>(),
    InputBroadcastReceiver {

    private val context by manager.context()
    private val theme by manager.theme()
    private val service by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()
    private val horizontalCandidate: HorizontalCandidateComponent by manager.must()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val popup: PopupComponent by manager.must()

    private val prefs = AppPrefs.getInstance()

    private val clipboardSuggestion = prefs.clipboard.clipboardSuggestion
    private val clipboardItemTimeout = prefs.clipboard.clipboardItemTimeout
    private val clipboardMaskSensitive by prefs.clipboard.clipboardMaskSensitive
    private val expandedCandidateStyle by prefs.keyboard.expandedCandidateStyle
    private val expandToolbarByDefault by prefs.keyboard.expandToolbarByDefault
    private val toolbarNumRowOnPassword by prefs.keyboard.toolbarNumRowOnPassword
    private val floatingKeyboard = prefs.keyboard.floatingKeyboard

    private var clipboardTimeoutJob: Job? = null
    private var voiceCommitJob: Job? = null
    private var voiceStartJob: Job? = null
    private var lastVoicePermissionPromptAt = 0L
    private var voiceNetworkCallbackRegistered = false
    private var disposed = false

    private var isClipboardFresh: Boolean = false
    private var isInlineSuggestionPresent: Boolean = false
    private var isCapabilityFlagsPassword: Boolean = false
    private var isKeyboardLayoutNumber: Boolean = false
    private var isToolbarManuallyToggled: Boolean = false
    private var shouldShowVoiceInput: Boolean = false
    private var desktopKeyboardMode: Boolean = false
    private var desktopVoiceButton: ToolButton? = null

    private val desktopVoiceTranscript by lazy {
        TextView(context).apply {
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(20), 0, dp(20), 0)
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(DESKTOP_VOICE_STATUS_BACKGROUND)
            // Keep this overlay permanently measured. Voice feedback changes alpha only so the
            // first press cannot request a new IME layout or move/resize the aquarium.
            visibility = View.VISIBLE
            alpha = 0f
        }
    }

    private val voiceNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshVoiceInputAvailability()

        override fun onLost(network: Network) = refreshVoiceInputAvailability()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshVoiceInputAvailability()
    }

    // ConnectivityManager is process-global on Android 12. Initializing it with the IME service
    // context makes the framework singleton retain that service and its complete View tree after
    // a dual-display rebind, so always obtain it from the application context.
    private val voiceConnectivityManager by lazy {
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }

    private enum class NumberRowState { Auto, ForceShow, ForceHide }

    private var numberRowState = NumberRowState.Auto

    @Keep
    private val onFloatingKeyboardUpdateListener =
        ManagedPreference.OnChangeListener<Boolean> { _, isFloating ->
            idleUi.buttonsUi.updateFloatingKeyboardState(isFloating)
            updateHideKeyboardButton()
        }

    @Keep
    private val onClipboardUpdateListener =
        ClipboardManager.OnClipboardUpdateListener {
            if (!clipboardSuggestion.getValue()) return@OnClipboardUpdateListener
            service.lifecycleScope.launch {
                if (it.text.isEmpty()) {
                    isClipboardFresh = false
                } else {
                    idleUi.clipboardUi.text.text = if (it.sensitive && clipboardMaskSensitive) {
                        ClipboardEntry.BULLET.repeat(min(42, it.text.length))
                    } else {
                        it.text.take(42)
                    }
                    isClipboardFresh = true
                    launchClipboardTimeoutJob()
                }
                evalIdleUiState()
            }
        }

    @Keep
    private val onClipboardSuggestionUpdateListener =
        ManagedPreference.OnChangeListener<Boolean> { _, it ->
            if (!it) {
                isClipboardFresh = false
                evalIdleUiState()
                clipboardTimeoutJob?.cancel()
                clipboardTimeoutJob = null
            }
        }

    @Keep
    private val onClipboardTimeoutUpdateListener =
        ManagedPreference.OnChangeListener<Int> { _, _ ->
            when (idleUi.currentState) {
                IdleUi.State.Clipboard -> {
                    // renew timeout when clipboard suggestion is present
                    launchClipboardTimeoutJob()
                }
                else -> {}
            }
        }

    private fun launchClipboardTimeoutJob() {
        clipboardTimeoutJob?.cancel()
        val timeout = clipboardItemTimeout.getValue() * 1000L
        // never transition to ClipboardTimedOut state when timeout < 0
        if (timeout < 0L) return
        clipboardTimeoutJob = service.lifecycleScope.launch {
            delay(timeout)
            isClipboardFresh = false
            clipboardTimeoutJob = null
        }
    }

    private fun evalIdleUiState(fromUser: Boolean = false) {
        val newState = when {
            numberRowState == NumberRowState.ForceShow -> IdleUi.State.NumberRow
            isClipboardFresh -> IdleUi.State.Clipboard
            isInlineSuggestionPresent -> IdleUi.State.InlineSuggestion
            isCapabilityFlagsPassword && !isKeyboardLayoutNumber && numberRowState != NumberRowState.ForceHide -> IdleUi.State.NumberRow
            /**
             * state matrix:
             *                               expandToolbarByDefault
             *                          |   \   |    true |   false
             * isToolbarManuallyToggled |  true |   Empty | Toolbar
             *                          | false | Toolbar |   Empty
             */
            expandToolbarByDefault == isToolbarManuallyToggled -> IdleUi.State.Empty
            else -> IdleUi.State.Toolbar
        }
        if (newState == idleUi.currentState) return
        idleUi.updateState(newState, fromUser)
    }

    private val hideKeyboardCallback = View.OnClickListener { view ->
        service.requestHideSelfAfterTouch(view)
    }

    private val toggleToolbarCallback = View.OnClickListener {
        when (idleUi.currentState) {
            IdleUi.State.Empty -> {
                isToolbarManuallyToggled = !expandToolbarByDefault
                evalIdleUiState(fromUser = true)
            }
            IdleUi.State.Toolbar -> {
                isToolbarManuallyToggled = expandToolbarByDefault
                evalIdleUiState(fromUser = true)
            }
            else -> {
                isToolbarManuallyToggled = !expandToolbarByDefault
                idleUi.updateState(IdleUi.State.Toolbar, fromUser = true)
            }
        }
        if (clipboardTimeoutJob != null) {
            launchClipboardTimeoutJob()
        }
    }

    private fun updateHideKeyboardButton() {
        val useVoiceInput = shouldShowVoiceInput
        idleUi.menuButton.apply {
            setOnClickListener(toggleToolbarCallback)
            swipeEnabled = false
            onGestureListener = null
        }
        idleUi.setHideKeyboardIsVoiceInput(useVoiceInput)
        idleUi.hideKeyboardButton.apply {
            setOnClickListener(if (useVoiceInput) null else hideKeyboardCallback)
            swipeEnabled = true
            onGestureListener = if (useVoiceInput) {
                voiceInputGestureCallback
            } else {
                swipeHideKeyboardCallback
            }
        }
        idleUi.setVoiceInputAvailable(!useVoiceInput || isNetworkAvailableForVoice())
        idleUi.setHideKeyboardButtonVisible(!(desktopKeyboardMode && useVoiceInput))
        updateDesktopVoiceButton(useVoiceInput)
    }

    fun setDesktopVoiceButton(button: ToolButton?) {
        desktopVoiceButton = button
        updateHideKeyboardButton()
    }

    fun setDesktopKeyboardMode(enabled: Boolean) {
        desktopKeyboardMode = enabled
        // Global mode uses a fixed dark aquarium surface independent of the selected theme.
        // Rebind every visible candidate with high-contrast colors; leaving global mode clears
        // the override so ordinary keyboards keep their configured theme unchanged.
        horizontalCandidate.setDesktopKeyboardMode(enabled)
        idleUi.setDesktopQuietMode(enabled)
        if (enabled) {
            // Build the ASR client while global mode is entering, not on the first voice DOWN.
            // This keeps Handler/OkHttp initialization out of the first interaction frame.
            asrClient.state
        }
        if (!enabled) {
            desktopVoiceTranscript.text = ""
            desktopVoiceTranscript.alpha = 0f
        }
        if (!enabled) {
            InputFeedbacks.setPhysicalKeyboardSoundSuppressed(false)
        }
        // Keep the bar's measured surface stable in desktop mode. IdleUi hides only its own
        // controls, while voice prompts reuse the same fixed slot without resizing the aquarium.
        view.visibility = View.VISIBLE
        updateHideKeyboardButton()
    }

    private fun updateDesktopVoiceButton(useVoiceInput: Boolean) {
        desktopVoiceButton?.apply {
            visibility = if (desktopKeyboardMode && useVoiceInput) View.VISIBLE else View.GONE
            if (!desktopKeyboardMode || !useVoiceInput) {
                setPhysicalKeyStyle(
                    false,
                    theme.altKeyBackgroundColor,
                    theme.keyPressHighlightColor
                )
                keyDownSoundEnabled = true
                physicalReleaseSoundEnabled = true
                physicalPressVisualEnabled = true
                gestureHapticEnabled = true
                setOnTouchListener(null)
                return@apply
            }
            setIcon(R.drawable.ic_baseline_keyboard_voice_24)
            useFullSizeIcon(DESKTOP_VOICE_ICON_SIZE_DP)
            physicalPressVisualEnabled = false
            restoreDesktopVoiceRestStyle()
            // Voice deliberately has no keycap animation, ripple, pond reaction, or sound.
            // Its gesture stream remains intact; only the microphone tint reflects press/state.
            keyDownSoundEnabled = false
            physicalReleaseSoundEnabled = false
            physicalPressVisualEnabled = false
            gestureHapticEnabled = false
            contentDescription = context.getString(R.string.start_voice_input)
            isEnabled = isNetworkAvailableForVoice()
            isClickable = true
            alpha = if (isEnabled) 1f else 0.38f
            setIconTintColor(
                if (isEnabled) Color.WHITE else DESKTOP_VOICE_DISABLED_COLOR
            )
            swipeEnabled = true
            setOnTouchListener(null)
            onGestureListener = CustomGestureView.OnGestureListener { view, event ->
                when (event.type) {
                    CustomGestureView.GestureType.Down -> {
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        setIconTintColor(DESKTOP_VOICE_ACTIVE_ICON_COLOR)
                    }
                    CustomGestureView.GestureType.Up -> {
                        view.parent.requestDisallowInterceptTouchEvent(false)
                        setIconTintColor(
                            if (asrClient.state != IflytekAsrClient.State.Idle)
                                DESKTOP_VOICE_ACTIVE_ICON_COLOR else Color.WHITE
                        )
                    }
                    CustomGestureView.GestureType.Move -> {}
                }
                voiceInputGestureCallback.onGesture(view, event)
            }
            setOnClickListener(null)
        }
    }

    private fun refreshVoiceInputAvailability() {
        if (disposed) return
        view.post {
            if (!disposed && shouldShowVoiceInput) updateHideKeyboardButton()
        }
    }

    private val swipeDownExpandCallback = CustomGestureView.OnGestureListener { view, e ->
        if (e.type == CustomGestureView.GestureType.Up && !e.cancelled && e.totalY > 0) {
            service.requestHideSelfAfterTouch(view)
            true
        } else false
    }

    // Combined gesture: determine primary direction by comparing totalX and totalY.
    // - If horizontal is dominant and left, show number row (when allowed).
    // - If vertical is dominant and down, hide keyboard.
    private val swipeHideKeyboardCallback = CustomGestureView.OnGestureListener { v, e ->
        require(v is ToolButton)
        if (e.type == CustomGestureView.GestureType.Up && e.cancelled) {
            v.iconRotation = 0f
            return@OnGestureListener true
        }
        val numberRowAvailable = isCapabilityFlagsPassword && !isKeyboardLayoutNumber
        if (numberRowAvailable) {
            val dir = if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) 1 else -1
            // `e.x` and `e.y` are relative to the view's top-left corner
            val centerX = e.x - v.width / 2f
            val centerY = e.y - v.height / 2f

            val distance = hypot(centerX, centerY)
            // the button is ↓, so apply -90 degrees offset
            var angle = atan2(-centerX, centerY) * (180f / PI.toFloat())

            when (e.type) {
                CustomGestureView.GestureType.Move -> {
                    angle = if (angle in -45f..45f) {
                        angle.coerceIn(-10f, 10f)
                    } else abs(angle).coerceIn(90f - 10f, 90f + 10f) * dir
                    v.iconRotation = angle
                }
                CustomGestureView.GestureType.Up -> {
                    val handled = when (angle) {
                        in -45f..45f if distance > v.swipeThresholdX -> {
                            service.requestHideSelfAfterTouch(v)
                            true
                        }
                        !in -45f..45f if distance > v.swipeThresholdY -> {
                            v.iconRotation = 90f * dir
                            numberRowState = NumberRowState.ForceShow
                            evalIdleUiState(fromUser = true)
                            true
                        }
                        else -> false
                    }
                    v.iconRotation = 0f
                    return@OnGestureListener handled
                }
                else -> {}
            }
        }

        if (e.type == CustomGestureView.GestureType.Up && abs(e.totalY) > abs(e.totalX) && e.totalY > 0) {
            service.requestHideSelfAfterTouch(v)
            true
        } else false
    }

    private val asrClientDelegate = lazy {
        IflytekAsrClient(
            context.applicationContext,
            onStateChanged = { state ->
                if (disposed) return@IflytekAsrClient
                InputFeedbacks.setPhysicalKeyboardSoundSuppressed(
                    desktopKeyboardMode && state != IflytekAsrClient.State.Idle
                )
                idleUi.setVoiceInputActive(state != IflytekAsrClient.State.Idle)
                desktopVoiceButton?.let { button ->
                    if (voicePressActive) {
                        button.setIconTintColor(
                            if (state != IflytekAsrClient.State.Idle) DESKTOP_VOICE_ACTIVE_ICON_COLOR
                            else Color.WHITE
                        )
                    } else {
                        button.setIconTintColor(Color.WHITE)
                    }
                    button.contentDescription = context.getString(
                        if (state != IflytekAsrClient.State.Idle) R.string.stop_voice_input
                        else R.string.start_voice_input
                    )
                }
                when (state) {
                    IflytekAsrClient.State.Starting,
                    IflytekAsrClient.State.Listening ->
                        showVoiceFeedback(context.getString(R.string.voice_input_listening))
                    IflytekAsrClient.State.Finishing ->
                        showVoiceFeedback(context.getString(R.string.voice_input_calibrating))
                    // Final/error/cancel callbacks own transcript cleanup. Hiding here races the
                    // final callback because IflytekAsrClient publishes Idle immediately before
                    // posting its corrected final text to the main thread.
                    IflytekAsrClient.State.Idle -> Unit
                }
            },
            onFinal = { text ->
                if (disposed) return@IflytekAsrClient
                showVoiceFeedback(text)
                voiceCommitJob?.cancel()
                voiceCommitJob = service.lifecycleScope.launch {
                    delay(VOICE_FINAL_PREVIEW_MS)
                    if (desktopKeyboardMode) {
                        // Global mode must not mutate the target editor while listening: some
                        // adjustPan clients reposition their whole surface on the first composing
                        // update, which looks like the keyboard zoomed. Commit corrected final once.
                        service.commitText(text)
                    } else {
                        service.commitVoiceComposing(text)
                    }
                    hideVoiceFeedback()
                }
            },
            onError = { message ->
                if (disposed) return@IflytekAsrClient
                cancelVoiceEditorPreview()
                hideVoiceFeedback()
                Toast.makeText(
                    context,
                    localizeVoiceError(message),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onPartial = { text ->
                if (disposed) return@IflytekAsrClient
                if (!desktopKeyboardMode) service.updateVoiceComposing(text)
                showVoiceFeedback(text)
            }
        )
    }
    private val asrClient by asrClientDelegate

    private fun cancelVoiceEditorPreview() {
        if (!desktopKeyboardMode) service.cancelVoiceComposing()
    }

    private fun showVoiceFeedback(text: CharSequence) {
        if (desktopKeyboardMode) {
            desktopVoiceTranscript.text = text
            // The opaque overlay is already measured above the candidate animator. Alpha is a
            // draw property only and therefore cannot start a layout/insets animation.
            desktopVoiceTranscript.alpha = 1f
        } else {
            idleUi.showVoiceTranscript(text)
        }
    }

    private fun ToolButton.restoreDesktopVoiceRestStyle() {
        physicalPressVisualEnabled = false
        setPhysicalKeyStyle(
            true,
            DESKTOP_VOICE_KEY_COLOR,
            DESKTOP_VOICE_HIGHLIGHT_COLOR,
            DESKTOP_VOICE_KEY_COLOR
        )
        keyDownSoundEnabled = false
        physicalReleaseSoundEnabled = false
        gestureHapticEnabled = false
    }

    private fun hideVoiceFeedback() {
        if (desktopKeyboardMode) {
            desktopVoiceTranscript.text = ""
            desktopVoiceTranscript.alpha = 0f
        } else {
            idleUi.hideVoiceTranscript()
        }
    }

    private var voicePressActive = false

    private val voiceInputGestureCallback = CustomGestureView.OnGestureListener { _, event ->
        Timber.i(
            "iFlytek ASR gesture=${event.type} active=$voicePressActive state=${asrClient.state}"
        )
        when (event.type) {
            CustomGestureView.GestureType.Down -> {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.voice_input_permission_required),
                        Toast.LENGTH_SHORT
                    ).show()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastVoicePermissionPromptAt > VOICE_PERMISSION_REQUEST_COOLDOWN_MS) {
                        lastVoicePermissionPromptAt = now
                        context.startActivity(
                            Intent(context, VoicePermissionActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                } else {
                    if (!isNetworkAvailableForVoice()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.voice_input_network_unavailable),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@OnGestureListener true
                    }
                    voiceCommitJob?.cancel()
                    voiceCommitJob = null
                    voiceStartJob?.cancel()
                    voiceStartJob = null
                    if (asrClient.state != IflytekAsrClient.State.Idle) {
                        asrClient.cancel()
                    }
                    // A new press supersedes an uncommitted preview from the previous session,
                    // but does not disturb the editor until the hold threshold is actually met.
                    cancelVoiceEditorPreview()
                    showVoiceFeedback(
                        context.getString(R.string.voice_input_listening)
                    )
                    voicePressActive = true
                    voiceStartJob = service.lifecycleScope.launch {
                        delay(VOICE_HOLD_START_DELAY_MS)
                        if (voicePressActive) {
                            if (!desktopKeyboardMode) service.beginVoiceComposing()
                            asrClient.start()
                        }
                        voiceStartJob = null
                    }
                }
            }
            CustomGestureView.GestureType.Up -> {
                val wasVoicePressActive = voicePressActive
                if (wasVoicePressActive) {
                    voicePressActive = false
                    voiceStartJob?.cancel()
                    voiceStartJob = null
                    when (asrClient.state) {
                        IflytekAsrClient.State.Idle -> {
                            cancelVoiceEditorPreview()
                            hideVoiceFeedback()
                        }
                        IflytekAsrClient.State.Starting -> {
                            // Authentication has not opened the microphone yet, so there is no
                            // audio to calibrate. End cleanly instead of leaving a permanent
                            // calibration label after stop() cancels a Starting session.
                            asrClient.cancel()
                            cancelVoiceEditorPreview()
                            hideVoiceFeedback()
                        }
                        IflytekAsrClient.State.Listening,
                        IflytekAsrClient.State.Finishing -> {
                            showVoiceFeedback(context.getString(R.string.voice_input_calibrating))
                            asrClient.stop()
                        }
                    }
                }
            }
            CustomGestureView.GestureType.Move -> {
                if (voicePressActive &&
                    (abs(event.totalX) > VOICE_CANCEL_MOVE_THRESHOLD ||
                        abs(event.totalY) > VOICE_CANCEL_MOVE_THRESHOLD)
                ) {
                    voicePressActive = false
                    voiceStartJob?.cancel()
                    voiceStartJob = null
                    asrClient.cancel()
                    cancelVoiceEditorPreview()
                    hideVoiceFeedback()
                }
            }
        }
        true
    }

    private fun localizeVoiceError(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("cleartext") || lower.contains("network security policy") ->
                context.getString(R.string.voice_input_error_network_policy)
            lower.contains("eai_nodata") || lower.contains("failed to connect") ||
                lower.contains("unable to resolve host") || lower.contains("timeout") ->
                context.getString(R.string.voice_input_network_unavailable)
            lower.contains("internet") && lower.contains("permission") ->
                context.getString(R.string.voice_input_error_internet_permission)
            lower.contains("iflytek_params") ->
                context.getString(R.string.voice_input_error_missing_params)
            lower.contains("microphone") || lower.contains("audio") ->
                context.getString(R.string.voice_input_error_microphone)
            else -> context.getString(R.string.voice_input_error, message)
        }
    }

    private fun isNetworkAvailableForVoice(): Boolean {
        return runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        }.getOrElse {
            Timber.w(it, "Failed to read network state for voice check")
            false
        }
    }

    private val idleUi: IdleUi by lazy {
        IdleUi(context, theme, popup, commonKeyActionListener).apply {
            menuButton.setOnClickListener(toggleToolbarCallback)
            hideKeyboardButton.apply {
                setOnClickListener(hideKeyboardCallback)
                swipeEnabled = true
                swipeThresholdY = dp(HEIGHT.toFloat())
                swipeThresholdX = swipeThresholdY
                onGestureListener = swipeHideKeyboardCallback
            }
            buttonsUi.apply {
                undoButton.setOnClickListener {
                    service.sendCombinationKeyEvents(KeyEvent.KEYCODE_Z, ctrl = true)
                }
                redoButton.setOnClickListener {
                    service.sendCombinationKeyEvents(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)
                }
                cursorMoveButton.setOnClickListener {
                    windowManager.attachWindow(TextEditingWindow())
                }
                clipboardButton.setOnClickListener {
                    windowManager.attachWindow(ClipboardWindow())
                }
                floatingKeyboardButton.setOnClickListener {
                    updateFloatingKeyboardState(service.toggleFloatingKeyboard())
                }
                desktopKeyboardButton.setOnClickListener {
                    Timber.d("Desktop keyboard button clicked")
                    windowManager.attachWindow(KeyboardWindow)
                    (windowManager.getEssentialWindow(KeyboardWindow) as KeyboardWindow)
                        .toggleDesktopKeyboard()
                }
                updateFloatingKeyboardState(prefs.keyboard.floatingKeyboard.getValue())
                moreButton.setOnClickListener {
                    windowManager.attachWindow(StatusAreaWindow())
                }
            }
            clipboardUi.suggestionView.apply {
                setOnClickListener {
                    ClipboardManager.lastEntry?.let {
                        service.commitText(it.text)
                    }
                    clipboardTimeoutJob?.cancel()
                    clipboardTimeoutJob = null
                    isClipboardFresh = false
                    evalIdleUiState()
                }
                setOnLongClickListener {
                    ClipboardManager.lastEntry?.let {
                        AppUtil.launchClipboardEdit(context, it.id, true)
                    }
                    true
                }
            }
            numberRow.apply {
                onCollapseListener = {
                    numberRowState = NumberRowState.ForceHide
                    evalIdleUiState(fromUser = true)
                }
            }
        }
    }

    private val candidateUi by lazy {
        CandidateUi(context, theme, horizontalCandidate.view).apply {
            expandButton.apply {
                swipeEnabled = true
                swipeThresholdY = dp(HEIGHT.toFloat())
                onGestureListener = swipeDownExpandCallback
            }
        }
    }

    private val titleUi by lazy {
        TitleUi(context, theme)
    }

    private val barStateMachine = KawaiiBarStateMachine.new {
        switchUiByState(it)
    }

    val expandButtonStateMachine = ExpandButtonStateMachine.new {
        when (it) {
            ClickToAttachWindow -> {
                setExpandButtonToAttach()
                setExpandButtonEnabled(true)
            }
            ClickToDetachWindow -> {
                setExpandButtonToDetach()
                setExpandButtonEnabled(true)
            }
            Hidden -> {
                setExpandButtonEnabled(false)
            }
        }
    }

    // set expand candidate button to create expand candidate
    private fun setExpandButtonToAttach() {
        candidateUi.expandButton.setOnClickListener {
            windowManager.attachWindow(
                when (expandedCandidateStyle) {
                    ExpandedCandidateStyle.Grid -> GridExpandedCandidateWindow()
                    ExpandedCandidateStyle.Flexbox -> FlexboxExpandedCandidateWindow()
                }
            )
        }
        candidateUi.expandButton.setIcon(R.drawable.ic_baseline_expand_more_24)
        candidateUi.expandButton.contentDescription = context.getString(R.string.expand_candidates_list)
    }

    // set expand candidate button to close expand candidate
    private fun setExpandButtonToDetach() {
        candidateUi.expandButton.setOnClickListener {
            windowManager.attachWindow(KeyboardWindow)
        }
        candidateUi.expandButton.setIcon(R.drawable.ic_baseline_expand_less_24)
        candidateUi.expandButton.contentDescription = context.getString(R.string.hide_candidates_list)
    }

    // should be used with setExpandButtonToAttach or setExpandButtonToDetach
    private fun setExpandButtonEnabled(enabled: Boolean) {
        candidateUi.expandButton.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    private fun switchUiByState(state: KawaiiBarStateMachine.State) {
        view.visibility = View.VISIBLE
        val index = state.ordinal
        if (barAnimator.displayedChild == index) return
        val new = barAnimator.getChildAt(index)
        if (new != titleUi.root) {
            titleUi.setReturnButtonOnClickListener { }
            titleUi.setTitle("")
            titleUi.removeExtension()
        }
        barAnimator.displayedChild = index
    }

    private val barAnimator by lazy {
        ViewAnimator(context).apply {
            add(idleUi.root, lParams(matchParent, matchParent))
            add(candidateUi.root, lParams(matchParent, matchParent))
            add(titleUi.root, lParams(matchParent, matchParent))
        }
    }

    override val view by lazy {
        FrameLayout(context).apply {
            backgroundColor =
                if (ThemeManager.prefs.keyBorder.getValue()) Color.TRANSPARENT
                else theme.barColor
            add(barAnimator, lParams(matchParent, matchParent))
            add(desktopVoiceTranscript, lParams(matchParent, matchParent))
        }
    }

    override fun onScopeSetupFinished(scope: DynamicScope) {
        ClipboardManager.lastEntry?.let {
            val now = System.currentTimeMillis()
            val clipboardTimeout = clipboardItemTimeout.getValue() * 1000L
            if (now - it.timestamp < clipboardTimeout) {
                onClipboardUpdateListener.onUpdate(it)
            }
        }
        ClipboardManager.addOnUpdateListener(onClipboardUpdateListener)
        clipboardSuggestion.registerOnChangeListener(onClipboardSuggestionUpdateListener)
        clipboardItemTimeout.registerOnChangeListener(onClipboardTimeoutUpdateListener)
        floatingKeyboard.registerOnChangeListener(onFloatingKeyboardUpdateListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                voiceConnectivityManager?.registerDefaultNetworkCallback(voiceNetworkCallback)
                voiceNetworkCallbackRegistered = voiceConnectivityManager != null
            }.onFailure {
                Timber.w(it, "Unable to observe voice network state")
            }
        }
    }

    /**
     * Release process-global observers before this IME service is destroyed.
     *
     * The Android 12 dual-display remote client destroys and recreates InputMethodService for
     * every keyboard toggle while keeping the Linux process alive. ConnectivityManager stores
     * callbacks in a process-global map, so an unregistered callback retains KawaiiBarComponent
     * and the entire keyboard View tree.
     */
    fun dispose() {
        if (disposed) return
        disposed = true

        clipboardTimeoutJob?.cancel()
        voiceCommitJob?.cancel()
        voiceStartJob?.cancel()
        clipboardTimeoutJob = null
        voiceCommitJob = null
        voiceStartJob = null

        if (asrClientDelegate.isInitialized()) {
            cancelVoiceEditorPreview()
            asrClient.cancel()
        }
        InputFeedbacks.setPhysicalKeyboardSoundSuppressed(false)
        voicePressActive = false

        ClipboardManager.removeOnUpdateListener(onClipboardUpdateListener)
        clipboardSuggestion.unregisterOnChangeListener(onClipboardSuggestionUpdateListener)
        clipboardItemTimeout.unregisterOnChangeListener(onClipboardTimeoutUpdateListener)
        floatingKeyboard.unregisterOnChangeListener(onFloatingKeyboardUpdateListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && voiceNetworkCallbackRegistered) {
            runCatching {
                voiceConnectivityManager?.unregisterNetworkCallback(voiceNetworkCallback)
            }.onFailure {
                Timber.w(it, "Unable to unregister voice network observer")
            }
            voiceNetworkCallbackRegistered = false
        }

        desktopVoiceButton?.setOnTouchListener(null)
        desktopVoiceButton?.onGestureListener = null
        desktopVoiceButton = null
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            idleUi.privateMode(info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        }
        isCapabilityFlagsPassword = toolbarNumRowOnPassword && capFlags.has(CapabilityFlag.Password)
        isInlineSuggestionPresent = false
        numberRowState = NumberRowState.Auto
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            idleUi.inlineSuggestionsBar.clear()
        }
        asrClient.cancel()
        voiceCommitJob?.cancel()
        voiceStartJob?.cancel()
        voiceCommitJob = null
        voiceStartJob = null
        cancelVoiceEditorPreview()
        hideVoiceFeedback()
        voicePressActive = false
        shouldShowVoiceInput = !capFlags.has(CapabilityFlag.Password)
        updateHideKeyboardButton()
        evalIdleUiState()
    }

    override fun onPreeditEmptyStateUpdate(empty: Boolean) {
        barStateMachine.push(PreeditUpdated, PreeditEmpty to empty)
    }

    override fun onCandidateUpdate(data: CandidateListEvent.Data) {
        barStateMachine.push(CandidatesUpdated, CandidateEmpty to data.candidates.isEmpty())
    }

    override fun onWindowAttached(window: InputWindow) {
        when (window) {
            is InputWindow.ExtendedInputWindow<*> -> {
                titleUi.setTitle(window.title)
                window.onCreateBarExtension()?.let { titleUi.addExtension(it, window.showTitle) }
                titleUi.setReturnButtonOnClickListener {
                    windowManager.attachWindow(KeyboardWindow)
                }
                barStateMachine.push(ExtendedWindowAttached)
            }
            else -> {}
        }
    }

    override fun onWindowDetached(window: InputWindow) {
        barStateMachine.push(WindowDetached)
    }

    private val suggestionSize by lazy {
        Size(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(HEIGHT))
    }

    private val directExecutor by lazy {
        Executor { it.run() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            isInlineSuggestionPresent = false
            evalIdleUiState()
            idleUi.inlineSuggestionsBar.clear()
            return true
        }
        var pinned: InlineSuggestion? = null
        val scrollable = mutableListOf<InlineSuggestion>()
        var extraPinnedCount = 0
        suggestions.forEach {
            if (it.info.isPinned) {
                if (pinned == null) {
                    pinned = it
                } else {
                    scrollable.add(extraPinnedCount++, it)
                }
            } else {
                scrollable.add(it)
            }
        }
        service.lifecycleScope.launch {
            idleUi.inlineSuggestionsBar.setPinnedView(
                pinned?.let { inflateInlineContentView(it) }
            )
        }
        service.lifecycleScope.launch {
            val views = scrollable.map { s ->
                service.lifecycleScope.async {
                    inflateInlineContentView(s)
                }
            }.awaitAll()
            idleUi.inlineSuggestionsBar.setScrollableViews(views)
        }
        isInlineSuggestionPresent = true
        evalIdleUiState()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inflateInlineContentView(suggestion: InlineSuggestion): InlineContentView? {
        return suspendCancellableCoroutine { c ->
            // callback view might be null
            suggestion.inflate(context, suggestionSize, directExecutor) { v ->
                c.resume(v)
            }
        }
    }

    companion object {
        const val HEIGHT = 48
        const val VOICE_FINAL_PREVIEW_MS = 600L
        const val VOICE_HOLD_START_DELAY_MS = 160L
        const val VOICE_PERMISSION_REQUEST_COOLDOWN_MS = 2_000L
        const val VOICE_CANCEL_MOVE_THRESHOLD = 24f
        const val DESKTOP_VOICE_KEY_COLOR = 0xFF29465C.toInt()
        const val DESKTOP_VOICE_ACTIVE_ICON_COLOR = 0xFF35E0A1.toInt()
        const val DESKTOP_VOICE_HIGHLIGHT_COLOR = 0xFF4EC7E8.toInt()
        const val DESKTOP_VOICE_DISABLED_COLOR = 0x66FFFFFF
        const val DESKTOP_VOICE_STATUS_BACKGROUND = 0xE6061827.toInt()
        const val DESKTOP_VOICE_ICON_SIZE_DP = 32
    }

    fun onKeyboardLayoutSwitched(isNumber: Boolean) {
        isKeyboardLayoutNumber = isNumber
        evalIdleUiState()
    }

}
