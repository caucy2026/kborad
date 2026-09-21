/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.fcitx.fcitx5.android.utils.toast

class AboutFragment : PaddingPreferenceFragment() {

    private lateinit var versionPreference: VersionPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(R.string.privacy_policy) {
                navigateWithAnim(SettingsRoute.CommercialPrivacyPolicy)
            }
            addPreference(
                R.string.open_source_licenses,
                R.string.licenses_of_third_party_libraries
            ) {
                navigateWithAnim(SettingsRoute.License)
            }
            addCategory(R.string.version) {
                isIconSpaceReserved = false
                versionPreference = VersionPreference(requireContext()) {
                    showEngineeringPasswordDialog()
                }.apply {
                    key = "kboard_version"
                    isIconSpaceReserved = false
                    showVersion(Const.versionName, false)
                    setOnPreferenceClickListener {
                        (requireActivity() as MainActivity).marketUpdates.onVersionClicked(viewLifecycleOwner)
                        true
                    }
                }
                addPreference(versionPreference)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                (requireActivity() as MainActivity).marketUpdates.state.collect {
                    versionPreference.showVersion(Const.versionName, it.showBadge)
                }
            }
        }
    }

    override fun onPause() {
        versionPreference.cancelHold()
        super.onPause()
    }

    private fun showEngineeringPasswordDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.engineering_password)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.engineering_settings)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (EngineeringAccessSession.gate.unlock(input.text.toString())) {
                    navigateWithAnim(SettingsRoute.Engineering)
                } else {
                    requireContext().toast(R.string.engineering_password_incorrect)
                }
            }
            .show()
    }
}
