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
import android.view.MotionEvent
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
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
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

    private val keyBorder by ThemeManager.prefs.keyBorder

    private val customBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val desktopVoiceButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_keyboard_voice_24, theme).apply {
            visibility = GONE
            useFullSizeIcon()
        }
    }

    private val desktopOperationArea = view(::View) {
        visibility = GONE
        setBackgroundColor(Color.BLACK)
    }

    private val desktopExitButton = ToolButton(context, R.drawable.ic_dock_keyboard_24, theme).apply {
        visibility = GONE
        contentDescription = context.getString(R.string.exit_desktop_keyboard)
        useFullSizeIcon()
        setIconTintColor(ThemePreset.AMOLEDBlack.keyTextColor)
        setOnClickListener { keyboardWindow.toggleDesktopKeyboard() }
    }

    private val desktopOperationButtons = listOf(
        desktopExitButton,
        desktopVoiceButton
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
        setOnClickListener { service.requestHideSelf(0) }
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

    @Keep
    private val onKeyboardSizeChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (keyboardSizePrefs.any { it.key == key }) {
            updateKeyboardSize()
        }
    }

    val keyboardView: View

    private fun bringDesktopButtonsToFront() {
        desktopVoiceButton.bringToFront()
        desktopExitButton.bringToFront()
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

    private fun updateDesktopOperationButtonPositions() {
        if (!desktopKeyboardMode) return
        windowManager.view.post {
            if (!desktopKeyboardMode) return@post
            val centers = keyboardWindow.desktopOperationButtonCentersOnScreen() ?: return@post
            val parent = desktopExitButton.parent as? View ?: return@post
            val parentLocation = IntArray(2)
            parent.getLocationOnScreen(parentLocation)
            desktopExitButton.x = centers.first - parentLocation[0] - desktopExitButton.width / 2f
            desktopVoiceButton.x = centers.second - parentLocation[0] - desktopVoiceButton.width / 2f
        }
    }

    init {
        // MUST call before any operation
        setupScope()

        // restore punctuation mapping in case of InputView recreation
        fcitx.launchOnReady {
            punctuation.updatePunctuationMapping(it.statusAreaActionsCached)
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
            add(desktopExitButton, lParams(dp(DESKTOP_OPERATION_BUTTON_SIZE_DP), dp(DESKTOP_OPERATION_BUTTON_SIZE_DP)) {
                startOfParent()
                endToStartOf(desktopVoiceButton)
                bottomOfParent()
                horizontalChainStyle = LayoutParams.CHAIN_PACKED
            })
            add(desktopVoiceButton, lParams(dp(DESKTOP_OPERATION_BUTTON_SIZE_DP), dp(DESKTOP_OPERATION_BUTTON_SIZE_DP)) {
                startToEndOf(desktopExitButton)
                endOfParent()
                bottomOfParent()
                marginStart = dp(16)
            })
            add(bottomPaddingSpace, lParams {
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
                bottomOfParent()
            })
        }

        updateKeyboardSize()
        kawaiiBar.setDesktopVoiceButton(desktopVoiceButton)
        windowManager.view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateDesktopCompositionPosition()
            updateDesktopOperationButtonPositions()
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
    }

    fun toggleFloatingKeyboard(): Boolean {
        val isFloating = !floatingKeyboard.getValue()
        floatingKeyboard.setValue(isFloating)
        return isFloating
    }

    fun setDesktopKeyboardMode(enabled: Boolean) {
        if (desktopKeyboardMode == enabled) return
        desktopKeyboardMode = enabled
        kawaiiBar.setDesktopKeyboardMode(enabled)
        desktopOperationArea.visibility = if (enabled) VISIBLE else GONE
        desktopOperationButtons.filter { it !== desktopVoiceButton }.forEach {
            it.visibility = if (enabled) VISIBLE else GONE
        }
        desktopExitButton.setPhysicalKeyStyle(
            enabled,
            theme.altKeyBackgroundColor,
            theme.keyPressHighlightColor
        )
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
            if (enabled) theme.barColor
            else if (keyBorder) Color.TRANSPARENT else theme.barColor
        )
        keyboardView.setBackgroundColor(if (enabled) Color.BLACK else Color.TRANSPARENT)
        customBackground.imageDrawable = if (enabled) {
            ColorDrawable(Color.BLACK)
        } else {
            theme.backgroundDrawable(keyBorder)
        }
        keyboardView.updateLayoutParams<LayoutParams> {
            height = if (enabled) matchParent else wrapContent
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
        updateDesktopOperationButtonPositions()
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
            if (isFloating) restoreFloatingKeyboardPosition() else updateFloatingKeyboardPosition(0f, translationY)
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
                updateFloatingKeyboardPosition(keyboardView.translationX, keyboardView.translationY)
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
        val panelLeft = (width - keyboardView.width) / 2f
        val resizeCornerInset = if (isFloatingResizeMode) dp(FLOATING_RESIZE_CORNER_OFFSET_DP).toFloat() else 0f
        val minX = -panelLeft + resizeCornerInset
        val maxX = width - panelLeft - keyboardView.width - resizeCornerInset
        val minY = -(height - keyboardView.height).toFloat() + resizeCornerInset
        val maxY = -resizeCornerInset
        keyboardView.translationX = x.coerceIn(minX, maxX)
        keyboardView.translationY = y.coerceIn(minY, maxY)
        preedit.ui.root.translationX = keyboardView.translationX
        preedit.ui.root.translationY = keyboardView.translationY
        floatingResizeCorners.forEach {
            it.translationX = keyboardView.translationX
            it.translationY = keyboardView.translationY
        }
        updateFloatingHideKeyboardButtonPosition()
    }

    private fun updateFloatingHideKeyboardButtonPosition() {
        if (!floatingKeyboard.getValue()) return
        floatingHideKeyboardButton.post {
            floatingHideKeyboardButton.x = keyboardView.x
            floatingHideKeyboardButton.y = keyboardView.y + keyboardView.height -
                    floatingHideKeyboardButton.height + dp(FLOATING_HIDE_BUTTON_OFFSET_DP)
        }
    }

    private fun restoreFloatingKeyboardPosition() {
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
                bottomToTop = unset
                above(desktopOperationArea)
            } else if (floatingKeyboard.getValue()) {
                bottomToTop = unset
                above(floatingWindowHandle)
            } else {
                bottomToTop = unset
                above(bottomPaddingSpace)
            }
        }
        val sidePadding = if (desktopKeyboardMode) 0 else keyboardSidePaddingPx
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
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
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

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        return kawaiiBar.handleInlineSuggestions(response)
    }

    override fun onDetachedFromWindow() {
        keyboardPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        keyboardPrefs.unregisterOnChangeListener(onFloatingKeyboardChangeListener)
        // clear DynamicScope, implies that InputView should not be attached again after detached.
        scope.clear()
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
        const val DESKTOP_OPERATION_HEIGHT_DP = 64
        const val DESKTOP_OPERATION_BUTTON_SIZE_DP = 56
        const val DESKTOP_PREEDIT_GAP_DP = 0
        const val FLOATING_KEYBOARD_RADIUS_DP = 24
        const val FLOATING_RESIZE_CORNER_SIZE_DP = 48
        const val FLOATING_RESIZE_CORNER_PADDING_DP = 8
        const val FLOATING_RESIZE_CORNER_OFFSET_DP = 24
        const val FLOATING_KEYBOARD_DOCK_THRESHOLD_DP = 28
    }

}
