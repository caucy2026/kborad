/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Outline
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.broadcast.PreeditEmptyStateComponent
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.picker.emojiPicker
import org.fcitx.fcitx5.android.input.picker.emoticonPicker
import org.fcitx.fcitx5.android.input.picker.symbolPicker
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.preedit.PreeditComponent
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.unset
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class InputView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme) {

    private var disposed = false

    val reusableForImeShow: Boolean
        get() = !disposed

    private val keyBorder by ThemeManager.prefs.keyBorder

    private val customBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val desktopVoiceButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_keyboard_voice_24, theme).apply {
            visibility = GONE
            useFullSizeIcon(DESKTOP_OPERATION_ICON_SIZE_DP)
        }
    }

    private val desktopOperationArea = view(::View) {
        visibility = GONE
        setBackgroundColor(theme.barColor)
    }

    private val desktopExitButton = ToolButton(context, R.drawable.ic_dock_keyboard_24, theme).apply {
        visibility = GONE
        contentDescription = context.getString(R.string.exit_desktop_keyboard)
        useFullSizeIcon(DESKTOP_OPERATION_ICON_SIZE_DP)
        setIconTintColor(theme.altKeyTextColor)
        setOnClickListener { keyboardWindow.toggleDesktopKeyboard() }
    }

    private val desktopEnterButton =
        ToolButton(context, R.drawable.ic_baseline_keyboard_return_24, theme).apply {
            visibility = GONE
            contentDescription = context.getString(R.string.desktop_enter)
            soundEffect = InputFeedbacks.SoundEffect.Return
            useFullSizeIcon(DESKTOP_OPERATION_ICON_SIZE_DP)
            setOnClickListener { keyboardWindow.sendDesktopEnter() }
        }

    private val desktopOperationButtons = listOf(
        desktopExitButton,
        desktopVoiceButton,
        desktopEnterButton
    )

    private val placeholderOnClickListener = OnClickListener { }

    // use clickable view as padding, so MotionEvent can be split to padding view and keyboard view
    private val leftPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val rightPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val bottomPaddingSpace = view(::View) {
        // height as keyboardBottomPadding
        // bottomMargin as WindowInsets (Navigation Bar) offset
        setOnClickListener(placeholderOnClickListener)
    }
    private val floatingWindowHandle = view(::View) {
        background = service.getDrawable(R.drawable.bkg_floating_keyboard_handle)
        contentDescription = service.getString(R.string.move_floating_keyboard)
        isClickable = true
        setOnTouchListener(::onFloatingWindowHandleTouch)
    }
    private val floatingResizeButton = ToolButton(service, R.drawable.ic_resize_24, theme).apply {
        contentDescription = service.getString(R.string.resize_floating_keyboard)
        setOnClickListener { setFloatingResizeMode(!isFloatingResizeMode) }
    }
    private val floatingHideKeyboardButton = ToolButton(
        service,
        R.drawable.ic_keyboard_arrow_down_24,
        theme
    ).apply {
        useFullSizeIcon()
        contentDescription = service.getString(R.string.hide_keyboard)
        setOnClickListener { service.requestHideSelfAfterTouch(it) }
    }
    private val floatingResizeCorners = listOf(
        imageView {
            setImageResource(R.drawable.ic_resize_corner_24)
            setPadding(dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP))
            setOnTouchListener { _, event -> onFloatingResizeCornerTouch(event, -1, -1) }
        },
        imageView {
            setImageResource(R.drawable.ic_resize_corner_24)
            setPadding(dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP))
            rotation = 90f
            setOnTouchListener { _, event -> onFloatingResizeCornerTouch(event, 1, -1) }
        },
        imageView {
            setImageResource(R.drawable.ic_resize_corner_24)
            setPadding(dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP))
            rotation = 180f
            setOnTouchListener { _, event -> onFloatingResizeCornerTouch(event, 1, 1) }
        },
        imageView {
            setImageResource(R.drawable.ic_resize_corner_24)
            setPadding(dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP), dp(FLOATING_RESIZE_CORNER_PADDING_DP))
            rotation = 270f
            setOnTouchListener { _, event -> onFloatingResizeCornerTouch(event, -1, 1) }
        }
    )

    private val scope = DynamicScope()
    private val broadcaster = InputBroadcaster()
    private val popup = PopupComponent()
    private val punctuation = PunctuationComponent()
    private val returnKeyDrawable = ReturnKeyDrawableComponent()
    private val preeditEmptyState = PreeditEmptyStateComponent()
    private val preedit = PreeditComponent()
    private val commonKeyActionListener = CommonKeyActionListener()
    private val windowManager = InputWindowManager()
    private val kawaiiBar = KawaiiBarComponent()
    private val horizontalCandidate = HorizontalCandidateComponent()
    private val keyboardWindow = KeyboardWindow()
    private val symbolPicker = symbolPicker()
    private val emojiPicker = emojiPicker()
    private val emoticonPicker = emoticonPicker()

    private fun setupScope() {
        scope += this@InputView.wrapToUniqueComponent()
        scope += service.wrapToUniqueComponent()
        scope += fcitx.wrapToUniqueComponent()
        scope += theme.wrapToUniqueComponent()
        scope += themedContext.wrapToUniqueComponent()
        scope += broadcaster
        scope += popup
        scope += punctuation
        scope += returnKeyDrawable
        scope += preeditEmptyState
        scope += preedit
        scope += commonKeyActionListener
        scope += windowManager
        scope += kawaiiBar
        scope += horizontalCandidate
        broadcaster.onScopeSetupFinished(scope)
    }

    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    private val focusChangeResetKeyboard by keyboardPrefs.focusChangeResetKeyboard

    private val keyboardHeightPercent = keyboardPrefs.keyboardHeightPercent
    private val keyboardHeightPercentLandscape = keyboardPrefs.keyboardHeightPercentLandscape
    private val keyboardSidePadding = keyboardPrefs.keyboardSidePadding
    private val keyboardSidePaddingLandscape = keyboardPrefs.keyboardSidePaddingLandscape
    private val keyboardBottomPadding = keyboardPrefs.keyboardBottomPadding
    private val keyboardBottomPaddingLandscape = keyboardPrefs.keyboardBottomPaddingLandscape
    private val floatingKeyboard = keyboardPrefs.floatingKeyboard
    private val floatingKeyboardWidthPercent = keyboardPrefs.floatingKeyboardWidthPercent
    private val floatingKeyboardHeightPercent = keyboardPrefs.floatingKeyboardHeightPercent
    private val floatingKeyboardPositionX = keyboardPrefs.floatingKeyboardPositionX
    private val floatingKeyboardPositionY = keyboardPrefs.floatingKeyboardPositionY

    private val keyboardSizePrefs = listOf(
        keyboardHeightPercent,
        keyboardHeightPercentLandscape,
        keyboardSidePadding,
        keyboardSidePaddingLandscape,
        keyboardBottomPadding,
        keyboardBottomPaddingLandscape,
    )
    private var desktopKeyboardMode = false
    private var pendingDesktopKeyboardMode: Boolean? = null
    private var inputViewHierarchyReady = false
    private var desktopHeightConfigurationKey = ""
    private var lockedDesktopKeyboardHeightPx = 0
    private var lastInsetsDisplayId = android.view.Display.INVALID_DISPLAY
    private var lastNavigationBottomInset = -1
    private val deferredInsetsRefresh = Runnable {
        if (!disposed && isAttachedToWindow) requestApplyInsets()
    }

    private val floatingKeyboardOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, dp(FLOATING_KEYBOARD_RADIUS_DP).toFloat())
        }
    }

    private var floatingHandleDownX = 0f
    private var floatingHandleDownY = 0f
    private var floatingStartX = 0f
    private var floatingStartY = 0f
    private var floatingResizeStartWidth = 0
    private var floatingResizeStartHeight = 0
    private var floatingResizeDownX = 0f
    private var floatingResizeDownY = 0f
    private var isFloatingResizeMode = false

    val floatingResizeTouchInset: Int
        get() = if (isFloatingResizeMode) dp(FLOATING_RESIZE_CORNER_OFFSET_DP) else 0

    @Keep
    private val onFloatingKeyboardChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (key == floatingKeyboard.key) {
            updateFloatingKeyboardLayout()
        }
    }

    private val keyboardHeightPx: Int
        get() {
            val percent = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardHeightPercentLandscape
                else -> keyboardHeightPercent
            }.getValue()
            return resources.displayMetrics.heightPixels * percent / 100
        }

    private val keyboardSidePaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
                else -> keyboardSidePadding
            }.getValue()
            return dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                else -> keyboardBottomPadding
            }.getValue()
            return dp(value)
        }

    /**
     * Keep near-square desktop keys while reserving a real touchpad above row one. Global mode is
     * intentionally immersive; ordinary text/number keyboards continue to use their own height.
     */
    private val desktopKeyboardHeightPx: Int
        get() {
            val configuration = resources.configuration
            val currentDisplay = display ?: service.display
            val displayMode = currentDisplay?.mode
            val physicalDisplayHeight = if (displayMode == null) {
                resources.displayMetrics.heightPixels
            } else {
                when (currentDisplay.rotation) {
                    Surface.ROTATION_90, Surface.ROTATION_270 -> displayMode.physicalWidth
                    else -> displayMode.physicalHeight
                }
            }
            val configurationKey = buildString {
                append(configuration.orientation)
                append(':')
                append(configuration.screenWidthDp)
                append(':')
                append(configuration.screenHeightDp)
                append(':')
                append(configuration.densityDpi)
                append(':')
                append(currentDisplay?.displayId)
                append(':')
                append(physicalDisplayHeight)
            }
            if (configurationKey == desktopHeightConfigurationKey &&
                lockedDesktopKeyboardHeightPx > 0
            ) {
                return lockedDesktopKeyboardHeightPx
            }
            val displayWidth = resources.displayMetrics.widthPixels
            // IME resource metrics exclude the upper system/app band on V900. Desktop mode is
            // intentionally full-screen up to the current Display's navigation inset, so size
            // from the rotated physical panel. Ordinary keyboards continue using resource metrics.
            val displayHeight = physicalDisplayHeight
                .coerceAtLeast(resources.displayMetrics.heightPixels)
            val contentWidth = displayWidth - dp(DESKTOP_SIDE_PADDING_DP * 2)
            val rowsHeight = contentWidth * DESKTOP_ROW_COUNT / DESKTOP_LAYOUT_WIDTH_UNITS
            // Desktop mode overlays KawaiiBar above row one instead of reserving a second strip
            // at the top. Its former 48dp slot is now part of the mouse surface, so do not count
            // the bar twice when deriving the outer keyboard height.
            val chromeHeight = dp(
                DESKTOP_OPERATION_HEIGHT_DP + DESKTOP_VERTICAL_INSET_DP +
                        DESKTOP_TOUCHPAD_HEIGHT_DP
            )
            val minimum = displayHeight * DESKTOP_MIN_HEIGHT_PERCENT / 100
            // Keep a small strip of the controlled desktop visible above the global keyboard.
            // The matching reduction in touchpad + operation chrome below keeps all six
            // physical-key rows at exactly their current height.
            val maximum = displayHeight - dp(DESKTOP_TOP_REVEAL_DP)
            lockedDesktopKeyboardHeightPx = (rowsHeight + chromeHeight)
                .roundToInt()
                .coerceIn(minimum, maximum)
            desktopHeightConfigurationKey = configurationKey
            return lockedDesktopKeyboardHeightPx
        }

    @Keep
    private val onKeyboardSizeChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (keyboardSizePrefs.any { it.key == key }) {
            updateKeyboardSize()
        }
    }

    val keyboardView: View

    private fun bringDesktopButtonsToFront() {
        desktopOperationButtons.forEach(View::bringToFront)
    }

    private fun updateDesktopCompositionPosition() {
        if (!desktopKeyboardMode) return
        windowManager.view.post {
            if (!desktopKeyboardMode) return@post
            val firstRowTop = keyboardWindow.desktopFirstRowTopOnScreen() ?: return@post
            val parent = preedit.ui.root.parent as? View ?: return@post
            val parentLocation = IntArray(2)
            parent.getLocationOnScreen(parentLocation)
            val candidateTopOnScreen = firstRowTop - kawaiiBar.view.height
            val preeditTop = (candidateTopOnScreen - parentLocation[1] -
                    preedit.ui.root.measuredHeight - dp(DESKTOP_PREEDIT_GAP_DP))
                .coerceAtLeast(0)
            preedit.ui.root.updateLayoutParams<LayoutParams> {
                topMargin = preeditTop
            }
            val barParent = kawaiiBar.view.parent as? View ?: return@post
            val barParentLocation = IntArray(2)
            barParent.getLocationOnScreen(barParentLocation)
            kawaiiBar.view.translationY = (
                    candidateTopOnScreen - barParentLocation[1] - kawaiiBar.view.top
                    ).toFloat()
        }
    }

    init {
        // MUST call before any operation
        setupScope()

        // restore punctuation mapping in case of InputView recreation
        service.postFcitxJob {
            punctuation.updatePunctuationMapping(statusAreaActionsCached)
        }

        // make sure KeyboardWindow's view has been created before it receives any broadcast
        windowManager.addEssentialWindow(keyboardWindow, createView = true)
        windowManager.addEssentialWindow(symbolPicker)
        windowManager.addEssentialWindow(emojiPicker)
        windowManager.addEssentialWindow(emoticonPicker)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        broadcaster.onImeUpdate(fcitx.runImmediately { inputMethodEntryCached })

        customBackground.imageDrawable = theme.backgroundDrawable(keyBorder)

        keyboardView = constraintLayout {
            // allow MotionEvent to be delivered to keyboard while pressing on padding views.
            // although it should be default for apps targeting Honeycomb (3.0, API 11) and higher,
            // but it's not the case on some devices ... just set it here
            isMotionEventSplittingEnabled = true
            outlineProvider = floatingKeyboardOutline
            add(customBackground, lParams {
                centerVertically()
                centerHorizontally()
            })
            add(floatingWindowHandle, lParams(dp(FLOATING_HANDLE_SIZE_DP), dp(FLOATING_HANDLE_HEIGHT_DP)) {
                bottomOfParent()
                centerHorizontally()
            })
            add(floatingResizeButton, lParams(dp(FLOATING_HANDLE_SIZE_DP), dp(FLOATING_HANDLE_HEIGHT_DP)) {
                bottomOfParent()
                startToEndOf(floatingWindowHandle)
            })
            add(kawaiiBar.view, lParams(matchParent, dp(KawaiiBarComponent.HEIGHT)) {
                topOfParent()
                centerHorizontally()
            })
            add(leftPaddingSpace, lParams {
                below(kawaiiBar.view)
                startOfParent()
                bottomOfParent()
            })
            add(rightPaddingSpace, lParams {
                below(kawaiiBar.view)
                endOfParent()
                bottomOfParent()
            })
            add(windowManager.view, lParams {
                below(kawaiiBar.view)
                above(bottomPaddingSpace)
                /**
                 * set start and end constrain in [updateKeyboardSize]
                 */
            })
            add(desktopOperationArea, lParams(matchParent, dp(DESKTOP_OPERATION_HEIGHT_DP)) {
                bottomOfParent()
                centerHorizontally()
            })
            add(desktopExitButton, lParams(0, dp(DESKTOP_OPERATION_BUTTON_SIZE_DP)) {
                startOfParent()
                endToStartOf(desktopVoiceButton)
                bottomOfParent()
                horizontalChainStyle = LayoutParams.CHAIN_SPREAD
                horizontalWeight = 1f
                marginStart = dp(DESKTOP_OPERATION_BUTTON_GAP_DP)
                marginEnd = dp(DESKTOP_OPERATION_BUTTON_GAP_DP)
                bottomMargin = dp(DESKTOP_OPERATION_BUTTON_VERTICAL_MARGIN_DP)
            })
            add(desktopVoiceButton, lParams(0, dp(DESKTOP_OPERATION_BUTTON_SIZE_DP)) {
                startToEndOf(desktopExitButton)
                endToStartOf(desktopEnterButton)
                bottomOfParent()
                horizontalWeight = 1f
                marginStart = dp(DESKTOP_OPERATION_BUTTON_GAP_DP)
                marginEnd = dp(DESKTOP_OPERATION_BUTTON_GAP_DP)
                bottomMargin = dp(DESKTOP_OPERATION_BUTTON_VERTICAL_MARGIN_DP)
            })
            add(desktopEnterButton, lParams(0, dp(DESKTOP_OPERATION_BUTTON_SIZE_DP)) {
                startToEndOf(desktopVoiceButton)
                endOfParent()
                bottomOfParent()
                horizontalWeight = 1f
                marginStart = dp(DESKTOP_OPERATION_BUTTON_GAP_DP)
                marginEnd = dp(DESKTOP_OPERATION_BUTTON_GAP_DP)
                bottomMargin = dp(DESKTOP_OPERATION_BUTTON_VERTICAL_MARGIN_DP)
            })
            add(bottomPaddingSpace, lParams {
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
                bottomOfParent()
            })
        }

        updateKeyboardSize()
        desktopOperationArea.setOnTouchListener { _, event ->
            keyboardWindow.onDesktopPondTouch(event)
        }
        kawaiiBar.setDesktopVoiceButton(desktopVoiceButton)
        windowManager.view.addOnLayoutChangeListener { _, left, top, right, bottom,
                                                       oldLeft, oldTop, oldRight, oldBottom ->
            if (desktopKeyboardMode &&
                (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
            ) {
                refreshDesktopKeyboardHeight()
            }
            updateDesktopCompositionPosition()
        }
        preedit.ui.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateDesktopCompositionPosition()
        }

        add(preedit.ui.root, lParams(matchParent, wrapContent) {
            above(keyboardView)
            centerHorizontally()
        })
        add(keyboardView, lParams(matchParent, wrapContent) {
            centerHorizontally()
            bottomOfParent()
        })
        add(floatingResizeCorners[0], lParams(dp(FLOATING_RESIZE_CORNER_SIZE_DP), dp(FLOATING_RESIZE_CORNER_SIZE_DP)) {
            startToStart = keyboardView.id
            topToTop = keyboardView.id
            marginStart = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
            topMargin = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
        })
        add(floatingResizeCorners[1], lParams(dp(FLOATING_RESIZE_CORNER_SIZE_DP), dp(FLOATING_RESIZE_CORNER_SIZE_DP)) {
            endToEnd = keyboardView.id
            topToTop = keyboardView.id
            marginEnd = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
            topMargin = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
        })
        add(floatingResizeCorners[2], lParams(dp(FLOATING_RESIZE_CORNER_SIZE_DP), dp(FLOATING_RESIZE_CORNER_SIZE_DP)) {
            endToEnd = keyboardView.id
            bottomToBottom = keyboardView.id
            marginEnd = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
            bottomMargin = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
        })
        add(floatingResizeCorners[3], lParams(dp(FLOATING_RESIZE_CORNER_SIZE_DP), dp(FLOATING_RESIZE_CORNER_SIZE_DP)) {
            startToStart = keyboardView.id
            bottomToBottom = keyboardView.id
            marginStart = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
            bottomMargin = -dp(FLOATING_RESIZE_CORNER_OFFSET_DP)
        })
        add(floatingHideKeyboardButton, lParams(dp(FLOATING_HIDE_BUTTON_SIZE_DP), dp(FLOATING_HIDE_BUTTON_SIZE_DP)) {
            startOfParent()
            bottomOfParent()
        })
        add(popup.root, lParams(matchParent, matchParent) {
            centerVertically()
            centerHorizontally()
        })

        keyboardPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
        keyboardPrefs.registerOnChangeListener(onFloatingKeyboardChangeListener)
        updateFloatingKeyboardLayout()
        inputViewHierarchyReady = true
        pendingDesktopKeyboardMode?.let { enabled ->
            pendingDesktopKeyboardMode = null
            setDesktopKeyboardMode(enabled)
        }
    }

    fun toggleFloatingKeyboard(): Boolean {
        val isFloating = !floatingKeyboard.getValue()
        floatingKeyboard.setValue(isFloating)
        return isFloating
    }

    fun setDesktopKeyboardMode(enabled: Boolean) {
        if (!inputViewHierarchyReady) {
            pendingDesktopKeyboardMode = enabled
            return
        }
        if (desktopKeyboardMode == enabled) {
            if (enabled) refreshDesktopKeyboardHeight()
            return
        }
        desktopKeyboardMode = enabled
        kawaiiBar.setDesktopKeyboardMode(enabled)
        if (enabled) {
            keyboardWindow.setDesktopSystemBottomInset(lastNavigationBottomInset.coerceAtLeast(0))
        }
        desktopOperationArea.visibility = if (enabled) VISIBLE else GONE
        desktopOperationButtons.filter { it !== desktopVoiceButton }.forEach {
            it.visibility = if (enabled) VISIBLE else GONE
        }
        desktopOperationButtons.forEach { button ->
            val isEnter = button === desktopEnterButton
            button.setPhysicalKeyStyle(
                enabled,
                if (enabled && isEnter) DESKTOP_ENTER_PRESSED_COLOR
                else if (enabled) DESKTOP_KEY_COLOR else theme.altKeyBackgroundColor,
                if (enabled) DESKTOP_KEY_HIGHLIGHT_COLOR else theme.keyPressHighlightColor,
                restColor = if (!enabled) null
                else if (isEnter) DESKTOP_ENTER_KEY_COLOR else DESKTOP_KEY_COLOR
            )
            button.physicalReleaseSoundEnabled = !enabled
            button.setIconTintColor(if (enabled) Color.WHITE else theme.altKeyTextColor)
            if (button === desktopEnterButton) {
                // The remote key path already provides the definitive Enter feedback. Playing a
                // second local key-down sample here made one press sound like two submissions.
                button.keyDownSoundEnabled = !enabled
                button.physicalReleaseSoundEnabled = false
            }
            if (button === desktopVoiceButton && enabled) {
                // Voice retains its hold-to-talk lifecycle but intentionally emits no key sound,
                // release sound, haptic, keycap animation, ripple, or aquarium reaction.
                button.keyDownSoundEnabled = false
                button.physicalReleaseSoundEnabled = false
                button.gestureHapticEnabled = false
            }
        }
        if (enabled) {
            bringDesktopButtonsToFront()
            kawaiiBar.view.bringToFront()
            // Explicitly hide floating‑keyboard controls so they never appear
            // alongside the desktop operation bar.
            floatingHideKeyboardButton.visibility = GONE
            floatingWindowHandle.visibility = GONE
            floatingResizeButton.visibility = GONE
        } else {
            kawaiiBar.view.translationY = 0f
            updateFloatingKeyboardLayout()
        }
        kawaiiBar.view.setBackgroundColor(
            if (enabled) DESKTOP_SURFACE_COLOR
            else if (keyBorder) Color.TRANSPARENT else theme.barColor
        )
        desktopOperationArea.setBackgroundColor(
            if (enabled) DESKTOP_TOOLBAR_COLOR else Color.TRANSPARENT
        )
        keyboardView.setBackgroundColor(if (enabled) DESKTOP_SURFACE_COLOR else Color.TRANSPARENT)
        customBackground.imageDrawable = if (enabled) {
            ColorDrawable(DESKTOP_SURFACE_COLOR)
        } else {
            theme.backgroundDrawable(keyBorder)
        }
        keyboardView.updateLayoutParams<LayoutParams> {
            // Keep the desktop surface at the full physical-panel height. The navigation inset
            // is already applied inside this view by bottomPaddingSpace, and DesktopKeyboard
            // yields the same amount from its flexible touch header. Subtracting it here as well
            // shifted the whole keyboard down by one system-bar height and squeezed all six key
            // rows (most visibly A/B/C/D) even though the upper screen band was still unused.
            height = if (enabled) desktopKeyboardHeightPx else wrapContent
            if (enabled) {
                topToBottom = unset
            } else {
                topToBottom = unset
            }
        }
        preedit.ui.root.updateLayoutParams<LayoutParams> {
            if (enabled) {
                topOfParent()
                bottomToTop = unset
                topMargin = dp(KawaiiBarComponent.HEIGHT + 4)
            } else {
                topToTop = unset
                bottomToTop = keyboardView.id
                topMargin = 0
            }
        }
        if (enabled) preedit.ui.root.bringToFront()
        updateKeyboardSize()
        updateDesktopCompositionPosition()
    }

    private fun refreshDesktopKeyboardHeight() {
        if (!desktopKeyboardMode) return
        val targetHeight = desktopKeyboardHeightPx
        if (keyboardView.layoutParams.height == targetHeight) return
        keyboardView.updateLayoutParams<LayoutParams> {
            height = targetHeight
        }
    }

    private fun updateFloatingKeyboardLayout() {
        val isFloating = floatingKeyboard.getValue()
        val width = if (isFloating) {
            resources.displayMetrics.widthPixels * floatingKeyboardWidthPercent.getValue()
                .coerceIn(FLOATING_KEYBOARD_MIN_WIDTH_PERCENT, FLOATING_KEYBOARD_MAX_WIDTH_PERCENT) / 100
        } else {
            matchParent
        }
        val translationY = if (isFloating) -dp(FLOATING_KEYBOARD_BOTTOM_OFFSET_DP).toFloat() else 0f
        keyboardView.updateLayoutParams<LayoutParams> {
            this.width = width
        }
        preedit.ui.root.updateLayoutParams<LayoutParams> {
            this.width = width
        }
        floatingWindowHandle.visibility = if (isFloating) VISIBLE else GONE
        floatingResizeButton.visibility = if (isFloating) VISIBLE else GONE
        floatingHideKeyboardButton.visibility = if (isFloating) VISIBLE else GONE
        if (isFloating) {
            floatingWindowHandle.bringToFront()
            floatingResizeButton.bringToFront()
            floatingHideKeyboardButton.bringToFront()
            floatingResizeCorners.forEach { it.bringToFront() }
        }
        setFloatingResizeMode(isFloating && isFloatingResizeMode)
        keyboardWindow.setFloatingMode(isFloating)
        keyboardView.clipToOutline = isFloating
        keyboardView.elevation = if (isFloating) dp(FLOATING_KEYBOARD_ELEVATION_DP).toFloat() else 0f
        updateKeyboardSize()
        keyboardView.post {
            // The mode may change again before this posted layout callback runs (for example
            // floating -> docked -> desktop in the same IME transition). Never apply floating
            // bounds to the new full-screen geometry.
            if (isFloating && floatingKeyboard.getValue() && !desktopKeyboardMode) {
                restoreFloatingKeyboardPosition()
            } else {
                resetFloatingKeyboardPosition(translationY)
            }
        }
    }

    private fun onFloatingWindowHandleTouch(view: View, event: MotionEvent): Boolean {
        if (!floatingKeyboard.getValue()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                floatingHandleDownX = event.rawX
                floatingHandleDownY = event.rawY
                floatingStartX = keyboardView.translationX
                floatingStartY = keyboardView.translationY
                view.parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFloatingKeyboardPosition(
                    floatingStartX + event.rawX - floatingHandleDownX,
                    floatingStartY + event.rawY - floatingHandleDownY
                )
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.parent.requestDisallowInterceptTouchEvent(false)
                if (keyboardView.translationY >= -dp(FLOATING_KEYBOARD_DOCK_THRESHOLD_DP)) {
                    floatingKeyboard.setValue(false)
                } else {
                    saveFloatingKeyboardPosition()
                }
                return true
            }
        }
        return false
    }

    private fun setFloatingResizeMode(enabled: Boolean) {
        isFloatingResizeMode = enabled
        val visibility = if (enabled && floatingKeyboard.getValue()) VISIBLE else GONE
        floatingResizeCorners.forEach { it.visibility = visibility }
        if (enabled) {
            keyboardView.post {
                if (floatingKeyboard.getValue() && !desktopKeyboardMode) {
                    updateFloatingKeyboardPosition(
                        keyboardView.translationX,
                        keyboardView.translationY
                    )
                }
            }
        }
    }

    private fun onFloatingResizeCornerTouch(event: MotionEvent, horizontal: Int, vertical: Int): Boolean {
        if (!isFloatingResizeMode || !floatingKeyboard.getValue()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                floatingResizeDownX = event.rawX
                floatingResizeDownY = event.rawY
                floatingResizeStartWidth = keyboardView.width
                floatingResizeStartHeight = keyboardView.height
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - floatingResizeDownX) * horizontal
                val deltaY = (event.rawY - floatingResizeDownY) * vertical
                resizeFloatingKeyboard(floatingResizeStartWidth + deltaX, floatingResizeStartHeight + deltaY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                saveFloatingKeyboardSize()
                setFloatingResizeMode(false)
                return true
            }
        }
        return false
    }

    private fun resizeFloatingKeyboard(requestedWidth: Float, requestedHeight: Float) {
        val width = requestedWidth.roundToInt().coerceIn(
            resources.displayMetrics.widthPixels * FLOATING_KEYBOARD_MIN_WIDTH_PERCENT / 100,
            resources.displayMetrics.widthPixels * FLOATING_KEYBOARD_MAX_WIDTH_PERCENT / 100
        )
        val keyboardHeight = requestedHeight.roundToInt().coerceIn(
            keyboardHeightPx * FLOATING_KEYBOARD_MIN_HEIGHT_PERCENT / 100,
            keyboardHeightPx * FLOATING_KEYBOARD_MAX_HEIGHT_PERCENT / 100
        )
        keyboardView.updateLayoutParams<LayoutParams> { this.width = width }
        preedit.ui.root.updateLayoutParams<LayoutParams> { this.width = width }
        windowManager.view.updateLayoutParams { height = keyboardHeight - dp(FLOATING_HANDLE_HEIGHT_DP) }
        updateFloatingKeyboardPosition(keyboardView.translationX, keyboardView.translationY)
    }

    private fun saveFloatingKeyboardSize() {
        floatingKeyboardWidthPercent.setValue(
            (keyboardView.width * 100 / resources.displayMetrics.widthPixels)
                .coerceIn(FLOATING_KEYBOARD_MIN_WIDTH_PERCENT, FLOATING_KEYBOARD_MAX_WIDTH_PERCENT)
        )
        floatingKeyboardHeightPercent.setValue(
            (windowManager.view.height * 100 / keyboardHeightPx)
                .coerceIn(FLOATING_KEYBOARD_MIN_HEIGHT_PERCENT, FLOATING_KEYBOARD_MAX_HEIGHT_PERCENT)
        )
    }

    private fun updateFloatingKeyboardPosition(x: Float, y: Float) {
        if (!floatingKeyboard.getValue() || desktopKeyboardMode) {
            resetFloatingKeyboardPosition()
            return
        }
        val panelLeft = (width - keyboardView.width) / 2f
        val resizeCornerInset = if (isFloatingResizeMode) dp(FLOATING_RESIZE_CORNER_OFFSET_DP).toFloat() else 0f
        val minX = -panelLeft + resizeCornerInset
        val maxX = width - panelLeft - keyboardView.width - resizeCornerInset
        val minY = -(height - keyboardView.height).toFloat() + resizeCornerInset
        val maxY = -resizeCornerInset
        // During a relayout the parent and keyboard can briefly report incompatible sizes.
        // Center that axis for the frame instead of passing an empty range to coerceIn().
        keyboardView.translationX = clampToLayoutRange(x, minX, maxX)
        keyboardView.translationY = clampToLayoutRange(y, minY, maxY)
        preedit.ui.root.translationX = keyboardView.translationX
        preedit.ui.root.translationY = keyboardView.translationY
        floatingResizeCorners.forEach {
            it.translationX = keyboardView.translationX
            it.translationY = keyboardView.translationY
        }
        updateFloatingHideKeyboardButtonPosition()
    }

    private fun resetFloatingKeyboardPosition(y: Float = 0f) {
        keyboardView.translationX = 0f
        keyboardView.translationY = y
        preedit.ui.root.translationX = 0f
        preedit.ui.root.translationY = y
        floatingResizeCorners.forEach {
            it.translationX = 0f
            it.translationY = y
        }
    }

    private fun clampToLayoutRange(value: Float, min: Float, max: Float): Float =
        if (min <= max) value.coerceIn(min, max) else (min + max) / 2f

    private fun updateFloatingHideKeyboardButtonPosition() {
        if (!floatingKeyboard.getValue()) return
        floatingHideKeyboardButton.post {
            floatingHideKeyboardButton.x = keyboardView.x
            floatingHideKeyboardButton.y = keyboardView.y + keyboardView.height -
                    floatingHideKeyboardButton.height + dp(FLOATING_HIDE_BUTTON_OFFSET_DP)
        }
    }

    private fun restoreFloatingKeyboardPosition() {
        if (!floatingKeyboard.getValue() || desktopKeyboardMode) {
            resetFloatingKeyboardPosition()
            return
        }
        val panelLeft = (width - keyboardView.width) / 2f
        val minX = -panelLeft
        val maxX = panelLeft
        val minY = -(height - keyboardView.height).toFloat()
        val maxY = 0f
        val x = lerp(minX, maxX, floatingKeyboardPositionX.getValue() / FLOATING_POSITION_SCALE.toFloat())
        val y = lerp(minY, maxY, floatingKeyboardPositionY.getValue() / FLOATING_POSITION_SCALE.toFloat())
        updateFloatingKeyboardPosition(x, y)
    }

    private fun saveFloatingKeyboardPosition() {
        if (!floatingKeyboard.getValue() || desktopKeyboardMode) return
        val panelLeft = (width - keyboardView.width) / 2f
        val minX = -panelLeft
        val maxX = panelLeft
        val minY = -(height - keyboardView.height).toFloat()
        val maxY = 0f
        floatingKeyboardPositionX.setValue(normalize(keyboardView.translationX, minX, maxX))
        floatingKeyboardPositionY.setValue(normalize(keyboardView.translationY, minY, maxY))
    }

    private fun normalize(value: Float, min: Float, max: Float): Int {
        if (max <= min) return FLOATING_POSITION_SCALE / 2
        return ((value - min) / (max - min) * FLOATING_POSITION_SCALE)
            .roundToInt()
            .coerceIn(0, FLOATING_POSITION_SCALE)
    }

    private fun lerp(min: Float, max: Float, fraction: Float): Float = min + (max - min) * fraction

    private fun updateKeyboardSize() {
        windowManager.view.updateLayoutParams {
            height = if (desktopKeyboardMode) {
                0
            } else if (floatingKeyboard.getValue()) {
                keyboardHeightPx * floatingKeyboardHeightPercent.getValue()
                    .coerceIn(FLOATING_KEYBOARD_MIN_HEIGHT_PERCENT, FLOATING_KEYBOARD_MAX_HEIGHT_PERCENT) / 100
            } else {
                keyboardHeightPx
            }
        }
        bottomPaddingSpace.updateLayoutParams {
            height = if (desktopKeyboardMode || floatingKeyboard.getValue()) 0 else keyboardBottomPaddingPx
        }
        windowManager.view.updateLayoutParams<LayoutParams> {
            if (desktopKeyboardMode) {
                // KawaiiBar is translated to the bottom of the desktop header. Starting the
                // keyboard window below its original top slot left another 48dp dead band above
                // the touchpad. Reclaim that band only in desktop mode.
                topToBottom = unset
                topOfParent()
                bottomToTop = unset
                // The aquarium is owned by DesktopKeyboard. Let that single surface continue
                // behind the operation buttons so koi can swim through the complete pond. The
                // desktop keyboard reserves this button height internally for its key rows.
                above(bottomPaddingSpace)
            } else if (floatingKeyboard.getValue()) {
                topToTop = unset
                below(kawaiiBar.view)
                bottomToTop = unset
                above(floatingWindowHandle)
            } else {
                topToTop = unset
                below(kawaiiBar.view)
                bottomToTop = unset
                above(bottomPaddingSpace)
            }
        }
        // These controls are siblings of the keyboard window. Moving only windowManager.view
        // would leave the visible HOME/BACK/mouse/voice/hide controls inside the navigation bar's
        // touch-owned region, so all desktop bottom overlays share the same safe-area anchor.
        (listOf(desktopOperationArea) + desktopOperationButtons).forEach { view ->
            view.updateLayoutParams<LayoutParams> {
                if (desktopKeyboardMode) {
                    bottomToBottom = unset
                    above(bottomPaddingSpace)
                } else {
                    bottomToTop = unset
                    bottomOfParent()
                }
            }
        }
        val sidePadding = if (desktopKeyboardMode) {
            dp(DESKTOP_SIDE_PADDING_DP)
        } else {
            keyboardSidePaddingPx
        }
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = GONE
            rightPaddingSpace.visibility = GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = VISIBLE
            rightPaddingSpace.visibility = VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(sidePadding, 0, sidePadding, 0)
        kawaiiBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val displayId = display?.displayId ?: android.view.Display.INVALID_DISPLAY
        val bottomInset = getNavBarBottomInset(insets)
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            // Assign the current Display's value; never add to the previous margin. This makes
            // repeated D0/D2 migration and rotation idempotent.
            bottomMargin = bottomInset
        }
        if (displayId != lastInsetsDisplayId || bottomInset != lastNavigationBottomInset) {
            Log.i(
                INSETS_LOG_TAG,
                "apply display=$displayId navigationBottom=$bottomInset desktop=$desktopKeyboardMode"
            )
            lastInsetsDisplayId = displayId
            lastNavigationBottomInset = bottomInset
            if (desktopKeyboardMode) {
                // Preserve key/button height. Only the intentionally oversized desktop touchpad
                // yields the pixels occupied by this Display's system navigation bar.
                keyboardWindow.setDesktopSystemBottomInset(bottomInset)
                refreshDesktopKeyboardHeight()
            }
        }
        return insets
    }

    /**
     * Refresh insets from the ViewRoot currently hosting this reusable IME hierarchy. Android 12
     * may finish cross-display reparenting one frame after the service callback, so issue one
     * immediate request plus one coalesced posted request. Older queued requests are removed.
     */
    fun requestCurrentDisplayInsets(reason: String) {
        if (disposed) return
        removeCallbacks(deferredInsetsRefresh)
        val displayId = display?.displayId ?: android.view.Display.INVALID_DISPLAY
        Log.i(
            INSETS_LOG_TAG,
            "request display=$displayId reason=$reason attached=$isAttachedToWindow"
        )
        if (isAttachedToWindow) requestApplyInsets()
        post(deferredInsetsRefresh)
    }

    /**
     * called when [InputView] is about to show, or restart
     */
    fun startInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean = false) {
        broadcaster.onStartInput(info, capFlags)
        returnKeyDrawable.updateDrawableOnEditorInfo(info)
        if (focusChangeResetKeyboard || !restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun onStartHandleFcitxEvent() {
        val inputPanelData = fcitx.runImmediately { inputPanelCached }
        val inputMethodEntry = fcitx.runImmediately { inputMethodEntryCached }
        val statusAreaActions = fcitx.runImmediately { statusAreaActionsCached }
        arrayOf(
            FcitxEvent.InputPanelEvent(inputPanelData),
            FcitxEvent.IMChangeEvent(inputMethodEntry),
            FcitxEvent.StatusAreaEvent(
                FcitxEvent.StatusAreaEvent.Data(statusAreaActions, inputMethodEntry)
            )
        ).forEach { handleFcitxEvent(it) }
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.CandidateListEvent -> {
                broadcaster.onCandidateUpdate(it.data)
            }
            is FcitxEvent.ClientPreeditEvent -> {
                preeditEmptyState.updatePreeditEmptyState(clientPreedit = it.data)
                broadcaster.onClientPreeditUpdate(it.data)
            }
            is FcitxEvent.InputPanelEvent -> {
                preeditEmptyState.updatePreeditEmptyState(preedit = it.data.preedit)
                broadcaster.onInputPanelUpdate(it.data)
            }
            is FcitxEvent.IMChangeEvent -> {
                broadcaster.onImeUpdate(it.data)
            }
            is FcitxEvent.StatusAreaEvent -> {
                punctuation.updatePunctuationMapping(it.data.actions)
                broadcaster.onStatusAreaUpdate(it.data.actions)
            }
            else -> {}
        }
    }

    fun updateSelection(start: Int, end: Int) {
        broadcaster.onSelectionUpdate(start, end)
    }

    fun onImeWindowShown() {
        keyboardWindow.onImeWindowShown()
    }

    fun onImeWindowHidden() {
        keyboardWindow.onImeWindowHidden()
    }

    /**
     * Release every listener and render resource that can retain this complete keyboard tree.
     * Android 12 dual-display builds do not consistently detach the IME view before rebinding
     * the service, so cleanup must not depend only on onDetachedFromWindow().
     */
    fun dispose() {
        if (disposed) return
        disposed = true
        removeCallbacks(deferredInsetsRefresh)
        handleEvents = false
        onImeWindowHidden()
        service.cancelPendingTouchHideRequest()
        kawaiiBar.dispose()
        keyboardWindow.dispose()
        keyboardPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        keyboardPrefs.unregisterOnChangeListener(onFloatingKeyboardChangeListener)
        scope.clear()
        // Some Android 12 vendor builds retain the obsolete IME root View after service
        // destruction. Sever the root-to-children graph so that retention costs one empty shell
        // instead of the complete keyboard, all key ConstraintLayouts and aquarium surface.
        removeAllViews()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        return kawaiiBar.handleInlineSuggestions(response)
    }

    override fun onDetachedFromWindow() {
        dispose()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FLOATING_KEYBOARD_MIN_WIDTH_PERCENT = 35
        const val FLOATING_KEYBOARD_MAX_WIDTH_PERCENT = 65
        const val FLOATING_KEYBOARD_MIN_HEIGHT_PERCENT = 70
        const val FLOATING_KEYBOARD_MAX_HEIGHT_PERCENT = 110
        const val FLOATING_POSITION_SCALE = 1000
        const val FLOATING_KEYBOARD_BOTTOM_OFFSET_DP = 0
        const val FLOATING_KEYBOARD_ELEVATION_DP = 8
        const val FLOATING_HANDLE_SIZE_DP = 112
        const val FLOATING_HANDLE_HEIGHT_DP = 48
        const val FLOATING_HIDE_BUTTON_SIZE_DP = 48
        const val FLOATING_HIDE_BUTTON_OFFSET_DP = 12
        const val DESKTOP_OPERATION_HEIGHT_DP = 56
        const val DESKTOP_OPERATION_BUTTON_SIZE_DP = 56
        const val DESKTOP_OPERATION_ICON_SIZE_DP = 32
        const val DESKTOP_OPERATION_BUTTON_GAP_DP = 5
        const val DESKTOP_OPERATION_BUTTON_VERTICAL_MARGIN_DP = 0
        const val DESKTOP_PREEDIT_GAP_DP = 0
        const val DESKTOP_SIDE_PADDING_DP = 0
        const val DESKTOP_VERTICAL_INSET_DP = 20
        // Preserve the established input/candidate buffer above F1. Only reduce the actual
        // pointer header by 48dp so the remote desktop remains visible above the keyboard.
        const val DESKTOP_TOUCHPAD_HEIGHT_DP = 272
        const val DESKTOP_TOP_REVEAL_DP = 56
        const val DESKTOP_ROW_COUNT = 6f
        const val DESKTOP_LAYOUT_WIDTH_UNITS = 15f
        const val DESKTOP_MIN_HEIGHT_PERCENT = 35
        const val DESKTOP_SURFACE_COLOR = 0xFF061827.toInt()
        const val DESKTOP_KEY_COLOR = 0xFF29465C.toInt()
        const val DESKTOP_ENTER_KEY_COLOR = 0xFF176B72.toInt()
        const val DESKTOP_ENTER_PRESSED_COLOR = 0xFF0F555D.toInt()
        const val DESKTOP_KEY_HIGHLIGHT_COLOR = 0xFF4EC7E8.toInt()
        const val DESKTOP_TOOLBAR_COLOR = 0xFF0A2232.toInt()
        const val INSETS_LOG_TAG = "KBoardInsets"
        const val FLOATING_KEYBOARD_RADIUS_DP = 24
        const val FLOATING_RESIZE_CORNER_SIZE_DP = 48
        const val FLOATING_RESIZE_CORNER_PADDING_DP = 8
        const val FLOATING_RESIZE_CORNER_OFFSET_DP = 24
        const val FLOATING_KEYBOARD_DOCK_THRESHOLD_DP = 28
    }

}
