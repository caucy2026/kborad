package org.fcitx.fcitx5.android.input.overlay

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import timber.log.Timber

/** A tiny virtual-display editor; it never appears on the HDMI display. */
class KBoardOverlayActivity : Activity() {
    private var requestId = -1L
    private lateinit var editor: RelayEditor
    private val imeStartGate = OverlayImeStartGate()
    private val retryImeStart = Runnable { driveImeStart() }
    private var tokenRefreshRequested = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getLongExtra(KBoardOverlayService.EXTRA_REQUEST_ID, -1L)
        if (!KBoardOverlaySession.attachEditor(requestId, this)) {
            finish()
            return
        }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        window.setBackgroundDrawable(ColorDrawable(OverlaySurfacePolicy.backgroundColor))
        window.setDimAmount(0f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setFormat(OverlaySurfacePolicy.pixelFormat)
        window.decorView.setBackgroundColor(OverlaySurfacePolicy.backgroundColor)
        editor = RelayEditor(this) {
            imeStartGate.inputConnectionReady = true
            if (!tokenRefreshRequested) {
                tokenRefreshRequested = FcitxInputMethodService.requestOverlayImeTokenRefresh()
            }
            editor.post { driveImeStart() }
        }.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            privateImeOptions = KBoardOverlaySession.EDITOR_MARKER
            setSingleLine(false)
            isCursorVisible = false
            setTextColor(android.graphics.Color.TRANSPARENT)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    imeStartGate.attached = true
                    post { driveImeStart() }
                }

                override fun onViewDetachedFromWindow(view: View) {
                    imeStartGate.attached = false
                    removeCallbacks(retryImeStart)
                }
            })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(OverlaySurfacePolicy.backgroundColor)
            addView(editor, FrameLayout.LayoutParams(1, 1))
        })
        editor.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        imeStartGate.windowFocused = hasFocus
        if (::editor.isInitialized) {
            editor.removeCallbacks(retryImeStart)
            if (hasFocus) editor.post { driveImeStart() }
        }
    }

    private fun driveImeStart() {
        if (!::editor.isInitialized || isFinishing || isDestroyed) return
        editor.removeCallbacks(retryImeStart)
        val inputMethodManager = getSystemService(InputMethodManager::class.java)
        when (imeStartGate.nextAction()) {
            OverlayImeStartGate.Action.WAIT -> Unit
            OverlayImeStartGate.Action.EXHAUSTED ->
                Timber.e("Overlay editor never became a served IME view on display=${display?.displayId}")
            OverlayImeStartGate.Action.RESTART_INPUT -> {
                editor.requestFocus()
                inputMethodManager.restartInput(editor)
                editor.postDelayed(retryImeStart, IME_RETRY_DELAY_MS)
            }
            OverlayImeStartGate.Action.RESTART_AND_SHOW -> {
                editor.requestFocus()
                inputMethodManager.restartInput(editor)
                editor.postOnAnimation {
                    if (isFinishing || isDestroyed) return@postOnAnimation
                    if (inputMethodManager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)) {
                        imeStartGate.markShowAccepted()
                    } else {
                        editor.postDelayed(retryImeStart, IME_RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::editor.isInitialized) editor.removeCallbacks(retryImeStart)
        if (!isChangingConfigurations && KBoardOverlaySession.owns(requestId)) {
            KBoardOverlaySession.requestClose(requestId)
        }
        super.onDestroy()
    }

    private class RelayEditor(
        activity: Activity,
        private val onInputConnectionReady: () -> Unit
    ) : EditText(activity) {
        override fun onCreateInputConnection(info: EditorInfo): InputConnection {
            val delegate = super.onCreateInputConnection(info)
            post(onInputConnectionReady)
            return object : InputConnectionWrapper(delegate, true) {
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    KBoardOverlaySession.input("commit", text?.toString().orEmpty(), newCursorPosition)
                    return super.commitText(text, newCursorPosition)
                }

                override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    return super.setComposingText(text, newCursorPosition)
                }

                override fun finishComposingText(): Boolean {
                    return super.finishComposingText()
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    KBoardOverlaySession.input("delete", arg1 = beforeLength, arg2 = afterLength)
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }

                override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                    KBoardOverlaySession.input("deleteCodePoints", arg1 = beforeLength, arg2 = afterLength)
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    KBoardOverlaySession.input(
                        "key",
                        arg1 = event.keyCode,
                        arg2 = event.action,
                        extras = Bundle().apply {
                            putInt("metaState", event.metaState)
                            putInt("repeatCount", event.repeatCount)
                            putInt("unicodeChar", event.unicodeChar)
                        }
                    )
                    return super.sendKeyEvent(event)
                }

                override fun performEditorAction(actionCode: Int): Boolean {
                    KBoardOverlaySession.input("editorAction", arg1 = actionCode)
                    return super.performEditorAction(actionCode)
                }

                override fun performPrivateCommand(action: String?, data: Bundle?): Boolean {
                    KBoardOverlaySession.input(
                        "privateCommand",
                        extras = Bundle(data ?: Bundle()).apply { putString("action", action) }
                    )
                    return super.performPrivateCommand(action, data)
                }

                override fun setSelection(start: Int, end: Int): Boolean {
                    KBoardOverlaySession.input("selection", arg1 = start, arg2 = end)
                    return super.setSelection(start, end)
                }
            }
        }
    }

    companion object {
        private const val IME_RETRY_DELAY_MS = 120L
    }
}
