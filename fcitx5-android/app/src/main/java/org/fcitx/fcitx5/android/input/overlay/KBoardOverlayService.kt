package org.fcitx.fcitx5.android.input.overlay

import android.app.Service
import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import androidx.lifecycle.setViewTreeLifecycleOwner
import org.fcitx.fcitx5.android.common.ipc.IKBoardOverlayCallback
import org.fcitx.fcitx5.android.common.ipc.IKBoardOverlayService
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.InputView
import java.security.MessageDigest
import java.lang.ref.WeakReference

/** Hosts the stable, complete InputView on a physical display without recreating keyboard UI. */
class KBoardOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private var owner: Owner? = null
    private var pendingStart: PendingStart? = null
    private var imeBootstrapBound = false
    private var taskStackProbeRegistered = false
    private val taskStackProbe = object : TaskStackListener() {
        override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
            handleTaskStackProbe("moved-to-front", taskInfo)
        }

        override fun onActivityRestartAttempt(
            task: ActivityManager.RunningTaskInfo,
            homeTaskVisible: Boolean,
            clearedTask: Boolean,
            wasVisible: Boolean
        ) {
            handleTaskStackProbe(
                "restart-attempt",
                task,
                " homeTaskVisible=$homeTaskVisible clearedTask=$clearedTask wasVisible=$wasVisible"
            )
        }
    }
    private val systemNavigationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            val reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY)
            if (!PhysicalOverlaySystemNavigationPolicy.shouldClose(reason)) return
            val eventOwner = owner
            val eventPending = pendingStart
            val requestId = eventOwner?.requestId ?: eventPending?.requestId ?: return
            val sessionId = eventOwner?.sessionId ?: eventPending?.sessionId ?: return
            handler.post {
                val current = owner
                if (PhysicalOverlaySystemNavigationPolicy.ownsEvent(
                        requestId, sessionId, current?.requestId, current?.sessionId
                    )) {
                    Log.i(
                        TAG,
                        "system-navigation request=$requestId display=${current?.targetDisplayId} reason=$reason action=close"
                    )
                    close(REASON_SYSTEM_NAVIGATION)
                } else {
                    val pending = pendingStart
                    if (PhysicalOverlaySystemNavigationPolicy.ownsEvent(
                            requestId, sessionId, pending?.requestId, pending?.sessionId
                        )) {
                        Log.i(
                            TAG,
                            "system-navigation request=$requestId display=${pending?.target?.displayId} " +
                                "reason=$reason action=cancel-pending"
                        )
                        cancelPendingStart(REASON_SYSTEM_NAVIGATION)
                    }
                }
            }
        }
    }
    private val imeBootstrapConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
        override fun onServiceDisconnected(name: ComponentName?) {
            imeBootstrapBound = false
        }
    }

    private data class PendingStart(
        val requestId: Long, val sessionId: String, val sourceDisplayId: Int,
        val target: Display, val width: Int, val height: Int,
        val callback: IKBoardOverlayCallback, val callerUid: Int
    )

    private data class Owner(
        val requestId: Long, val sessionId: String, val sourceDisplayId: Int,
        val targetDisplayId: Int, val peerDisplayId: Int, val windowWidth: Int,
        val requestedHeight: Int,
        val callback: IKBoardOverlayCallback,
        val death: IBinder.DeathRecipient, val windowManager: WindowManager,
        val inputMethodService: FcitxInputMethodService, val inputView: InputView,
        val listener: DisplayManager.DisplayListener, val callerUid: Int
    )

    private val binder = object : IKBoardOverlayService.Stub() {
        override fun getCapabilities(): Int {
            enforceOwner()
            return CAPABILITY_BIDIRECTIONAL_OVERLAY or CAPABILITY_LOCAL_COMPOSITION or CAPABILITY_REMOTE_MOUSE
        }

        override fun show(
            requestId: Long, sessionId: String, sourceDisplayId: Int, targetDisplayId: Int,
            targetWidth: Int, targetHeight: Int, keyboardHeight: Int,
            callback: IKBoardOverlayCallback?
        ): Boolean {
            enforceOwner()
            val online = displays.displays.mapTo(mutableSetOf()) { it.displayId }
            if (callback == null || !OverlayRequestPolicy.isValid(
                    requestId, sessionId, sourceDisplayId, targetDisplayId,
                    targetWidth, targetHeight, keyboardHeight, online
                )) return false
            val target = displays.getDisplay(targetDisplayId) ?: return false
            if (targetWidth > target.mode.physicalWidth || targetHeight > target.mode.physicalHeight) return false
            val callerUid = Binder.getCallingUid()
            handler.post {
                cancelPendingStart(REASON_REPLACED)
                close(REASON_REPLACED)
                val pending = PendingStart(
                    requestId, sessionId, sourceDisplayId, target,
                    targetWidth, targetHeight, callback, callerUid
                )
                pendingStart = pending
                start(pending, 0)
            }
            return true
        }

        override fun hide(requestId: Long, sessionId: String) {
            enforceOwner()
            handler.post {
                if (pendingStart?.requestId == requestId && pendingStart?.sessionId == sessionId) {
                    cancelPendingStart(REASON_HIDDEN)
                }
                if (owner?.requestId == requestId && owner?.sessionId == sessionId) close(REASON_HIDDEN)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
    override fun onCreate() {
        super.onCreate()
        currentService = WeakReference(this)
        registerReceiver(systemNavigationReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        runCatching {
            ActivityTaskManager.getInstance().registerTaskStackListener(taskStackProbe)
            taskStackProbeRegistered = true
            Log.i(TAG, "task-probe registration=success action=close-target-home")
        }.onFailure {
            Log.e(TAG, "task-probe registration=failed action=close-target-home", it)
        }
    }
    override fun onDestroy() {
        if (taskStackProbeRegistered) {
            runCatching {
                ActivityTaskManager.getInstance().unregisterTaskStackListener(taskStackProbe)
            }.onFailure { Log.w(TAG, "task-probe unregister failed", it) }
            taskStackProbeRegistered = false
        }
        runCatching { unregisterReceiver(systemNavigationReceiver) }
        cancelPendingStart(REASON_SERVICE_STOPPED)
        close(REASON_SERVICE_STOPPED)
        if (currentService?.get() === this) currentService = null
        super.onDestroy()
    }

    private fun handleTaskStackProbe(
        event: String,
        task: ActivityManager.RunningTaskInfo,
        suffix: String = ""
    ) {
        val eventOwner = owner
        val eventPending = pendingStart
        val requestId = eventOwner?.requestId ?: eventPending?.requestId ?: return
        val sessionId = eventOwner?.sessionId ?: eventPending?.sessionId ?: return
        val overlayDisplay = eventOwner?.targetDisplayId
            ?: eventPending?.target?.displayId ?: return
        val categories = task.baseIntent.categories.orEmpty()
        val taskDisplay = runCatching {
            task.javaClass.getField("displayId").getInt(task)
        }.getOrElse {
            Log.w(TAG, "task-probe displayId unavailable", it)
            Display.INVALID_DISPLAY
        }
        val candidate = HomeTaskStackProbePolicy.isTargetHome(
            taskDisplay, overlayDisplay, categories
        )
        if (!candidate) {
            Log.i(
                TAG,
                "task-probe event=$event action=ignore candidate=false " +
                    "overlayDisplay=$overlayDisplay taskDisplay=$taskDisplay taskId=${task.taskId} " +
                    "request=$requestId session=$sessionId " +
                    "base=${task.baseActivity?.flattenToShortString()} " +
                    "top=${task.topActivity?.flattenToShortString()} categories=${categories.sorted()}$suffix"
            )
            return
        }
        handler.post {
            val current = owner
            if (HomeTaskStackProbePolicy.ownsCandidate(
                    requestId, sessionId, taskDisplay, current?.requestId,
                    current?.sessionId, current?.targetDisplayId
                )) {
                Log.i(
                    TAG,
                    "task-probe event=$event action=close-home-task candidate=true " +
                        "display=$taskDisplay request=$requestId session=$sessionId$suffix"
                )
                close(REASON_SYSTEM_NAVIGATION)
                return@post
            }
            val pending = pendingStart
            if (HomeTaskStackProbePolicy.ownsCandidate(
                    requestId, sessionId, taskDisplay, pending?.requestId,
                    pending?.sessionId, pending?.target?.displayId
                )) {
                Log.i(
                    TAG,
                    "task-probe event=$event action=cancel-pending-home-task candidate=true " +
                        "display=$taskDisplay request=$requestId session=$sessionId$suffix"
                )
                cancelPendingStart(REASON_SYSTEM_NAVIGATION)
            }
        }
    }

    private fun start(pending: PendingStart, attempt: Int) {
        if (pendingStart !== pending) return
        val (requestId, sessionId, sourceDisplayId, target, width, height, callback, callerUid) = pending
        val ime = FcitxInputMethodService.currentProcessInstance()
        if (ime == null) {
            if (attempt == 0) {
                runCatching {
                    val bootstrapIntent = Intent(
                        this, FcitxInputMethodService::class.java
                    ).apply { action = ACTION_BOOTSTRAP_IME }
                    startService(bootstrapIntent)
                    imeBootstrapBound = bindService(
                        bootstrapIntent,
                        imeBootstrapConnection,
                        BIND_AUTO_CREATE
                    )
                    check(imeBootstrapBound) { "failed to retain bootstrapped IME service" }
                    stopService(bootstrapIntent)
                }.onFailure {
                    Log.e(TAG, "failed to bootstrap IME service", it)
                    cancelPendingStart(REASON_START_FAILED)
                    return
                }
            }
            if (attempt < IME_BOOTSTRAP_MAX_ATTEMPTS) {
                handler.postDelayed({ start(pending, attempt + 1) }, IME_BOOTSTRAP_RETRY_MS)
            } else {
                Log.e(TAG, "IME bootstrap timed out request=$requestId")
                cancelPendingStart(REASON_START_FAILED)
            }
            return
        }
        pendingStart = null
        val displayContext = createDisplayContext(target)
        val windowManager = displayContext.getSystemService(WindowManager::class.java)
        val death = IBinder.DeathRecipient {
            handler.post { if (owner?.requestId == requestId) close(REASON_OWNER_DIED) }
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayChanged(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) {
                if (id == sourceDisplayId || id == target.displayId) handler.post { close(REASON_DISPLAY_REMOVED) }
            }
        }
        var inputView: InputView? = null
        try {
            callback.asBinder().linkToDeath(death, 0)
            displays.registerDisplayListener(listener, handler)
            KBoardOverlaySession.beginPhysical(
                requestId, sessionId, callback,
                onCloseRequested = { handler.post { close(REASON_HIDDEN) } },
                onSwitchDisplayRequested = { handler.post { switchPhysicalDisplay() } }
            )
            inputView = ime.createPhysicalOverlayInputView(displayContext, requestId, callerUid = callerUid)
            val createdView = inputView
            createdView.setViewTreeLifecycleOwner(ime)
            val layoutName = createdView.physicalOverlayLayoutName()
            val windowHeight = PhysicalOverlayWindowPolicy.resolveHeight(
                layoutName, height, target.mode.physicalHeight
            )
            owner = Owner(requestId, sessionId, sourceDisplayId, target.displayId,
                sourceDisplayId, width, height, callback,
                death, windowManager, ime, createdView, listener, callerUid)
            val layoutParams = WindowManager.LayoutParams(
                width, windowHeight,
                PhysicalOverlayWindowPolicy.type,
                PhysicalOverlayWindowPolicy.flags, PhysicalOverlayWindowPolicy.format
            ).apply {
                gravity = PhysicalOverlayWindowPolicy.gravity
                title = "KBoardPhysicalKeyboard"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setFitInsetsTypes(PhysicalOverlayWindowPolicy.fitInsetsTypes)
                }
            }
            windowManager.addView(createdView, layoutParams)
            createdView.post {
                if (owner?.requestId != requestId) return@post
                createdView.windowInsetsController?.show(WindowInsets.Type.navigationBars())
                Log.i(TAG, "ready request=$requestId physicalDisplay=${target.displayId} size=${createdView.width}x${createdView.height}")
                runCatching { callback.onReady(requestId, sessionId, target.displayId) }
                    .onFailure { close(REASON_OWNER_DIED) }
            }
        } catch (error: Exception) {
            Log.e(TAG, "start failed physicalDisplay=${target.displayId}", error)
            if (owner != null) {
                close(REASON_START_FAILED)
            } else {
                KBoardOverlaySession.end(requestId)
                inputView?.let(ime::disposePhysicalOverlayInputView)
                runCatching { displays.unregisterDisplayListener(listener) }
                callback.asBinder().unlinkToDeath(death, 0)
                runCatching { callback.onClosed(requestId, sessionId, REASON_START_FAILED) }
            }
        }
    }

    private fun close(reason: Int) {
        val previous = owner ?: return
        owner = null
        KBoardOverlaySession.end(previous.requestId)
        runCatching { previous.windowManager.removeViewImmediate(previous.inputView) }
        previous.inputMethodService.disposePhysicalOverlayInputView(previous.inputView)
        previous.inputMethodService.finishPhysicalOverlayInput(previous.callerUid)
        displays.unregisterDisplayListener(previous.listener)
        previous.callback.asBinder().unlinkToDeath(previous.death, 0)
        Log.i(TAG, "closed request=${previous.requestId} physicalDisplay=${previous.targetDisplayId} reason=$reason")
        runCatching { previous.callback.onClosed(previous.requestId, previous.sessionId, reason) }
        releaseImeBootstrap()
    }

    private fun cancelPendingStart(reason: Int) {
        val pending = pendingStart ?: return
        pendingStart = null
        runCatching {
            pending.callback.onClosed(pending.requestId, pending.sessionId, reason)
        }
        if (reason != REASON_REPLACED) releaseImeBootstrap()
    }

    private fun releaseImeBootstrap() {
        if (!imeBootstrapBound) return
        imeBootstrapBound = false
        runCatching { unbindService(imeBootstrapConnection) }
    }

    private fun handOffBootstrapToSystemImeBinding() {
        if (!imeBootstrapBound) return
        handler.post {
            if (imeBootstrapBound) {
                Log.i(TAG, "handoff bootstrap lifetime to system IME binding")
                releaseImeBootstrap()
            }
        }
    }

    private fun switchPhysicalDisplay() {
        val previous = owner ?: return
        val nextDisplay = displays.getDisplay(previous.peerDisplayId) ?: return
        if (!nextDisplay.isValid || nextDisplay.state == Display.STATE_OFF) return
        val nextContext = createDisplayContext(nextDisplay)
        val nextWindowManager = nextContext.getSystemService(WindowManager::class.java)
        val nextView = try {
            previous.inputMethodService.createPhysicalOverlayInputView(
                nextContext, previous.requestId,
                previous.inputView.physicalOverlayLayoutName(), previous.callerUid
            )
        } catch (error: Exception) {
            Log.e(TAG, "switch create failed display=${nextDisplay.displayId}", error)
            return
        }
        nextView.setViewTreeLifecycleOwner(previous.inputMethodService)
        val width = nextDisplay.mode.physicalWidth
        val height = PhysicalOverlayWindowPolicy.resolveHeight(
            nextView.physicalOverlayLayoutName(),
            previous.requestedHeight,
            nextDisplay.mode.physicalHeight
        )
        val layoutParams = WindowManager.LayoutParams(
            width, height,
            PhysicalOverlayWindowPolicy.type,
            PhysicalOverlayWindowPolicy.flags, PhysicalOverlayWindowPolicy.format
        ).apply {
            gravity = PhysicalOverlayWindowPolicy.gravity
            title = "KBoardPhysicalKeyboard"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(PhysicalOverlayWindowPolicy.fitInsetsTypes)
            }
        }
        try {
            Log.i(
                TAG,
                "switch request=${previous.requestId} from=${previous.targetDisplayId} to=${nextDisplay.displayId} phase=attach"
            )
            nextWindowManager.addView(nextView, layoutParams)
            owner = previous.copy(
                targetDisplayId = nextDisplay.displayId,
                peerDisplayId = previous.targetDisplayId,
                windowWidth = width,
                windowManager = nextWindowManager,
                inputView = nextView
            )
            runCatching { previous.windowManager.removeViewImmediate(previous.inputView) }
            previous.inputMethodService.disposePhysicalOverlayInputView(previous.inputView)
            nextView.post {
                if (owner?.inputView !== nextView) return@post
                nextView.windowInsetsController?.show(WindowInsets.Type.navigationBars())
                Log.i(
                    TAG,
                    "switch request=${previous.requestId} from=${previous.targetDisplayId} " +
                        "to=${nextDisplay.displayId} phase=ready size=${nextView.width}x${nextView.height}"
                )
                runCatching {
                    previous.callback.onReady(
                        previous.requestId, previous.sessionId, nextDisplay.displayId
                    )
                }.onFailure { close(REASON_OWNER_DIED) }
            }
        } catch (error: Exception) {
            Log.e(TAG, "switch attach failed display=${nextDisplay.displayId}", error)
            runCatching { nextWindowManager.removeViewImmediate(nextView) }
            previous.inputMethodService.disposePhysicalOverlayInputView(nextView)
        }
    }

    private fun migratePhysicalOverlayToIme(currentIme: FcitxInputMethodService) {
        val previous = owner ?: return
        val ownerGeneration = System.identityHashCode(previous.inputMethodService)
        val currentGeneration = System.identityHashCode(currentIme)
        if (!OverlayImeGenerationPolicy.shouldMigrate(ownerGeneration, currentGeneration)) return
        val layoutName = previous.inputView.physicalOverlayLayoutName()
        val replacement = try {
            currentIme.createPhysicalOverlayInputView(
                previous.inputView.context,
                previous.requestId,
                layoutName,
                previous.callerUid
            )
        } catch (error: Exception) {
            Log.e(
                TAG,
                "ime-generation migration create failed request=${previous.requestId} " +
                    "from=$ownerGeneration to=$currentGeneration",
                error
            )
            close(REASON_START_FAILED)
            return
        }
        replacement.setViewTreeLifecycleOwner(currentIme)
        val layoutParams = WindowManager.LayoutParams(
            previous.windowWidth,
            PhysicalOverlayWindowPolicy.resolveHeight(
                layoutName,
                previous.requestedHeight,
                previous.inputView.display?.mode?.physicalHeight
                    ?: previous.inputView.resources.displayMetrics.heightPixels
            ),
            PhysicalOverlayWindowPolicy.type,
            PhysicalOverlayWindowPolicy.flags,
            PhysicalOverlayWindowPolicy.format
        ).apply {
            gravity = PhysicalOverlayWindowPolicy.gravity
            title = "KBoardPhysicalKeyboard"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(PhysicalOverlayWindowPolicy.fitInsetsTypes)
            }
        }
        try {
            previous.windowManager.addView(replacement, layoutParams)
            if (owner !== previous) {
                previous.windowManager.removeViewImmediate(replacement)
                currentIme.disposePhysicalOverlayInputView(replacement)
                return
            }
            owner = previous.copy(inputMethodService = currentIme, inputView = replacement)
            runCatching { previous.windowManager.removeViewImmediate(previous.inputView) }
            previous.inputMethodService.disposePhysicalOverlayInputView(previous.inputView)
            replacement.post {
                if (owner?.inputView !== replacement) return@post
                replacement.windowInsetsController?.show(WindowInsets.Type.navigationBars())
                Log.i(
                    TAG,
                    "ime-generation migration ready request=${previous.requestId} " +
                        "display=${previous.targetDisplayId} from=$ownerGeneration to=$currentGeneration"
                )
            }
        } catch (error: Exception) {
            Log.e(
                TAG,
                "ime-generation migration attach failed request=${previous.requestId} " +
                    "from=$ownerGeneration to=$currentGeneration",
                error
            )
            runCatching { previous.windowManager.removeViewImmediate(replacement) }
            currentIme.disposePhysicalOverlayInputView(replacement)
            close(REASON_START_FAILED)
        }
    }

    private fun enforceOwner() {
        val packages = packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (packages.none { it in KEMI_PACKAGES && hasExpectedSignature(it) }) {
            throw SecurityException("KBoard overlay caller is not the signed KEMI client")
        }
    }


    private fun hasExpectedSignature(packageName: String): Boolean {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return info.signingInfo?.apkContentsSigners?.any { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) } in KEMI_SIGNER_SHA256
        } == true
    }

    companion object {
        private var currentService: WeakReference<KBoardOverlayService>? = null

        internal fun onSystemImeBound() {
            currentService?.get()?.handOffBootstrapToSystemImeBinding()
        }

        internal fun onImeGenerationAvailable(ime: FcitxInputMethodService) {
            currentService?.get()?.let { service ->
                service.handler.post { service.migratePhysicalOverlayToIme(ime) }
            }
        }

        const val EXTRA_REQUEST_ID = "requestId"
        const val REASON_HIDDEN = 1; const val REASON_REPLACED = 2
        const val REASON_OWNER_DIED = 3; const val REASON_DISPLAY_REMOVED = 4
        const val REASON_SURFACE_LOST = 5; const val REASON_START_FAILED = 6
        const val REASON_SERVICE_STOPPED = 7; const val REASON_READY_TIMEOUT = 8
        const val REASON_REGULAR_INPUT = 9
        const val REASON_SYSTEM_NAVIGATION = 10
        const val CAPABILITY_BIDIRECTIONAL_OVERLAY = 1
        const val CAPABILITY_LOCAL_COMPOSITION = 1 shl 1
        const val CAPABILITY_REMOTE_MOUSE = 1 shl 2
        private const val TAG = "KBoardPhysicalOverlay"
        private const val SYSTEM_DIALOG_REASON_KEY = "reason"
        private const val ACTION_BOOTSTRAP_IME =
            "org.fcitx.fcitx5.android.input.overlay.BOOTSTRAP_IME"
        private const val IME_BOOTSTRAP_MAX_ATTEMPTS = 40
        private const val IME_BOOTSTRAP_RETRY_MS = 50L
        private val KEMI_PACKAGES = setOf(
            "com.newlinksz.kemi.remote",
            "com.vibekits.vibekits",
        )
        private val KEMI_SIGNER_SHA256 = setOf(
            "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8",
            "8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2"
        )
    }
}
