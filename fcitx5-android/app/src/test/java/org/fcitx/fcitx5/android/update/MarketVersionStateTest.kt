package org.fcitx.fcitx5.android.update

import org.junit.Assert.*
import org.junit.Test

class MarketVersionStateTest {
    @Test fun onlyAvailableShowsBadge() {
        assertFalse(MarketVersionState.Unknown.showBadge)
        assertFalse(MarketVersionState.Checking.showBadge)
        assertFalse(MarketVersionState.Latest.showBadge)
        assertFalse(MarketVersionState.Failed.showBadge)
        assertTrue(MarketVersionState.Available(MarketUpdate(163, "1.4.3", 20, "")).showBadge)
    }
    @Test fun failureAndUnknownNeverMeanLatest() {
        assertFalse(MarketVersionState.Failed.isLatest)
        assertFalse(MarketVersionState.Unknown.isLatest)
        assertFalse(MarketVersionState.Checking.isLatest)
        assertTrue(MarketVersionState.Latest.isLatest)
    }
}
