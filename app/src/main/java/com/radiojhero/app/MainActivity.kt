package com.radiojhero.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.ui.RadioJHeroApp
import com.radiojhero.app.ui.theme.RadioJHeroTheme


class MainActivity : AppCompatActivity() {

    companion object {
        /** Intent extra with a site URL to open in the web page screen (e.g. from a notification). */
        const val EXTRA_URL = "com.radiojhero.app.EXTRA_URL"
    }

    private lateinit var player: PlayerConnection
    private var pendingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val screenLayoutSize =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        if (screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_SMALL || screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        player = PlayerConnection(this).apply {
            connect()
        }

        if (savedInstanceState == null) {
            pendingUrl = intent.getStringExtra(EXTRA_URL)
        }

        setContent {
            RadioJHeroTheme {
                RadioJHeroApp(
                    player = player,
                    pendingUrl = pendingUrl,
                    onPendingUrlHandled = { pendingUrl = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let {
            pendingUrl = it
        }
    }

    override fun onStart() {
        super.onStart()
        ConfigFetcher.start(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onStop() {
        super.onStop()
        ConfigFetcher.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.disconnect()
    }
}
