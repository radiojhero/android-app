package com.radiojhero.app.ui.now

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.text.format.DateUtils
import android.view.*
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.radiojhero.app.*
import com.radiojhero.app.databinding.FragmentNowBinding
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.services.MediaPlaybackService
import koleton.api.hideSkeleton
import koleton.api.loadSkeleton
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.min
import kotlin.math.roundToInt

class NowFragment : Fragment() {

    private var _binding: FragmentNowBinding? = null
    private val binding get() = _binding!!

    private var progressTimer: Timer? = null
    private var lastUpdatedAt = 0.0
    private var songProgress = 0.0
    private var songDuration = 0.0

    private var programImageLoaded = false
    private val programImageListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>?,
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
            resource: Drawable?,
            model: Any?,
            target: Target<Drawable>?,
            dataSource: DataSource?,
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
            target: Target<Drawable>?,
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
            resource: Drawable?,
            model: Any?,
            target: Target<Drawable>?,
            dataSource: DataSource?,
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
            metadata = extras!!
            updateMetadata()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowBinding.inflate(inflater, container, false)
        binding.apply {
            constraintLayout.loadSkeleton {
                color(R.color.skeleton)
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

        setupMediaController()
        return binding.root
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
        _binding = null
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
        metadata.getDouble(MediaPlaybackService.LAST_UPDATED_TIME, -1.0) == -1.0

    private fun updateMetadata() {
        if (_binding == null || isMetadataEmpty()) {
            return
        }

        Glide.with(this).load(metadata.getString(MediaPlaybackService.PROGRAM_IMAGE))
            .addListener(programImageListener)
            .into(binding.programImage)

        val djImageSrc = metadata.getString(MediaPlaybackService.DJ_IMAGE)
        if (djImageSrc == null) {
            djImageLoaded = true
        } else {
            Glide.with(this).load(djImageSrc).addListener(djImageListener)
                .into(binding.djImage)
        }

        lastUpdatedAt = metadata.getDouble(MediaPlaybackService.LAST_UPDATED_TIME)
        maybeFinishUpdatingMetadata()
    }

    private fun maybeFinishUpdatingMetadata() {
        if (!programImageLoaded || !djImageLoaded || _binding == null || isMetadataEmpty()) {
            return
        }

        binding.constraintLayout.hideSkeleton()

        val djImageSrc = metadata.getString(MediaPlaybackService.DJ_IMAGE)
        binding.djImage.visibility = if (djImageSrc == null) View.GONE else View.VISIBLE

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

        val songHistory = metadata.getStringArray(MediaPlaybackService.SONG_HISTORY)!!
        for ((index, line) in songHistory.withIndex()) {
            (binding.songHistoryWrapper[index + 1] as TextView).apply {
                visibility = View.VISIBLE
                text = line
            }
        }

        binding.songLabel.text = metadata.getString(MediaPlaybackService.SONG_TITLE)
        binding.artistLabel.text = metadata.getString(MediaPlaybackService.SONG_ARTIST)

        songDuration = metadata.getDouble(MediaPlaybackService.SONG_DURATION)
        songProgress =
            metadata.getDouble(MediaPlaybackService.CURRENT_TIME) - metadata.getDouble(
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
        if (_binding == null) {
            stopUpdatingProgress()
            return
        }

        val duration = if (songDuration < 0) Double.POSITIVE_INFINITY else songDuration
        val progress = min(duration, songProgress + getNow() - lastUpdatedAt)
        binding.timeLabel.text = formatTime(progress)
        binding.durationLabel.text =
            if (duration.isInfinite()) getString(R.string.live) else formatTime(duration)
        binding.nowProgress.max = duration.roundToInt()
        binding.nowProgress.progress = progress.roundToInt()
    }

    private fun formatTime(time: Double): String {
        return DateUtils.formatElapsedTime(time.toLong())
    }

    private var showSongHistory = false

    private fun toggleSongHistory() {
        showSongHistory = !showSongHistory

        if (showSongHistory) {
            binding.songHistoryWrapper.expand(250)
        } else {
            binding.songHistoryWrapper.collapse(250)
        }
    }
}