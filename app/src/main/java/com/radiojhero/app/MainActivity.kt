package com.radiojhero.app

import android.content.ComponentName
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.radiojhero.app.databinding.ActivityMainBinding
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.MetadataFetcher
import com.radiojhero.app.services.MediaPlaybackService
import com.radiojhero.app.ui.settings.SettingsFragmentDirections

class MainActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaBrowser: MediaBrowserCompat

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser.sessionToken.also { token ->
                val mediaController = MediaControllerCompat(
                    this@MainActivity, // Context
                    token
                )

                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
            }

            buildTransportControls()
        }

        override fun onConnectionSuspended() {
        }

        override fun onConnectionFailed() {
        }
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            switchFabIcon(state?.state == PlaybackStateCompat.STATE_PLAYING)
        }
    }

    private fun buildTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this)

        binding.fab.setOnClickListener {
            if (mediaController.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController.transportControls.stop()
            } else {
                mediaController.transportControls.play()
            }
        }

        mediaController.registerCallback(controllerCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaPlaybackService::class.java),
            connectionCallbacks,
            null
        ).apply {
            connect()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView = binding.navView
        navView.menu.getItem(2).isEnabled = false

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_now,
                R.id.navigation_interact,
                R.id.navigation_articles,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayShowHomeEnabled(true)
            setDisplayUseLogoEnabled(true)
            setLogo(R.mipmap.ic_logojhero)
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val original = resources.displayMetrics.heightPixels
            val heightDiff = original - binding.root.height
            val visibility = if (heightDiff > 500) View.GONE else View.VISIBLE
            binding.bottomAppBar.visibility = visibility
            binding.fab.visibility = visibility
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment_activity_main).navigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        if (pref.fragment != "com.radiojhero.app.ui.webpage.WebPageFragment") {
            return false
        }

        val action =
            SettingsFragmentDirections.actionNavigationSettingsToNavigationWebpage(
                ConfigFetcher.getConfig("aboutUrl") ?: ""
            )
        findNavController(R.id.nav_host_fragment_activity_main).navigate(action)

        return true
    }

    override fun onStart() {
        super.onStart()
        ConfigFetcher.start(applicationContext)
        MetadataFetcher.start(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onStop() {
        super.onStop()
        ConfigFetcher.stop()
        MetadataFetcher.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaBrowser.disconnect()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
    }

    private fun switchFabIcon(toggle: Boolean) {
        binding.fab.apply {
            setImageResource(if (toggle) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24)
            contentDescription = if (toggle) "Pause" else "Play"
        }
    }
}