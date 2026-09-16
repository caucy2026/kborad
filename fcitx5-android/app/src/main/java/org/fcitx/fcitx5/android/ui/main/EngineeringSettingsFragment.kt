package org.fcitx.fcitx5.android.ui.main

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class EngineeringSettingsFragment : PaddingPreferenceFragment() {
    private fun PreferenceCategory.addDestination(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        route: SettingsRoute,
    ) {
        addPreference(title, icon = icon) { navigateWithAnim(route) }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addCategory(R.string.engineering_settings) {
                addDestination(R.string.global_options, R.drawable.ic_baseline_tune_24, SettingsRoute.GlobalConfig)
                addDestination(R.string.input_methods, R.drawable.ic_baseline_language_24, SettingsRoute.InputMethodList)
                addDestination(R.string.addons, R.drawable.ic_baseline_extension_24, SettingsRoute.AddonList)
                addDestination(R.string.theme, R.drawable.ic_baseline_palette_24, SettingsRoute.Theme)
                addDestination(R.string.virtual_keyboard, R.drawable.ic_baseline_keyboard_24, SettingsRoute.VirtualKeyboard)
                addDestination(R.string.candidates_window, R.drawable.ic_baseline_list_alt_24, SettingsRoute.CandidatesWindow)
                addDestination(R.string.clipboard, R.drawable.ic_clipboard, SettingsRoute.Clipboard)
                addDestination(R.string.emoji_and_symbols, R.drawable.ic_baseline_emoji_symbols_24, SettingsRoute.Symbol)
                addDestination(R.string.plugins, R.drawable.ic_baseline_android_24, SettingsRoute.Plugin)
                addDestination(R.string.advanced, R.drawable.ic_baseline_more_horiz_24, SettingsRoute.Advanced)
                addDestination(R.string.developer, R.drawable.ic_baseline_more_horiz_24, SettingsRoute.Developer)
            }
        }
    }
}
