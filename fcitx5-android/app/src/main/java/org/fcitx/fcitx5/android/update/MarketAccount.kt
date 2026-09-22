/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.update

import android.content.Context
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Read-only subset of the official kemi-auth SDK 1.0.0 Provider contract.
 * Never reads private app files, writes accounts, launches login, or retains tokens.
 */
object MarketAccount {
    private const val AUTHORITY = "com.kemi.auth.accounts"
    // Public Android client registered for com.newlink.kemi.kboard; not a secret.
    private const val CLIENT_ID = "kemi_and_a4bea8b8e361a70827377c81"

    suspend fun activeUserId(context: Context): Long? = withContext(Dispatchers.IO) {
        try {
            val provider = context.packageManager.resolveContentProvider(AUTHORITY, 0)
            if (provider?.packageName != MarketUpdatePolicy.MARKET_PACKAGE) return@withContext null
            val result = context.contentResolver.call(
                Uri.parse("content://$AUTHORITY/accounts"), "get_active", null,
                Bundle().apply { putString("client_id", CLIENT_ID) }
            )
            parseUserId(result?.getString("account_json"))
        } catch (_: Exception) {
            // Missing/old store, signed-out account or denied caller: public updates remain usable.
            null
        }
    }

    internal fun parseUserId(body: String?): Long? {
        if (body == null || body.length > 65_536) return null
        return runCatching {
            val account = Json.parseToJsonElement(body) as? JsonObject
            (account?.get("userId") as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
        }.getOrNull()
    }
}
