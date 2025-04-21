package com.radiojhero.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.radiojhero.app.databinding.ActivityMainBinding
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.services.MediaPlaybackService
import com.radiojhero.app.ui.settings.SettingsFragmentDirections


class MainActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaBrowser: MediaBrowserCompat

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val mediaController =
                MediaControllerCompat(this@MainActivity, mediaBrowser.sessionToken)
            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
            buildTransportControls()
        }
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            switchFabIcon(!isStopped(state?.state))
        }
    }

    private fun buildTransportControls() {
        MediaControllerCompat.getMediaController(this).apply {
            binding.fab.setOnClickListener {
                if (!isStopped(playbackState?.state)) {
                    transportControls.pause()
                } else {
                    transportControls.play()
                }
            }

            transportControls.prepare()
            switchFabIcon(!isStopped(playbackState?.state))
            registerCallback(controllerCallback)
        }
    }

    private fun isStopped(state: Int?): Boolean {
        return state != PlaybackStateCompat.STATE_PLAYING && state != PlaybackStateCompat.STATE_BUFFERING
    }

    fun switchFormat(format: String) {
        MediaControllerCompat.getMediaController(this).transportControls.playFromMediaId(
            format, null
        )
    }

    private var hasScrolled = false

    fun toggleAppBarBackground(hasScrolled: Boolean) {
        if (hasScrolled == this.hasScrolled) {
            return
        }

        this.hasScrolled = hasScrolled
        val colorFrom = resources.getColor(if (hasScrolled) R.color.md_theme_surface else R.color.md_theme_surfaceContainer, null)
        val colorTo = resources.getColor(if (hasScrolled) R.color.md_theme_surfaceContainer else R.color.md_theme_surface, null)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.setDuration(200) // milliseconds
        colorAnimation.addUpdateListener { animator -> binding.top.setBackgroundColor(animator.animatedValue as Int) }
        colorAnimation.start()
    }

    private var originalPaddingBottom: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val screenLayoutSize =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        if (screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_SMALL || screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view: View, insets: WindowInsetsCompat ->
            if (originalPaddingBottom == null) {
                originalPaddingBottom = view.paddingBottom
            }

            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                originalPaddingBottom!! - if (imeInsets.bottom > 0) 0 else systemInsets.bottom
            )

            val params = binding.navView.layoutParams
            params.height = if (imeInsets.bottom > 0) 0 else Toolbar.LayoutParams.MATCH_PARENT
            binding.navView.layoutParams = params
            binding.bottomAppBar.visibility = if (imeInsets.bottom > 0) View.INVISIBLE else View.VISIBLE

            insets
        }

        mediaBrowser = MediaBrowserCompat(
            this, ComponentName(this, MediaPlaybackService::class.java), connectionCallbacks, null
        ).apply {
            connect()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView = binding.navView
        navView.menu[2].isEnabled = false

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_now,
                R.id.navigation_interact,
                R.id.navigation_articles,
                R.id.navigation_settings
            )
        )
        setSupportActionBar(binding.topAppBar)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment_activity_main).navigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat, pref: Preference
    ): Boolean {
        if (pref.fragment != "com.radiojhero.app.ui.webpage.WebPageFragment") {
            return false
        }

        val action = SettingsFragmentDirections.actionNavigationSettingsToNavigationWebpage(
            ConfigFetcher.getConfig("aboutUrl") ?: ""
        )
        findNavController(R.id.nav_host_fragment_activity_main).navigate(action)

        return true
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
        mediaBrowser.disconnect()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
    }

    private fun switchFabIcon(toggle: Boolean) {
        binding.fab.apply {
            setImageResource(if (toggle) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24)
            contentDescription = getString(if (toggle) R.string.pause else R.string.play)
        }
    }
}