package com.radiojhero.app.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.onesignal.OneSignal
import com.radiojhero.app.BuildConfig
import com.radiojhero.app.R
import com.radiojhero.app.switchTheme
import kotlinx.coroutines.*

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

    private fun setNotificationMode(mode: String) = lifecycleScope.launch {
        val newValue = when (mode) {
            "shows" -> 1
            "shows_articles" -> 2
            else -> 0
        }
        println("Setting notification mode to $newValue...")

        if (newValue != 0) {
            OneSignal.consentGiven = OneSignal.Notifications.requestPermission(true)
        }

        if (!OneSignal.Notifications.permission) {
            return@launch
        }

        OneSignal.User.addTag("notification_mode", "$newValue")

        if (BuildConfig.DEBUG) {
            OneSignal.User.addTag("debug_mode", "1")
        }
    }
}