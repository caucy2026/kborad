package org.fcitx.fcitx5.android.ui.main

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.findNavController
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialPrivacyPolicyFragmentTest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun aboutPrivacyPolicyOpensLocalPolicyPage() {
        assertPrivacyEntryNavigates(SettingsRoute.About)
    }

    @Test
    fun commercialPrivacyPolicyOpensLocalPolicyPage() {
        assertPrivacyEntryNavigates(SettingsRoute.CommercialPrivacy)
    }

    private fun assertPrivacyEntryNavigates(entryRoute: SettingsRoute) {
        val activity = activityRule.activity
        activityRule.runOnUiThread {
            activity.findNavController(R.id.nav_host_fragment).navigateWithAnim(entryRoute)
        }
        activityRule.runOnUiThread {
            val title = activity.findViewById<ViewGroup>(android.R.id.content)
                .findText(activity.getString(R.string.privacy_policy))
            assertNotNull(title)
            title!!.clickPreferenceRow()
        }
        activityRule.runOnUiThread {
            val controller = activity.findNavController(R.id.nav_host_fragment)
            assertTrue(controller.currentDestination!!.hasRoute<SettingsRoute.CommercialPrivacyPolicy>())
            val body = activity.findViewById<ViewGroup>(android.R.id.content)
                .findText(activity.getString(R.string.commercial_privacy_policy_body))
            assertNotNull(body)
        }
    }

    private fun View.findText(expected: String): TextView? {
        if (this is TextView && text.toString() == expected) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findText(expected)?.let { return it }
        }
        return null
    }

    private fun View.clickPreferenceRow() {
        var target: View? = this
        while (target != null && !target.isClickable) {
            target = target.parent as? View
        }
        checkNotNull(target).performClick()
    }
}
