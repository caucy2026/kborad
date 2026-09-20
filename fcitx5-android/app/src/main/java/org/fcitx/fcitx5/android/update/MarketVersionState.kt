package org.fcitx.fcitx5.android.update

sealed interface MarketVersionState {
    data object Unknown : MarketVersionState
    data object Checking : MarketVersionState
    data object Latest : MarketVersionState
    data object Failed : MarketVersionState
    data class Available(val update: MarketUpdate) : MarketVersionState
    val showBadge: Boolean get() = this is Available
    val isLatest: Boolean get() = this == Latest
}
