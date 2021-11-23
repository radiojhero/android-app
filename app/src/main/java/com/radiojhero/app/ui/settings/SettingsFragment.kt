package com.radiojhero.app.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.onesignal.OneSignal
import com.radiojhero.app.R
import com.radiojhero.app.switchTheme

class SettingsFragment : PreferenceFragmentCompat() {

    private val listener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                "appearance" -> switchTheme(sharedPreferences.getString(key, "system") ?: "system")
                "notification" -> setNotificationMode(
                    sharedPreferences.getString(key, "none") ?: "none"
                )
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun setNotificationMode(mode: String) {
        val newValue = when (mode) {
            "shows" -> 1
            "shows_articles" -> 2
            else -> 0
        }
        println("Setting notification mode to $newValue...")

        if (newValue != 0) {
            OneSignal.provideUserConsent(true)
            OneSignal.promptForPushNotifications()
        }

        OneSignal.sendTag("notification_mode", "$newValue")
    }
}