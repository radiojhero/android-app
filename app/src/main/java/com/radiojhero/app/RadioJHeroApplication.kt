package com.radiojhero.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavDeepLinkBuilder
import androidx.preference.PreferenceManager
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.services.MediaPlaybackService
import io.sentry.android.core.SentryAndroid
import androidx.core.net.toUri

class RadioJHeroApplication : Application(), LifecycleEventObserver {

    private var mediaServiceStarted = false

    override fun onCreate() {
        super.onCreate()
        detectNightModeFromSettings()
        ensureRemoteSettings()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        println("debug = ${BuildConfig.DEBUG}")

        if (!BuildConfig.DEBUG) {
            SentryAndroid.init(this) {
                it.apply {
                    dsn = ConfigFetcher.getConfig("sentryUrl")
                    isEnableAutoSessionTracking = true
                    sampleRate = 1.0
                }
            }
        }

        OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.consentRequired = true
        OneSignal.initWithContext(this, ConfigFetcher.getConfig("onesignalApp") ?: "")
        OneSignal.Location.isShared = false
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val url = event.notification.additionalData?.getString("myurl") ?: return

                if (url.toUri().host != ConfigFetcher.getConfig("host")) {
                    return
                }

                val args = Bundle()
                args.putString("url", url)

                NavDeepLinkBuilder(this@RadioJHeroApplication).setGraph(R.navigation.mobile_navigation)
                    .setDestination(R.id.navigation_webpage).setArguments(args)
                    .createTaskStackBuilder().startActivities()
            }
        })
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_START) {
            startMediaService()
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

    private fun startMediaService() {
        if (mediaServiceStarted) {
            return
        }

        try {
            startService(Intent(this, MediaPlaybackService::class.java))
            mediaServiceStarted = true
        } catch (error: IllegalStateException) {
            println("MediaPlaybackService not started, app is probably in background; retrying later...")
            println(error)
        }
    }
}