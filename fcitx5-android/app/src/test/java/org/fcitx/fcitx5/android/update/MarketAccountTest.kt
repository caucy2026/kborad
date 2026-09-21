package org.fcitx.fcitx5.android.update

import org.junit.Assert.*
import org.junit.Test

class MarketAccountTest {
    @Test fun readsOnlyPositiveCurrentUserId() {
        assertEquals(42L, MarketAccount.parseUserId("""{"userId":42,"token":"unused"}"""))
        assertEquals(42L, MarketAccount.parseUserId("""{"userId":"42"}"""))
    }
    @Test fun missingInvalidAndOversizedAccountsRemainAnonymous() {
        for (body in listOf(null, "", "[]", "{}", "bad", """{"userId":0}""",
            """{"userId":-1}""", """{"userId":1.5}""", """{"userId":true}""", " ".repeat(65_537))) {
            assertNull(MarketAccount.parseUserId(body))
        }
    }
}
