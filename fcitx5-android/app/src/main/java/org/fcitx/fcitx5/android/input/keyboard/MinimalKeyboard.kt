/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import splitties.dimensions.dp

/**
 * A deliberately small, movable keyboard for voice-first editing.
 *
 * The keyboard reuses the normal Backspace/Return definitions so their input routing, repeat
 * behaviour and feedback stay identical to the full keyboard. Clipboard and ASR are delegated to
 * the existing windows/components; this view owns presentation only.
 */
@SuppressLint("ViewConstructor")
class MinimalKeyboard private constructor(
    context: Context,
    theme: Theme,
    private val controls: Controls
) : BaseKeyboard(
    context,
    theme,
    Layout,
    controls.root
) {

    constructor(
        context: Context,
        theme: Theme,
        onClipboard: (String) -> Unit,
        onReturnToPrevious: () -> Unit,
        onHide: (View) -> Unit,
        onDrag: (View, MotionEvent) -> Boolean
    ) : this(
        context,
        theme,
        createControls(context, theme, onClipboard, onReturnToPrevious, onHide, onDrag)
    )

    val voiceButton: ToolButton
        get() = controls.voiceButton

    init {
        controls.statusText.setOnClickListener {
            if (voiceTranscript.isNullOrBlank()) displayedEntry?.let {
                controls.onClipboard(it.text)
                ClipboardManager.consumeSuggestion(it)
                updateClipboard(null)
            }
        }
        listOf(controls.backButton, controls.voiceButton).forEachIndexed { index, button ->
            addView(button, LayoutParams(0, 0).apply {
                leftToLeft = LayoutParams.PARENT_ID
                rightToRight = LayoutParams.PARENT_ID
                topToBottom = controls.root.id
                bottomToBottom = LayoutParams.PARENT_ID
                matchConstraintPercentWidth = 0.20f
                horizontalBias = index / 4f
            })
        }
    }

    private var attached = false
    private var attachmentScope: CoroutineScope? = null
    private var voiceTranscript: CharSequence? = null
    private var clipboardText: CharSequence = context.getString(R.string.clipboard)
    private var clipboardEntryText: String? = null
    private var displayedEntry: ClipboardEntry? = null
    private var clipboardExpiry: Job? = null

    private val clipboardListener = ClipboardManager.OnClipboardUpdateListener { entry ->
        attachmentScope?.launch { if (attached) updateClipboard(entry) }
    }

    override fun onAttach() {
        if (attached) return
        attached = true
        attachmentScope = CoroutineScope(Job() + Dispatchers.Main.immediate)
        ClipboardManager.addOnUpdateListener(clipboardListener)
        updateClipboard(ClipboardManager.lastEntry)
        attachmentScope?.launch {
            val entry = ClipboardManager.latestEntry()
            if (attached) updateClipboard(entry)
        }
    }

    override fun onDetach() {
        if (!attached) return
        attached = false
        ClipboardManager.removeOnUpdateListener(clipboardListener)
        attachmentScope?.cancel()
        attachmentScope = null
        showVoiceTranscript(null)
    }

    override fun dispose() {
        onDetach()
        super.dispose()
    }

    fun showVoiceTranscript(text: CharSequence?) {
        voiceTranscript = text
        controls.statusText.text = text?.takeIf { it.isNotBlank() } ?: clipboardText
        controls.statusText.visibility = if (text.isNullOrBlank() && displayedEntry == null)
            View.INVISIBLE else View.VISIBLE
        controls.statusText.contentDescription = if (text.isNullOrBlank()) {
            context.getString(R.string.clipboard)
        } else {
            text
        }
    }

    private fun updateClipboard(entry: ClipboardEntry?) {
        clipboardExpiry?.cancel()
        val prefs = AppPrefs.getInstance().clipboard
        val remaining = entry?.let {
            prefs.clipboardItemTimeout.getValue() * 1000L - (System.currentTimeMillis() - it.timestamp)
        } ?: 0L
        displayedEntry = entry?.takeIf {
            it.text.isNotBlank() && prefs.clipboardSuggestion.getValue() &&
                !ClipboardManager.isSuggestionConsumed(it) && remaining > 0
        }
        clipboardEntryText = displayedEntry?.text
        clipboardText = clipboardEntryText.orEmpty()
        if (displayedEntry != null) clipboardExpiry = attachmentScope?.launch {
            delay(remaining)
            updateClipboard(null)
        }
        showVoiceTranscript(voiceTranscript)
    }

    private data class Controls(
        val root: LinearLayout,
        val statusText: TextView,
        val voiceButton: ToolButton,
        val backButton: ToolButton,
        val onClipboard: (String) -> Unit
    )

    companion object {
        const val Name = "Minimal"

        private val Layout: List<List<KeyDef>> = listOf(
            listOf(
                KeyDef(KeyDef.Appearance.Text("", 14f, percentWidth = 0.20f), emptySet()),
                KeyDef(KeyDef.Appearance.Text("", 14f, percentWidth = 0.20f), emptySet()),
                uniformKey(BackspaceKey(0.20f)),
                uniformKey(ReturnKey(0.20f)),
                uniformKey(ScreenSwitchKey(0.20f))
            )
        )

        private fun uniformKey(original: KeyDef): KeyDef {
            val appearance = original.appearance as KeyDef.Appearance.Image
            return KeyDef(
                KeyDef.Appearance.Image(
                    src = appearance.src,
                    percentWidth = appearance.percentWidth,
                    variant = KeyDef.Appearance.Variant.Normal,
                    border = KeyDef.Appearance.Border.On,
                    viewId = View.generateViewId(),
                    soundEffect = appearance.soundEffect
                ),
                original.behaviors,
                original.popup
            )
        }

        private fun createControls(
            context: Context,
            theme: Theme,
            onClipboard: (String) -> Unit,
            onReturnToPrevious: () -> Unit,
            onHide: (View) -> Unit,
            onDrag: (View, MotionEvent) -> Boolean
        ): Controls {
            val root = LinearLayout(context).apply {
                id = View.generateViewId()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(4), 0, context.dp(4), 0)
            }
            val back = ToolButton(context, R.drawable.ic_minimal_return_keyboard_24, theme).apply {
                useFullSizeIcon()
                contentDescription = context.getString(R.string.minimal_keyboard_return)
                setOnClickListener { onReturnToPrevious() }
            }
            val status = TextView(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                textSize = 14f
                setTextColor(theme.candidateTextColor)
                setPadding(context.dp(6), 0, context.dp(6), 0)
                text = context.getString(R.string.minimal_keyboard_clipboard_empty)
                contentDescription = context.getString(R.string.clipboard)
            }
            val voice = ToolButton(
                context,
                R.drawable.ic_baseline_keyboard_voice_24,
                theme
            ).apply {
                useFullSizeIcon()
                contentDescription = context.getString(R.string.start_voice_input)
            }
            val drag = ToolButton(
                context,
                R.drawable.ic_baseline_drag_handle_24,
                theme
            ).apply {
                useFullSizeIcon()
                contentDescription = context.getString(R.string.minimal_keyboard_move)
                setOnTouchListener(onDrag)
            }
            val hide = ToolButton(context, R.drawable.ic_keyboard_arrow_down_24, theme).apply {
                useFullSizeIcon()
                contentDescription = context.getString(R.string.hide_keyboard)
                setOnClickListener { onHide(it) }
            }
            root.addView(status, LinearLayout.LayoutParams(0, context.dp(48), 1f))
            root.addView(hide, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
            root.addView(drag, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
            return Controls(root, status, voice, back, onClipboard)
        }
    }
}
