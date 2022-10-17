package com.radiojhero.app

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavDeepLinkBuilder
import androidx.preference.PreferenceManager
import com.onesignal.OneSignal
import com.radiojhero.app.fetchers.ConfigFetcher
import io.sentry.android.core.SentryAndroid

class RadioJHeroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        detectNightModeFromSettings()
        ensureRemoteSettings()

        println("debug = ${BuildConfig.DEBUG}")

        if (!BuildConfig.DEBUG) {
            SentryAndroid.init(this) {
                it.apply {
                    dsn = ConfigFetcher.getConfig("sentryUrl")
                    isDebug = true
                    isEnableAutoSessionTracking = true
                    sampleRate = 1.0
                }
            }
        }

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.setRequiresUserPrivacyConsent(true)
        OneSignal.setLocationShared(false)
        OneSignal.setAppId(ConfigFetcher.getConfig("onesignalApp") ?: "")
        OneSignal.initWithContext(this)
        OneSignal.setNotificationOpenedHandler {
            val url = it.notification.additionalData?.getString("myurl")
                ?: return@setNotificationOpenedHandler

            if (Uri.parse(url).host != ConfigFetcher.getConfig("host")) {
                return@setNotificationOpenedHandler
            }

            val args = Bundle()
            args.putString("url", url)

            NavDeepLinkBuilder(this)
                .setGraph(R.navigation.mobile_navigation)
                .setDestination(R.id.navigation_webpage)
                .setArguments(args)
                .createTaskStackBuilder()
                .startActivities()
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