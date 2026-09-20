package org.fcitx.fcitx5.android.input.overlay

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.common.ipc.IKBoardOverlayCallback
import org.fcitx.fcitx5.android.input.keyboard.RemoteMouseInputProtocol
import java.lang.ref.WeakReference

/** Process-local handoff from the virtual editor to its bound, signature-protected owner. */
internal object KBoardOverlaySession {
    private var physicalOverlay = false
    data class LayoutMetrics(
        val canvasWidthPx: Int,
        val canvasHeightPx: Int,
        val physicalTargetHeightPx: Int
    )

    var virtualDisplayId: Int = -1
        private set
    private var requestId = 0L
    private var sessionId = ""
    private var callback: IKBoardOverlayCallback? = null
    private var editor: WeakReference<Activity>? = null
    private var readyListener: ((Int) -> Unit)? = null
    private var closeListener: (() -> Unit)? = null
    private var switchDisplayListener: (() -> Unit)? = null
    var layoutMetrics: LayoutMetrics? = null
        private set
    private val heldModifiers = linkedSetOf<Int>()
    private val heldMouseButtons = linkedSetOf<String>()

    fun beginPhysical(
        id: Long,
        session: String,
        owner: IKBoardOverlayCallback,
        onCloseRequested: () -> Unit,
        onSwitchDisplayRequested: () -> Unit
    ) {
        requestId = id
        sessionId = session
        callback = owner
        closeListener = onCloseRequested
        switchDisplayListener = onSwitchDisplayRequested
        physicalOverlay = true
    }

    val isPhysicalOverlayActive: Boolean
        get() = physicalOverlay && callback != null

    fun ownsPhysical(id: Long?): Boolean =
        id != null && physicalOverlay && callback != null && id == requestId

    fun physicalSessionFor(id: Long?): String? =
        if (ownsPhysical(id)) sessionId else null

    fun begin(
        id: Long,
        session: String,
        displayId: Int,
        canvasWidthPx: Int,
        canvasHeightPx: Int,
        physicalTargetHeightPx: Int,
        owner: IKBoardOverlayCallback,
        onReady: (Int) -> Unit,
        onCloseRequested: () -> Unit
    ) {
        requestId = id
        sessionId = session
        virtualDisplayId = displayId
        layoutMetrics = LayoutMetrics(canvasWidthPx, canvasHeightPx, physicalTargetHeightPx)
        callback = owner
        readyListener = onReady
        closeListener = onCloseRequested
    }

    fun end(id: Long) {
        if (id != requestId) return
        releaseInputStates()
        editor?.get()?.finish()
        editor = null
        callback = null
        readyListener = null
        closeListener = null
        switchDisplayListener = null
        heldModifiers.clear()
        heldMouseButtons.clear()
        requestId = 0L
        sessionId = ""
        virtualDisplayId = -1
        layoutMetrics = null
        physicalOverlay = false
    }

    fun owns(id: Long): Boolean = callback != null && id == requestId

    fun isOverlayEditor(info: EditorInfo): Boolean =
        callback != null && info.privateImeOptions?.contains(EDITOR_MARKER) == true

    fun attachEditor(id: Long, activity: Activity): Boolean {
        if (!owns(id)) return false
        editor = WeakReference(activity)
        return true
    }

    fun markImeFrameReady(displayId: Int) {
        if (displayId == virtualDisplayId) readyListener?.invoke(displayId)
    }

    fun closeForRegularInput(displayId: Int) {
        if (!physicalOverlay && callback != null && virtualDisplayId != -1 && displayId != virtualDisplayId) {
            closeListener?.invoke()
        }
    }

    fun requestClose(id: Long) {
        if (owns(id)) closeListener?.invoke()
    }

    fun requestClosePhysical() {
        if (isPhysicalOverlayActive) closeListener?.invoke()
    }

    fun requestSwitchPhysicalDisplay(id: Long?) {
        if (ownsPhysical(id)) {
            releaseInputStates()
            heldModifiers.clear()
            heldMouseButtons.clear()
            switchDisplayListener?.invoke()
        }
    }

    fun input(operation: String, text: String = "", arg1: Int = 0, arg2: Int = 0, extras: Bundle = Bundle()) {
        val owner = callback ?: return
        trackInputState(operation, arg1, arg2, extras)
        try {
            owner.onInput(requestId, sessionId, operation, text, arg1, arg2, extras)
        } catch (_: android.os.RemoteException) {
            // Binder death also tears down the surface through the bound service.
        }
    }

    private fun trackInputState(operation: String, arg1: Int, arg2: Int, extras: Bundle) {
        if (operation == "key" && arg1 in MODIFIER_KEYS) {
            if (arg2 == KeyEvent.ACTION_DOWN) heldModifiers += arg1 else heldModifiers -= arg1
        }
        if (operation == "privateCommand" &&
            extras.getString("action") == RemoteMouseInputProtocol.ACTION &&
            extras.getString(RemoteMouseInputProtocol.EXTRA_TYPE) ==
            RemoteMouseInputProtocol.TYPE_BUTTON
        ) {
            val button = extras.getString(RemoteMouseInputProtocol.EXTRA_BUTTON) ?: return
            if (extras.getBoolean(RemoteMouseInputProtocol.EXTRA_DOWN)) {
                heldMouseButtons += button
            } else {
                heldMouseButtons -= button
            }
        }
    }

    private fun releaseInputStates() {
        val owner = callback ?: return
        heldModifiers.toList().forEach { keyCode ->
            runCatching {
                owner.onInput(
                    requestId, sessionId, "key", "", keyCode, KeyEvent.ACTION_UP, Bundle()
                )
            }
        }
        heldMouseButtons.toList().forEach { button ->
            runCatching {
                owner.onInput(
                    requestId,
                    sessionId,
                    "privateCommand",
                    "",
                    0,
                    0,
                    Bundle().apply {
                        putString("action", RemoteMouseInputProtocol.ACTION)
                        putString(
                            RemoteMouseInputProtocol.EXTRA_TYPE,
                            RemoteMouseInputProtocol.TYPE_BUTTON
                        )
                        putString(RemoteMouseInputProtocol.EXTRA_BUTTON, button)
                        putBoolean(RemoteMouseInputProtocol.EXTRA_DOWN, false)
                    }
                )
            }
        }
    }

    private val MODIFIER_KEYS = setOf(
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT,
        KeyEvent.KEYCODE_META_RIGHT
    )

    const val EDITOR_MARKER = "com.newlink.kemi.kboard.OVERLAY_EDITOR"
}
