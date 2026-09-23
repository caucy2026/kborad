/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.voice

import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ProtocolException
import java.net.UnknownServiceException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun newAsrAuthHttpClient(callTimeoutMs: Long): OkHttpClient = OkHttpClient.Builder()
    // The auth endpoint occasionally leaves an idle HTTP socket open without answering
    // its next request. A fresh connection costs ~10 ms on H730 and avoids the 2.5 s stall.
    .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
    .connectTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
    .readTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
    .writeTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
    .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .build()

internal data class AsrAuthResponse(val code: Int, val body: String)

/** One press owns one cancellable authorization operation. No credentials are cached here. */
internal class AsrAuthRequest(
    private val calls: Call.Factory,
    private val request: () -> Request,
    private val complete: (Result<AsrAuthResponse>) -> Unit,
    private val trace: (String) -> Unit = {}
) {
    private val lock = Any()
    @Volatile private var cancelled = false
    private var started = false
    private var currentCall: Call? = null

    fun start() {
        synchronized(lock) {
            if (started || cancelled) return
            started = true
        }
        executor.execute {
            for (attempt in 1..MAX_ATTEMPTS) {
                val call = synchronized(lock) {
                    if (cancelled) return@execute
                    calls.newCall(request()).also { currentCall = it }
                }
                trace("auth_attempt=$attempt")
                val result = try {
                    // Like the desktop assistant: HTTP on a worker, consume/close the response
                    // fully before the caller can start its separate WebSocket connection.
                    Result.success(call.execute().use { response ->
                        AsrAuthResponse(response.code, response.body?.string().orEmpty())
                    })
                } catch (error: IOException) {
                    trace("auth_transport=${error.javaClass.simpleName}")
                    Result.failure(error)
                } catch (error: RuntimeException) {
                    // Android can throw SecurityException for a missing INTERNET grant.
                    // This is terminal, not a transport retry, and must reach the UI.
                    trace("auth_error=${error.javaClass.simpleName}")
                    Result.failure(error)
                }
                synchronized(lock) {
                    currentCall = null
                    if (cancelled) return@execute
                }
                val error = result.exceptionOrNull()
                val retryable = (error is IOException && error !is UnknownServiceException &&
                    error !is ProtocolException && error !is javax.net.ssl.SSLException) ||
                    result.getOrNull()?.code in listOf(502, 503, 504)
                if (retryable && attempt < MAX_ATTEMPTS) continue
                // The consumer must still check its session token atomically with state changes:
                // cancellation may race this delivery. Never invoke consumer code under lock.
                if (!cancelled) complete(result)
                return@execute
            }
        }
    }

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            currentCall?.cancel()
            currentCall = null
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "iflytek-auth").apply { isDaemon = true }
        }
    }
}
