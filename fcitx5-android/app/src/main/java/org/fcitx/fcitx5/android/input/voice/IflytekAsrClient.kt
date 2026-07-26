/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max

class IflytekAsrClient(
    private val context: Context,
    private val onStateChanged: (State) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPartial: (String) -> Unit = {}
) {

    enum class State { Idle, Starting, Listening, Finishing }

    private data class Params(
        val token: String,
        val appId: String,
        val apiKey: String,
        val authId: String,
        val wifiMac: String,
        val sn: String,
        val systemVersion: String
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    var state = State.Idle
        private set

    private var generation = 0
    private var authCall: Call? = null
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var confirmedText = ""
    private var latestText = ""

    @Synchronized
    fun start() {
        if (state != State.Idle) return
        val params = runCatching { readParams() }.getOrElse {
            fail(it.message ?: "missing iflytek_params")
            return
        }
        generation += 1
        val session = generation
        confirmedText = ""
        latestText = ""
        updateState(State.Starting)
        authenticate(params, session)
    }

    @Synchronized
    fun stop() {
        when (state) {
            State.Idle -> return
            State.Listening -> {
                updateState(State.Finishing)
                stopAudio()
                webSocket?.send(END_FLAG)
                val session = generation
                mainHandler.postDelayed({
                    if (generation == session && state == State.Finishing) {
                        finish(combineText(confirmedText, latestText))
                    }
                }, FINAL_TIMEOUT_MS)
            }
            State.Starting, State.Finishing -> cancel()
        }
    }

    @Synchronized
    fun cancel() {
        generation += 1
        authCall?.cancel()
        authCall = null
        stopAudio()
        webSocket?.cancel()
        webSocket = null
        confirmedText = ""
        latestText = ""
        updateState(State.Idle)
    }

    private fun readParams(): Params {
        val raw = Settings.Global.getString(context.contentResolver, SETTINGS_KEY)
            ?: error("iflytek_params is unavailable")
        val json = JSONObject(raw)
        fun required(name: String) = json.optString(name).takeIf { it.isNotBlank() }
            ?: error("iflytek_params.$name is missing")
        return Params(
            required("token"),
            required("app_id"),
            required("api_key"),
            required("auth_id"),
            required("wifi_mac").uppercase(),
            json.optString("sn").ifBlank { "QUALMETA-${required("wifi_mac").uppercase()}" },
            json.optString("system_version").ifBlank { "V1.0.1.2:2026-06-02:V1.4.4" }
        )
    }

    private fun authenticate(params: Params, session: Int) {
        val body = JSONObject()
            .put("xiriSn", params.wifiMac)
            .put("license", params.token)
            .put("channel", "NEWLINK01")
            .put("devicewifiMac", params.wifiMac)
            .put("deviceMac", params.wifiMac)
            .put("sn", params.sn)
            .put("clientVersion", params.systemVersion)
            .put("timestamp", System.currentTimeMillis().toString())
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(AUTH_URL).post(body).build()
        authCall = httpClient.newCall(request).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (isCurrent(session)) fail(e.message ?: "authentication failed")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!isCurrent(session)) return
                        val json = runCatching { JSONObject(response.body?.string().orEmpty()) }
                            .getOrElse {
                                fail("invalid authentication response")
                                return
                            }
                        if (json.optString("code") != "00000" ||
                            json.optJSONObject("data")?.optInt("status", -1) != 0
                        ) {
                            fail(json.optJSONObject("data")?.optString("msg")
                                ?.takeIf(String::isNotBlank) ?: "authentication rejected")
                            return
                        }
                        Timber.i("iFlytek ASR authentication succeeded")
                        connectWebSocket(params, session)
                    }
                }
            })
        }
    }

    private fun connectWebSocket(params: Params, session: Int) {
        val curTime = (System.currentTimeMillis() / 1000).toString()
        val param = JSONObject()
            .put("result_level", "plain")
            .put("auth_id", params.authId)
            .put("data_type", "audio")
            .put("aue", "raw")
            .put("scene", "main")
            .put("sample_rate", "16000")
            .put("dwa", "wpgs")
            .put("cloud_vad_eos", "60000")
            .toString()
        val encodedParam = Base64.encodeToString(param.toByteArray(), Base64.NO_WRAP)
        val checksum = sha256(params.apiKey + curTime + encodedParam)
        val url = "$WS_URL?appid=${params.appId}&checksum=$checksum&curtime=$curTime" +
            "&param=${URLEncoder.encode(encodedParam, Charsets.UTF_8.name())}&signtype=sha256"
        val request = Request.Builder()
            .url(url)
            .header("Origin", WS_ORIGIN)
            .build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(session)) return
                val json = runCatching { JSONObject(text) }.getOrElse {
                    fail("invalid recognition response")
                    return
                }
                if (json.optInt("code", 0) != 0) {
                    fail(json.optString("desc").ifBlank { "recognition error ${json.optInt("code")}" })
                    return
                }
                when (json.optString("action")) {
                    "started" -> {
                        Timber.i("iFlytek ASR WebSocket started")
                        startAudio(webSocket, session)
                    }
                    "result" -> {
                        val data = json.optJSONObject("data") ?: return
                        data.optString("text").takeIf(String::isNotBlank)?.let { latestText = it }
                        val segmentFinished = data.optBoolean("is_finish")
                        val streamFinished = data.optBoolean("is_last")
                        if (state == State.Listening && (segmentFinished || streamFinished)) {
                            confirmedText = combineText(confirmedText, latestText)
                            latestText = ""
                            publishPartial(confirmedText)
                        } else if (state == State.Finishing && (segmentFinished || streamFinished)) {
                            finish(combineText(confirmedText, latestText))
                        } else {
                            publishPartial(combineText(confirmedText, latestText))
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isCurrent(session)) fail(t.message ?: "WebSocket failed")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun startAudio(socket: WebSocket, session: Int) {
        if (!isCurrent(session) || state != State.Starting) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(AUDIO_CHUNK_BYTES, minBuffer)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (error: RuntimeException) {
            fail(error.message ?: "microphone unavailable")
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            fail("microphone initialization failed")
            return
        }
        audioRecord = recorder
        recorder.startRecording()
        updateState(State.Listening)
        audioThread = Thread({
            val buffer = ByteArray(AUDIO_CHUNK_BYTES)
            while (isCurrent(session) && state == State.Listening) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count > 0 && !socket.send(buffer.toByteString(0, count))) break
            }
        }, "iflytek-asr-audio").apply { start() }
    }

    @Synchronized
    private fun finish(text: String) {
        if (state == State.Idle) return
        generation += 1
        stopAudio()
        webSocket?.close(1000, null)
        webSocket = null
        authCall = null
        confirmedText = ""
        latestText = ""
        updateState(State.Idle)
        if (text.isNotBlank()) {
            Timber.i("iFlytek ASR final text length=${text.length}")
            mainHandler.post { onFinal(text) }
        }
    }

    @Synchronized
    private fun fail(message: String) {
        Timber.w("iFlytek ASR: $message")
        generation += 1
        authCall?.cancel()
        authCall = null
        stopAudio()
        webSocket?.cancel()
        webSocket = null
        confirmedText = ""
        latestText = ""
        updateState(State.Idle)
        mainHandler.post { onError(message) }
    }

    private fun stopAudio() {
        val recorder = audioRecord
        audioRecord = null
        runCatching { recorder?.stop() }
        recorder?.release()
        audioThread = null
    }

    @Synchronized
    private fun isCurrent(session: Int) = session == generation && state != State.Idle

    private fun updateState(newState: State) {
        state = newState
        mainHandler.post { onStateChanged(newState) }
    }

    private fun publishPartial(text: String) {
        if (text.isNotBlank()) {
            mainHandler.post { onPartial(text) }
        }
    }

    private fun combineText(confirmed: String, current: String): String = when {
        current.isBlank() -> confirmed
        confirmed.isBlank() -> current
        current.startsWith(confirmed) -> current
        confirmed.endsWith(current) -> confirmed
        else -> confirmed + current
    }

    private fun sha256(input: String) = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SETTINGS_KEY = "iflytek_params"
        const val AUTH_URL = "http://api.voice.gskiot.com/voice-api/voice/auth"
        const val WS_URL = "ws://wsapi.xfyun.cn/v1/aiui"
        const val WS_ORIGIN = "http://wsapi.xfyun.cn"
        const val SAMPLE_RATE = 16000
        // v1 latency tune: 1600B @16k/16bit/mono ~= 50ms per packet.
        const val AUDIO_CHUNK_BYTES = 1600
        const val FINAL_TIMEOUT_MS = 5000L
        const val END_FLAG = "--end--"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}