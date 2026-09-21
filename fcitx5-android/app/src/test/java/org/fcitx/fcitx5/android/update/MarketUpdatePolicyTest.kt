/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.update

import org.junit.Assert.*
import org.junit.Test

class MarketUpdatePolicyTest {
    private val pkg = "com.newlink.kemi.kboard"
    private val valid = """{"status":200,"data":{"package_name":"$pkg","has_update":true,
        "app_id":12,"version_name":"1.4.3","version_code":172,"local_version_code":162,
        "list_in_store":true,"deeplink":"kemiappstore://app/12","force_update":false,"release_notes":"Fix input"}}"""

    private fun rejected(body: String) {
        assertThrows(Exception::class.java) { MarketUpdatePolicy.parse(body, pkg, 162) }
    }

    @Test fun higherVersionRoutesToExactStoreDetailWithoutForceUpdate() {
        val update = MarketUpdatePolicy.parse(valid, pkg, 162)!!
        assertEquals(172L, update.versionCode)
        assertEquals("kemiappstore://app/12", update.deeplink)
        assertEquals("Fix input", update.notes)
    }
    @Test fun noUpdateReturnsNull() {
        assertNull(MarketUpdatePolicy.parse("""{"status":200,"data":{"package_name":"$pkg","has_update":false}}""", pkg, 162))
    }
    @Test fun businessErrorIsNotNoUpdate() = rejected(valid.replace("\"status\":200", "\"status\":400"))
    @Test fun malformedEnvelopeRejected() {
        for (body in listOf("not-json", "[]", "{}", "{\"status\":200,\"data\":null}")) rejected(body)
    }
    @Test fun wrongPackageOrPlatformRejected() {
        rejected(valid.replace(pkg, "com.other.app"))
        rejected(valid.replace("\"app_id\":12", "\"app_id\":12,\"os_type\":\"windows\""))
    }
    @Test fun sameOrLowerVersionRejectedEvenIfServerSaysUpdate() {
        rejected(valid.replace("\"version_code\":172", "\"version_code\":162"))
        rejected(valid.replace("\"version_code\":172", "\"version_code\":152"))
    }
    @Test fun invalidOrMismatchedLinkRejected() {
        for (link in listOf("https://evil.example", "intent://app/12", "kemiappstore://app/13", "kemiappstore://app/12?redirect=x")) {
            rejected(valid.replace("kemiappstore://app/12", link))
        }
    }
    @Test fun hiddenRecordAndShellFallbackNotAccepted() = rejected(valid.replace("\"list_in_store\":true", "\"list_in_store\":false"))
    @Test fun invalidIdOrBooleanRejected() {
        rejected(valid.replace("\"app_id\":12", "\"app_id\":0"))
        rejected(valid.replace("\"has_update\":true", "\"has_update\":\"true\""))
    }
    @Test fun unknownFieldsAndDecimalStringNumbersAccepted() {
        assertNotNull(MarketUpdatePolicy.parse(valid.replace("\"app_id\":12", "\"app_id\":\"12\",\"future_field\":true"), pkg, 162))
    }
    @Test fun ignoredVersionStaysQuietButNextVersionPrompts() {
        assertFalse(MarketUpdatePolicy.shouldPrompt(172, 162, 172))
        assertFalse(MarketUpdatePolicy.shouldPrompt(162, 162, 0))
        assertTrue(MarketUpdatePolicy.shouldPrompt(182, 162, 172))
    }
    @Test fun failureBackoffIsBoundedAndIncreasing() {
        assertEquals(15 * 60 * 1000L, MarketUpdatePolicy.failureIntervalMs(0))
        assertTrue(MarketUpdatePolicy.failureIntervalMs(1) > MarketUpdatePolicy.failureIntervalMs(0))
        assertEquals(MarketUpdatePolicy.SUCCESS_INTERVAL_MS, MarketUpdatePolicy.failureIntervalMs(100))
    }
}
