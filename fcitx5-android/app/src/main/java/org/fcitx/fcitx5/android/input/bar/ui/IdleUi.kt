/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.text.TextUtils
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.Gravity
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.Space
import android.widget.TextView
import android.widget.ViewAnimator
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.bar.ui.idle.ButtonsBarUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.ClipboardSuggestionUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.InlineSuggestionsUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.NumberRow
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.matchParent
import splitties.views.imageResource
import timber.log.Timber

class IdleUi(
    override val ctx: Context,
    private val theme: Theme,
    private val popup: PopupComponent,
    private val commonKeyActionListener: CommonKeyActionListener
) : Ui {

    private companion object {
        const val VOICE_ICON_SYSTEM_GREEN = -13346741 // 0xFF34C759
    }

    enum class State {
        Empty, Toolbar, Clipboard, NumberRow, InlineSuggestion
    }

    var currentState = State.Empty
        private set

    private val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation

    private var inPrivate = false

    private val translateDirection by lazy {
        if (ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) 1f else -1f
    }

    private val menuButtonRotation
        get() = when {
            inPrivate -> 0f
            currentState == State.Toolbar -> 90f * translateDirection
            else -> -90f * translateDirection
        }

    val menuButton = ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, theme).apply {
        iconRotation = menuButtonRotation
    }

    val hideKeyboardButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_drop_down_24, theme)

    private val voiceSeparator = View(ctx).apply {
        setBackgroundColor((theme.altKeyTextColor and 0x00ffffff) or 0x33000000)
        visibility = View.GONE
    }

    val emptyBar = Space(ctx)

    val buttonsUi = ButtonsBarUi(ctx, theme)

    val clipboardUi = ClipboardSuggestionUi(ctx, theme)

    val numberRow = NumberRow(ctx, theme).apply {
        visibility = View.GONE
    }

    val inlineSuggestionsBar = InlineSuggestionsUi(ctx)
    private val voiceTranscript = TextView(ctx).apply {
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        maxLines = 1
        setPadding(dp(16), 0, dp(16), 0)
        setTextColor(theme.keyTextColor)
        textSize = 18f
        visibility = View.GONE
    }

    private val animator = ViewAnimator(ctx).apply {
        add(emptyBar, lParams(matchParent, matchParent))
        add(buttonsUi.root, lParams(matchParent, matchParent))
        add(clipboardUi.root, lParams(matchParent, matchParent))
        add(inlineSuggestionsBar.root, lParams(matchParent, matchParent))
    }

    private val inAnimation by lazy {
        AnimationSet(true).apply {
            duration = 200L
            addAnimation(AlphaAnimation(0f, 1f))
            // 2 stands for Animation.RELATIVE_TO_PARENT
            addAnimation(TranslateAnimation(2, -0.3f * translateDirection, 2, 0f, 0, 0f, 0, 0f))
        }
    }

    private val outAnimation by lazy {
        AnimationSet(true).apply {
            duration = 200L
            addAnimation(AlphaAnimation(1f, 0f))
            addAnimation(TranslateAnimation(2, 0f, 2, -0.3f * translateDirection, 0, 0f, 0, 0f))
        }
    }

    private val idleBody = constraintLayout {
        val size = dp(KawaiiBarComponent.HEIGHT)
        add(menuButton, lParams(size, size) {
            startOfParent()
            centerVertically()
        })
        add(hideKeyboardButton, lParams(size, size) {
            endOfParent()
            centerVertically()
            marginEnd = dp(32)
        })
        add(voiceSeparator, lParams(dp(1), dp(32)) {
            before(hideKeyboardButton)
            centerVertically()
            marginEnd = dp(28)
        })
        add(animator, lParams(matchConstraints, matchParent) {
            after(menuButton)
            before(voiceSeparator)
            centerVertically()
        })
        add(voiceTranscript, lParams(matchConstraints, matchParent) {
            after(menuButton)
            before(voiceSeparator)
            centerVertically()
        })
    }

    override val root = frameLayout {
        add(idleBody, lParams(matchParent, matchParent))
        add(numberRow, lParams(matchParent, matchParent))
    }

    fun privateMode(activate: Boolean = true) {
        if (activate == inPrivate) return
        inPrivate = activate
        updateMenuButtonIcon()
        updateMenuButtonContentDescription()
        updateMenuButtonRotation(instant = true)
    }

    private fun updateMenuButtonIcon() {
        menuButton.setIcon(
            if (inPrivate) R.drawable.ic_view_private
            else R.drawable.ic_baseline_expand_more_24
        )
    }

    private fun updateMenuButtonContentDescription() {
        menuButton.contentDescription = when {
            inPrivate -> ctx.getString(R.string.private_mode)
            currentState == State.Toolbar -> ctx.getString(R.string.hide_toolbar)
            else -> ctx.getString(R.string.expand_toolbar)
        }
    }

    private fun updateMenuButtonRotation(instant: Boolean = false) {
        val targetRotation = menuButtonRotation
        menuButton.apply {
            if (targetRotation == iconRotation) return
            iconAnimate().cancel()
            if (!instant && !disableAnimation) {
                iconAnimate().setDuration(200L).rotation(targetRotation)
            } else {
                iconRotation = targetRotation
            }
        }
    }

    fun setHideKeyboardIsVoiceInput(isVoiceInput: Boolean) {
        voiceSeparator.visibility = if (isVoiceInput) View.VISIBLE else View.GONE
        if (isVoiceInput) {
            hideKeyboardButton.setIcon(R.drawable.ic_baseline_keyboard_voice_24)
            hideKeyboardButton.setIconTintColor(theme.altKeyTextColor)
            hideKeyboardButton.useFullSizeIcon()
            hideKeyboardButton.setCircleBackgroundColor(theme.keyBackgroundColor)
            hideKeyboardButton.contentDescription = ctx.getString(R.string.start_voice_input)
        } else {
            hideKeyboardButton.setIcon(R.drawable.ic_baseline_arrow_drop_down_24)
            hideKeyboardButton.setIconTintColor(theme.altKeyTextColor)
            hideKeyboardButton.setPressHighlightColor(theme.keyPressHighlightColor)
            hideKeyboardButton.contentDescription = ctx.getString(R.string.hide_keyboard)
        }
    }

    fun setVoiceInputActive(active: Boolean) {
        hideKeyboardButton.setIcon(R.drawable.ic_baseline_keyboard_voice_24)
        hideKeyboardButton.setIconTintColor(if (active) VOICE_ICON_SYSTEM_GREEN else theme.altKeyTextColor)
        hideKeyboardButton.setCircleBackgroundColor(
            if (active) theme.genericActiveBackgroundColor else theme.keyBackgroundColor
        )
        hideKeyboardButton.contentDescription = ctx.getString(
            if (active) R.string.stop_voice_input else R.string.start_voice_input
        )
    }
    fun showVoiceTranscript(text: CharSequence) {
        voiceTranscript.text = text
        voiceTranscript.visibility = View.VISIBLE
        animator.visibility = View.INVISIBLE
    }

    fun hideVoiceTranscript() {
        voiceTranscript.text = ""
        voiceTranscript.visibility = View.GONE
        animator.visibility = View.VISIBLE
    }

    private fun clearAnimation() {
        animator.inAnimation = null
        animator.outAnimation = null
    }

    private fun setAnimation() {
        animator.inAnimation = inAnimation
        animator.outAnimation = outAnimation
    }

    private fun enableSlideTransition(inTarget: View, outTarget: View, inGravity: Int, outGravity: Int) {
        val slideIn = Slide(inGravity).apply { duration = 200L }
        val slideOut = Slide(outGravity).apply { duration = 200L }
        slideIn.addTarget(inTarget)
        slideOut.addTarget(outTarget)
        val set = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(slideIn)
            addTransition(slideOut)
        }
        TransitionManager.beginDelayedTransition(root, set)
    }

    fun updateState(state: State, fromUser: Boolean = false) {
        Timber.d("Switch idle ui to $state")
        if (
            !fromUser ||
            disableAnimation ||
            (state == State.InlineSuggestion || currentState == State.InlineSuggestion) ||
            (state == State.NumberRow || currentState == State.NumberRow)
        ) {
            clearAnimation()
        } else {
            setAnimation()
        }
        when (state) {
            State.Empty -> animator.displayedChild = 0
            State.Toolbar -> animator.displayedChild = 1
            State.Clipboard -> animator.displayedChild = 2
            State.NumberRow -> {}
            State.InlineSuggestion -> animator.displayedChild = 3
        }
        if (state == State.NumberRow) {
            numberRow.keyActionListener = commonKeyActionListener.listener
            numberRow.popupActionListener = popup.listener
            if (fromUser && !disableAnimation) {
                enableSlideTransition(numberRow, idleBody, Gravity.END, Gravity.START)
            }
            numberRow.visibility = View.VISIBLE
            idleBody.visibility = View.GONE
        } else if (currentState == State.NumberRow) {
            if (fromUser && !disableAnimation) {
                enableSlideTransition(idleBody, numberRow, Gravity.START, Gravity.END)
            }
            idleBody.visibility = View.VISIBLE
            numberRow.visibility = View.GONE
            numberRow.keyActionListener = null
            numberRow.popupActionListener = null
            popup.dismissAll()
        }
        currentState = state
        updateMenuButtonContentDescription()
        updateMenuButtonRotation(instant = !fromUser)
    }
}
