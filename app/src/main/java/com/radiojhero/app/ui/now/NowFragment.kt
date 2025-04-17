package com.radiojhero.app.ui.now

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.radiojhero.app.R
import com.radiojhero.app.RoundedOutlineProvider
import com.radiojhero.app.collapse
import com.radiojhero.app.databinding.FragmentNowBinding
import com.radiojhero.app.expand
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.getNow
import com.radiojhero.app.services.MediaPlaybackService
import koleton.api.hideSkeleton
import koleton.api.loadSkeleton
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

class NowFragment : Fragment() {

    private var binding: FragmentNowBinding? = null

    private var progressTimer: Timer? = null
    private var lastUpdatedAt = 0L
    private var songProgress = 0L
    private var songDuration = 0L

    private var programImageLoaded = false
    private val programImageListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            if (programImageLoaded) {
                return false
            }
            programImageLoaded = true
            maybeFinishUpdatingMetadata()
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            if (programImageLoaded) {
                return false
            }
            programImageLoaded = true
            maybeFinishUpdatingMetadata()
            return false
        }
    }

    private var djImageLoaded = false
    private val djImageListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            if (djImageLoaded) {
                return false
            }
            djImageLoaded = true
            maybeFinishUpdatingMetadata()
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            if (djImageLoaded) {
                return false
            }
            djImageLoaded = true
            maybeFinishUpdatingMetadata()
            return false
        }
    }

    private lateinit var mediaController: MediaControllerCompat
    private var metadata: Bundle = Bundle()

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onExtrasChanged(extras: Bundle?) {
            metadata = extras ?: Bundle().apply {
                putBoolean(MediaPlaybackService.HAS_ERROR, true)
            }

            binding?.error?.visibility = View.GONE
            binding?.constraintLayout?.visibility = View.VISIBLE
            updateMetadata()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inflated = FragmentNowBinding.inflate(inflater, container, false)
        inflated.apply {
            constraintLayout.loadSkeleton {
                color(R.color.md_theme_surfaceDim)
                shimmer(true)
            }
            programImageWrapper.apply {
                clipToOutline = true
                outlineProvider = RoundedOutlineProvider(10f)
            }
            djImage.apply {
                clipToOutline = true
                outlineProvider = RoundedOutlineProvider(25f)
            }
        }

        binding = inflated
        setupMediaController()
        return inflated.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                createMenu(menu, menuInflater)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return selectMenu(item)
            }
        }, viewLifecycleOwner, Lifecycle.State.STARTED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaController.unregisterCallback(controllerCallback)
        binding = null
    }

    private fun createMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.now_menu, menu)
    }

    private fun selectMenu(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_schedule) {
            val action =
                NowFragmentDirections.actionNavigationNowToNavigationWebpage(
                    ConfigFetcher.getConfig("scheduleUrl") ?: ""
                )
            activity?.findNavController(R.id.nav_host_fragment_activity_main)?.navigate(action)
            return true
        }

        if (item.itemId == R.id.action_song_history) {
            toggleSongHistory()
            return true
        }

        return false
    }

    override fun onStart() {
        super.onStart()
        updateMetadata()
    }

    override fun onStop() {
        super.onStop()
        stopUpdatingProgress()
    }

    private fun setupMediaController() {
        try {
            mediaController = MediaControllerCompat.getMediaController(requireActivity()).apply {
                this@NowFragment.metadata = extras
                registerCallback(controllerCallback)
                updateMetadata()
            }
        } catch (_: NullPointerException) {
            Timer().schedule(50) {
                println("Failed to set up mediaController; retrying...")
                requireActivity().runOnUiThread {
                    setupMediaController()
                }
            }
        }
    }

    private fun isMetadataEmpty() =
        metadata.getLong(MediaPlaybackService.LAST_UPDATED_TIME, -1L) == -1L

    private fun updateMetadata() {
        val binding = this.binding ?: return

        if (metadata.getBoolean(MediaPlaybackService.HAS_ERROR)) {
            binding.error.visibility = View.VISIBLE
            binding.constraintLayout.visibility = View.GONE
            return
        }

        if (isMetadataEmpty()) {
            return
        }

        Glide.with(this).load(metadata.getString(MediaPlaybackService.PROGRAM_IMAGE))
            .addListener(programImageListener)
            .into(binding.programImage)

        val djImageSrc = metadata.getString(MediaPlaybackService.DJ_IMAGE)
        if (djImageSrc.isNullOrBlank() || djImageSrc == "about:blank") {
            djImageLoaded = true
        } else {
            Glide.with(this).load(djImageSrc).addListener(djImageListener)
                .into(binding.djImage)
        }

        lastUpdatedAt = metadata.getLong(MediaPlaybackService.LAST_UPDATED_TIME)
        maybeFinishUpdatingMetadata()
    }

    private fun maybeFinishUpdatingMetadata() {
        val binding = this.binding ?: return

        if (!programImageLoaded || !djImageLoaded || isMetadataEmpty()) {
            return
        }

        binding.constraintLayout.hideSkeleton()

        val djImageSrc = metadata.getString(MediaPlaybackService.DJ_IMAGE)
        binding.djImage.visibility =
            if (djImageSrc.isNullOrBlank() || djImageSrc == "about:blank") View.GONE else View.VISIBLE

        binding.programLabel.text = metadata.getString(MediaPlaybackService.PROGRAM_NAME)
        binding.descriptionLabel.text =
            metadata.getString(MediaPlaybackService.PROGRAM_DESCRIPTION)

        binding.djLabel.text = getString(
            R.string.dj_and_genre,
            metadata.getString(MediaPlaybackService.DJ_NAME) ?: getString(R.string.playlist),
            metadata.getString(MediaPlaybackService.PROGRAM_GENRE)
        )

        binding.songHistory0.visibility = View.GONE
        binding.songHistory1.visibility = View.GONE
        binding.songHistory2.visibility = View.GONE
        binding.songHistory3.visibility = View.GONE

        val songHistory = metadata.getStringArray(MediaPlaybackService.SONG_HISTORY) ?: emptyArray()
        for ((index, line) in songHistory.withIndex()) {
            if (index + 1 >= binding.songHistoryWrapper.size) {
                break
            }

            (binding.songHistoryWrapper[index + 1] as TextView).apply {
                visibility = View.VISIBLE
                text = line
            }
        }

        binding.songLabel.text = metadata.getString(MediaPlaybackService.SONG_TITLE)
        binding.artistLabel.text = metadata.getString(MediaPlaybackService.SONG_ARTIST)
        binding.albumLabel.text = metadata.getString(MediaPlaybackService.SONG_ALBUM)

        songDuration = metadata.getLong(MediaPlaybackService.SONG_DURATION)
        songProgress =
            metadata.getLong(MediaPlaybackService.CURRENT_TIME) - metadata.getLong(
                MediaPlaybackService.SONG_START_TIME
            )

        startUpdatingProgress()
    }

    private fun startUpdatingProgress() {
        stopUpdatingProgress()

        println("Starting song progress timer.")
        progressTimer = Timer()
        progressTimer?.scheduleAtFixedRate(0, 250) {
            activity?.runOnUiThread {
                updateProgress()
            }
        }
    }

    private fun stopUpdatingProgress() {
        if (progressTimer != null) {
            println("Stopping song progress timer.")
            progressTimer?.cancel()
        }
        progressTimer = null
    }

    private fun updateProgress() {
        val binding = this.binding

        if (binding == null) {
            stopUpdatingProgress()
            return
        }

        val duration = songDuration
        val progress = songProgress + getNow() - lastUpdatedAt
        binding.timeLabel.text = formatTime(progress)
        binding.durationLabel.text =
            if (duration < 0) getString(R.string.live) else formatTime(duration)
        binding.nowProgress.max = duration.toInt()
        binding.nowProgress.progress = progress.toInt()
    }

    private fun formatTime(time: Long): String {
        return DateUtils.formatElapsedTime(time / 1000)
    }

    private var showSongHistory = false

    private fun toggleSongHistory() {
        showSongHistory = !showSongHistory

        if (showSongHistory) {
            binding?.songHistoryWrapper?.expand(250)
        } else {
            binding?.songHistoryWrapper?.collapse(250)
        }
    }
}