package org.fcitx.fcitx5.android.ui.main

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.navigation.findNavController
import androidx.test.runner.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialThemeFragmentTest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun themePageDoesNotRequestInternalSwitchLabels() {
        val activity = activityRule.activity
        activity.runOnUiThread {
            activity.findNavController(R.id.nav_host_fragment)
                .navigateWithAnim(SettingsRoute.CommercialTheme)
        }
        activityRule.runOnUiThread {
            val switch = activity.findViewById<ViewGroup>(android.R.id.content)
                .findSwitch()
            assertNotNull(switch)
            assertFalse(switch!!.showText)
            assertTrue(switch.isClickable)
        }
    }

    private fun View.findSwitch(): SwitchCompat? {
        if (this is SwitchCompat) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findSwitch()?.let { return it }
        }
        return null
    }
}
