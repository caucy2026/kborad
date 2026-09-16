package org.fcitx.fcitx5.android.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceScreen
import com.google.android.material.materialswitch.MaterialSwitch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.theme.ResponsiveThemeListView
import org.fcitx.fcitx5.android.ui.main.settings.theme.SimpleThemeListAdapter
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.addPreference
import splitties.dimensions.dp

private fun PreferenceScreen.addManagedPreference(
    provider: ManagedPreferenceProvider,
    key: String,
) {
    val item = provider.managedPreferencesUi.first { it.key == key }
    addPreference(item.createUi(context).apply { isEnabled = item.isEnabled() })
}

class CommercialKeyboardSettingsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val prefs = AppPrefs.getInstance()
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addManagedPreference(prefs.keyboard, prefs.keyboard.soundOnKeyPress.key)
            addManagedPreference(prefs.keyboard, prefs.keyboard.hapticOnKeyPress.key)
            addManagedPreference(prefs.keyboard, prefs.keyboard.popupOnKeyPress.key)
            addManagedPreference(prefs.keyboard, prefs.keyboard.spaceSwipeMoveCursor.key)
            addManagedPreference(prefs.clipboard, prefs.clipboard.clipboardSuggestion.key)
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AppPrefs.getInstance().syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }
}

class CommercialPrivacySettingsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val prefs = AppPrefs.getInstance().clipboard
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addManagedPreference(prefs, prefs.clipboardListening.key)
            addManagedPreference(prefs, prefs.clipboardMaskSensitive.key)
            addPreference(R.string.privacy_policy) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.privacyPolicyUrl)))
            }
        }
    }
}

class CommercialThemeFragment : Fragment() {
    private var followSystem by ThemeManager.prefs.followSystemDayNightTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        val themes = ThemeManager.BuiltinThemes
        val adapter = object : SimpleThemeListAdapter<Theme.Builtin>(themes) {
            override fun onClick(theme: Theme.Builtin) {
                followSystem = false
                ThemeManager.setNormalModeTheme(theme)
            }
        }.apply {
            selected = themes.indexOfFirst { it.name == ThemeManager.activeTheme.name }
        }
        val list = ResponsiveThemeListView(ctx).apply {
            this.adapter = adapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        val followSwitch = MaterialSwitch(ctx).apply {
            text = getString(R.string.follow_system_day_night_theme)
            isChecked = followSystem
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnCheckedChangeListener { _, checked ->
                followSystem = checked
            }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(followSwitch)
            addView(list)
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ThemeManager.syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }
}
