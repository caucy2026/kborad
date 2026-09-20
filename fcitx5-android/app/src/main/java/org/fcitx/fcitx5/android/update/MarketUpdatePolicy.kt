/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

data class MarketUpdate(val versionCode: Long, val versionName: String, val appId: Long, val notes: String) {
    val deeplink: String get() = "kemiappstore://app/$appId"
}

/** Pure contract validation: never accepts an arbitrary server-provided Intent or download URL. */
object MarketUpdatePolicy {
    const val MARKET_PACKAGE = "com.newlink.featuredapps"
    const val MAX_RESPONSE_BYTES = 65_536L
    const val SUCCESS_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun parse(body: String, packageName: String, localCode: Long): MarketUpdate? {
        val root = Json.parseToJsonElement(body) as? JsonObject ?: error("invalid_envelope")
        require(root.number("status") == 200L) { "business_error" }
        val data = root["data"] as? JsonObject ?: error("invalid_data")
        require(data.text("package_name") == packageName) { "wrong_package" }
        if ("os_type" in data) require(data.text("os_type") == "android") { "wrong_platform" }
        if ("local_version_code" in data) require(data.number("local_version_code") == localCode) { "wrong_local_version" }
        val hasUpdate = (data["has_update"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
            ?: error("invalid_update_flag")
        if (!hasUpdate) return null
        val code = data.number("version_code") ?: error("missing_version")
        require(code > localCode) { "non_increasing_version" }
        val id = data.number("app_id") ?: error("missing_app_id")
        require(id > 0) { "invalid_app_id" }
        require((data["list_in_store"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true) { "not_in_store" }
        require(data.text("deeplink") == "kemiappstore://app/$id") { "invalid_deeplink" }
        val name = data.text("version_name") ?: error("missing_version_name")
        require(name.isNotBlank() && name.length <= 64 && name.none { it.isISOControl() }) { "invalid_version_name" }
        // force_update does not authorize blocking an input method or installing anything.
        return MarketUpdate(code, name, id, data.text("release_notes").orEmpty().take(2000))
    }

    fun shouldPrompt(remoteCode: Long, localCode: Long, ignoredCode: Long) =
        remoteCode > localCode && remoteCode > ignoredCode

    fun failureIntervalMs(failures: Int): Long =
        (15 * 60 * 1000L * (1L shl failures.coerceIn(0, 5))).coerceAtMost(SUCCESS_INTERVAL_MS)

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
