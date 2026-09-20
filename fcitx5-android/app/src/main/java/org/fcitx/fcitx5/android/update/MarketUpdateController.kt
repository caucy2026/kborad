/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.versionCodeCompat
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Settings-foreground only: never starts an Activity or steals focus while another app is typing. */
class MarketUpdateController(private val activity: AppCompatActivity) {
    private val prefs = activity.getSharedPreferences("kemi_market_update", Context.MODE_PRIVATE)
    private val current = MutableStateFlow<MarketVersionState>(restore())
    val state = current.asStateFlow()
    private var promptedCode: Long? = null

    private fun restore(): MarketVersionState = runCatching {
        val age = System.currentTimeMillis() - prefs.getLong("checked_at", 0)
        if (age !in 0 until MarketUpdatePolicy.SUCCESS_INTERVAL_MS) return MarketVersionState.Unknown
        val body = prefs.getString("checked_body", null) ?: return MarketVersionState.Unknown
        val code = activity.packageManager.getPackageInfo(activity.packageName, 0).versionCodeCompat
        if (prefs.getLong("checked_local_code", -1) != code) return MarketVersionState.Unknown
        MarketUpdatePolicy.parse(body, activity.packageName, code)?.let { MarketVersionState.Available(it) }
            ?: MarketVersionState.Latest
    }.getOrDefault(MarketVersionState.Unknown)

    fun onVersionClicked(owner: LifecycleOwner) {
        when (val value = current.value) {
            is MarketVersionState.Available -> openStore(value.update)
            MarketVersionState.Latest -> message(R.string.market_update_latest)
            MarketVersionState.Checking -> message(R.string.market_update_checking)
            else -> owner.lifecycleScope.launch {
                message(R.string.market_update_checking)
                val code = activity.packageManager.getPackageInfo(activity.packageName, 0).versionCodeCompat
                check(code, force = true)
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                when (val result = current.value) {
                    is MarketVersionState.Available -> openStore(result.update)
                    MarketVersionState.Latest -> message(R.string.market_update_latest)
                    else -> message(R.string.market_update_failed)
                }
            }
        }
    }

    private fun message(resource: Int) = Toast.makeText(activity, resource, Toast.LENGTH_SHORT).show()

    fun start() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var dialog: AlertDialog? = null
                try {
                    val localCode = activity.packageManager.getPackageInfo(activity.packageName, 0).versionCodeCompat
                    check(localCode)
                    val update = (current.value as? MarketVersionState.Available)?.update ?: return@repeatOnLifecycle
                    if (!MarketUpdatePolicy.shouldPrompt(update.versionCode, localCode, prefs.getLong("ignored_code", 0))) {
                        return@repeatOnLifecycle
                    }
                    if (promptedCode == update.versionCode) return@repeatOnLifecycle
                    // Do not stack on notification permission, dictionary import, or setup dialogs.
                    while (!activity.hasWindowFocus()) delay(300)
                    delay(300)
                    if (!activity.hasWindowFocus() || activity.isFinishing) return@repeatOnLifecycle
                    dialog = AlertDialog.Builder(activity)
                        .setTitle(R.string.market_update_title)
                        .setMessage(activity.getString(R.string.market_update_message, update.versionName) +
                            if (update.notes.isBlank()) "" else "\n\n${update.notes}")
                        .setNegativeButton(R.string.market_update_later) { _, _ -> ignore(update) }
                        .setPositiveButton(R.string.market_update_open_store) { _, _ ->
                            openStore(update)
                        }
                        .setOnCancelListener { ignore(update) }
                        .show()
                    promptedCode = update.versionCode
                    Timber.i("KBoard update prompt shown")
                    awaitCancellation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Never log server bodies, query parameters, account data, or exception messages.
                    Timber.w("KBoard update check failed; keyboard remains available")
                } finally {
                    dialog?.dismiss()
                }
            }
        }
    }

    private suspend fun check(localCode: Long, force: Boolean = false) {
        if (current.value == MarketVersionState.Checking) return
        val now = System.currentTimeMillis()
        val next = prefs.getLong("next_check", 0)
        if (!force && next > now && next - now <= MarketUpdatePolicy.SUCCESS_INTERVAL_MS) return
        current.value = MarketVersionState.Checking
        try {
        val failures = prefs.getInt("failures", 0).coerceIn(0, 5)
        // Persist a bounded backoff before the call, including interrupted requests/process restarts.
        prefs.edit().putLong("next_check", now + MarketUpdatePolicy.failureIntervalMs(failures))
            .putInt("failures", (failures + 1).coerceAtMost(5)).apply()
        val bodyText = withContext(Dispatchers.IO) {
            val url = "https://kemi.newlinksz.com/kd-api/api/store/update/check".toHttpUrl().newBuilder()
                .addQueryParameter("package_name", activity.packageName)
                .addQueryParameter("version_code", localCode.toString())
                .addQueryParameter("os", "android").build()
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                require(response.isSuccessful) { "http_error" }
                val body = response.body ?: error("empty_body")
                require(body.contentLength() <= MarketUpdatePolicy.MAX_RESPONSE_BYTES) { "oversized_body" }
                val source = body.source()
                source.request(MarketUpdatePolicy.MAX_RESPONSE_BYTES + 1)
                require(source.buffer.size <= MarketUpdatePolicy.MAX_RESPONSE_BYTES) { "oversized_body" }
                source.readUtf8()
            }
        }
        val update = MarketUpdatePolicy.parse(bodyText, activity.packageName, localCode)
        prefs.edit().putLong("next_check", System.currentTimeMillis() + MarketUpdatePolicy.SUCCESS_INTERVAL_MS)
            .putInt("failures", 0).putLong("checked_at", System.currentTimeMillis())
            .putLong("checked_local_code", localCode).putString("checked_body", bodyText).apply()
        current.value = update?.let { MarketVersionState.Available(it) } ?: MarketVersionState.Latest
        Timber.i("KBoard update check completed: available=%s", update != null)
        } catch (cancelled: CancellationException) {
            current.value = MarketVersionState.Unknown
            throw cancelled
        } catch (_: Exception) {
            prefs.edit().remove("checked_body").remove("checked_at").apply()
            current.value = MarketVersionState.Failed
            Timber.w("KBoard update check failed; keyboard remains available")
        }
    }

    private fun ignore(update: MarketUpdate) {
        prefs.edit().putLong("ignored_code", update.versionCode).apply()
    }

    private fun openStore(update: MarketUpdate) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.deeplink))
            .setPackage(MarketUpdatePolicy.MARKET_PACKAGE)
        try {
            // Explicit package launch also handles package-visibility restrictions; catch shell apps.
            activity.startActivity(intent)
            Timber.i("KBoard update opened KEMI Store")
        } catch (_: ActivityNotFoundException) {
            storeUnavailable()
        } catch (_: SecurityException) {
            storeUnavailable()
        }
    }

    private fun storeUnavailable() {
        Toast.makeText(activity, R.string.market_update_store_unavailable, Toast.LENGTH_LONG).show()
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    }
}
