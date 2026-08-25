/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.daemon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Fcitx
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxLifecycle
import org.fcitx.fcitx5.android.core.lifeCycleScope
import org.fcitx.fcitx5.android.core.whenReady
import org.fcitx.fcitx5.android.daemon.FcitxDaemon.connect
import org.fcitx.fcitx5.android.daemon.FcitxDaemon.disconnect
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.notificationManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manage the singleton instance of [Fcitx]
 *
 * To use fcitx, client should call [connect] to obtain a [FcitxConnection],
 * and call [disconnect] on client destroyed. Client should not leak the instance of [FcitxAPI],
 * and must use [FcitxConnection] to access fcitx functionalities.
 *
 * The instance of [Fcitx] always exists,but whether the dispatcher runs and callback works depend on clients, i.e.
 * if no clients are connected, [Fcitx.stop] will be called.
 *
 * Functions are thread-safe in this class.
 */
object FcitxDaemon {

    class DisconnectedException(name: String) :
        IllegalStateException("$name is disconnected")

    private val realFcitx by lazy { Fcitx(appContext) }

    // don't leak fcitx instance
    private val fcitxImpl by lazy { object : FcitxAPI by realFcitx {} }

    private fun mkConnection(name: String): FcitxConnection {
        lateinit var connection: FcitxConnection
        connection = object : FcitxConnection {

            private fun ensureConnected() {
                if (!isConnected(name, connection)) throw DisconnectedException(name)
            }

            override fun <T> runImmediately(block: suspend FcitxAPI.() -> T): T {
                ensureConnected()
                return runBlocking(realFcitx.lifeCycleScope.coroutineContext) {
                    // disconnect() can run while dispatching onto the native lifecycle thread.
                    ensureConnected()
                    block(fcitxImpl)
                }
            }

            override suspend fun <T> runOnReady(block: suspend FcitxAPI.() -> T): T {
                ensureConnected()
                return realFcitx.lifecycle.whenReady {
                    // A client can be retired while whenReady() is suspended.
                    ensureConnected()
                    block(fcitxImpl)
                }
            }

            override fun runIfReady(block: suspend FcitxAPI.() -> Unit) {
                if (!isConnected(name, connection)) {
                    Timber.d("Ignore runIfReady for disconnected client $name")
                    return
                }
                if (realFcitx.isReady) {
                    realFcitx.lifeCycleScope.launch {
                        // The client can disconnect while this launch waits for dispatch.
                        if (isConnected(name, connection)) {
                            block(fcitxImpl)
                        }
                    }
                }
            }

            override val lifecycleScope: CoroutineScope
                get() = realFcitx.lifecycle.lifecycleScope

        }
        return connection
    }

    private fun isConnected(name: String, connection: FcitxConnection): Boolean =
        clients[name] === connection

    private val lock = ReentrantLock()

    // Connection calls execute on both Android and native lifecycle threads. Reads must not take
    // [lock], because stop/restart holds that lock while native teardown can complete callbacks.
    // A concurrent identity lookup prevents both data races and lock inversion.
    private val clients = ConcurrentHashMap<String, FcitxConnection>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopIfUnused = Runnable {
        lock.withLock {
            if (clients.isEmpty() && realFcitx.lifecycle.currentState == FcitxLifecycle.State.READY) {
                Timber.d("FcitxDaemon stop fcitx after disconnect grace period")
                realFcitx.stop()
            }
        }
    }

    /**
     * Create a connection
     */
    fun connect(name: String): FcitxConnection = lock.withLock {
        mainHandler.removeCallbacks(stopIfUnused)
        if (clients.containsKey(name))
            return@withLock clients.getValue(name)
        if (realFcitx.lifecycle.currentState == FcitxLifecycle.State.STOPPED) {
            Timber.d("FcitxDaemon start fcitx")
            realFcitx.start()
        }
        val new = mkConnection(name)
        clients[name] = new
        return@withLock new
    }

    /**
     * Dispose the connection
     */
    fun disconnect(name: String): Unit = lock.withLock {
        if (!clients.containsKey(name))
            return
        clients -= name
        if (clients.isEmpty()) {
            mainHandler.removeCallbacks(stopIfUnused)
            mainHandler.postDelayed(stopIfUnused, DISCONNECT_GRACE_PERIOD_MS)
        }
    }

    /**
     * Restart fcitx instance while keep the clients connected
     */
    fun restartFcitx() = lock.withLock {
        val id = RESTART_ID++
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_sync_24)
            .setContentTitle(appContext.getString(R.string.fcitx_daemon))
            .setContentText(appContext.getString(R.string.restarting_fcitx))
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build().let { appContext.notificationManager.notify(id, it) }
        realFcitx.stop()
        realFcitx.start()
        FcitxApplication.getInstance().coroutineScope.launch {
            // cancel notification on ready
            realFcitx.lifecycle.whenReady {
                appContext.notificationManager.cancel(id)
            }
        }
    }

    /**
     * Stop fcitx instance regardless of connected clients.
     * Should only be used before importing user configuration files,
     * then the App must be restarted as soon as possible.
     *
     * This method blocks until fully stopped.
     */
    fun stopFcitx() {
        realFcitx.stop()
    }

    /**
     * Start fcitx instance.
     * Should only be used when it has been stopped **AND** user data importing failed.
     */
    fun startFcitx() {
        realFcitx.start()
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getText(R.string.fcitx_daemon),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = CHANNEL_ID }
            appContext.notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Reuse a connection for remote service
     */
    fun getFirstConnectionOrNull() = lock.withLock {
        clients.firstNotNullOfOrNull { it.value }
    }


    private const val CHANNEL_ID = "fcitx-daemon"
    private const val DISCONNECT_GRACE_PERIOD_MS = 2_000L
    private var RESTART_ID = 0

}
