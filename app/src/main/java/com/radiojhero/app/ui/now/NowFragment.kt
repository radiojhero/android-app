package com.radiojhero.app.ui.now

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.text.format.DateUtils
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.radiojhero.app.R
import com.radiojhero.app.RoundedOutlineProvider
import com.radiojhero.app.databinding.FragmentNowBinding
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.getNow
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
    private var metadata: Bundle? = null

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onExtrasChanged(extras: Bundle?) {
            metadata = extras
            updateMetadata()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        binding.apply {
            constraintLayout.loadSkeleton {
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

    override fun onDestroyView() {
        super.onDestroyView()
        mediaController.unregisterCallback(controllerCallback)
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.now_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_schedule) {
            return super.onOptionsItemSelected(item)
        }

        val action =
            NowFragmentDirections.actionNavigationNowToNavigationWebpage(
                ConfigFetcher.getConfig("scheduleUrl") ?: ""
            )
        activity?.findNavController(R.id.nav_host_fragment_activity_main)?.navigate(action)
        return true
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

    private fun updateMetadata() {
        if (_binding == null || metadata == null) {
            return
        }

        Glide.with(this).load(metadata!!.getString(MediaPlaybackService.PROGRAM_IMAGE))
            .addListener(programImageListener)
            .into(binding.programImage)

        val djImageSrc = metadata!!.getString(MediaPlaybackService.DJ_IMAGE)
        if (djImageSrc == null) {
            binding.djImage.visibility = View.GONE
            djImageLoaded = true
        } else {
            binding.djImage.visibility = View.VISIBLE
            Glide.with(this).load(djImageSrc).addListener(djImageListener)
                .into(binding.djImage)
        }

        lastUpdatedAt = metadata!!.getDouble(MediaPlaybackService.LAST_UPDATED_TIME)
        maybeFinishUpdatingMetadata()
    }

    private fun maybeFinishUpdatingMetadata() {
        if (!programImageLoaded || !djImageLoaded || _binding == null || metadata == null) {
            return
        }

        binding.programLabel.text = metadata!!.getString(MediaPlaybackService.PROGRAM_NAME)
        binding.descriptionLabel.text =
            metadata!!.getString(MediaPlaybackService.PROGRAM_DESCRIPTION)

        binding.djLabel.text = getString(
            R.string.dj_and_genre,
            metadata!!.getString(MediaPlaybackService.DJ_NAME) ?: getString(R.string.playlist),
            metadata!!.getString(MediaPlaybackService.PROGRAM_GENRE)
        )

        binding.songLabel.text = metadata!!.getString(MediaPlaybackService.SONG_TITLE)
        binding.artistLabel.text = metadata!!.getString(MediaPlaybackService.SONG_ARTIST)

        songDuration = metadata!!.getDouble(MediaPlaybackService.SONG_DURATION)
        songProgress =
            metadata!!.getDouble(MediaPlaybackService.CURRENT_TIME) - metadata!!.getDouble(
                MediaPlaybackService.SONG_START_TIME
            )

        binding.constraintLayout.hideSkeleton()
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
}