package com.radiojhero.app

import android.app.Application
import androidx.preference.PreferenceManager
import com.onesignal.OneSignal
import com.radiojhero.app.fetchers.ConfigFetcher
import io.sentry.android.core.SentryAndroid

class RadioJHeroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        detectNightModeFromSettings()
        ensureRemoteSettings()

        SentryAndroid.init(this) {
            it.apply {
                dsn = ConfigFetcher.getConfig("sentryUrl")
                isDebug = true
                isEnableAutoSessionTracking = true
                sampleRate = 1.0
            }
        }

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.setRequiresUserPrivacyConsent(true)
        OneSignal.setLocationShared(false)
        OneSignal.setAppId(ConfigFetcher.getConfig("onesignalApp") ?: "")
        OneSignal.initWithContext(this)
        OneSignal.setNotificationOpenedHandler {
            val url = it.notification.additionalData?.getString("myurl")
            println(url)
        }
    }

    private fun ensureRemoteSettings() {
        ConfigFetcher.start(this)

        while (true) {
            if (ConfigFetcher.hasLoaded) {
                break
            }
        }
    }

    private fun detectNightModeFromSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        switchTheme(sharedPreferences.getString("appearance", "system") ?: "system")
    }
}