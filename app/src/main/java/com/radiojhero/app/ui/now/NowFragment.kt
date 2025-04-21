package com.radiojhero.app.ui.now

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.radiojhero.app.MainActivity
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
import kotlin.math.min

class NowFragment : Fragment() {

    private var binding: FragmentNowBinding? = null

    private var progressTimer: Timer? = null
    private var lastUpdatedAt = 0L
    private var songProgress = 0L
    private var songDuration = 0L

    private var programImageLoaded = false
    private val programImageListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
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
            e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
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

    private var songImageLoaded = false
    private val songImageListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
        ): Boolean {
            if (songImageLoaded) {
                return false
            }
            songImageLoaded = true
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
            if (songImageLoaded) {
                return false
            }
            songImageLoaded = true
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val inflated = FragmentNowBinding.inflate(inflater, container, false)
        inflated.apply {
            constraintLayout.loadSkeleton {
                color(R.color.md_theme_surfaceVariant)
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
            songImageWrapper.apply {
                clipToOutline = true
                outlineProvider = RoundedOutlineProvider(5f)
            }
            lyricsButton.addOnCheckedChangeListener { _, _ ->
                toggleLyrics()
            }
            historyButton.addOnCheckedChangeListener { _, _ ->
                toggleSongHistory()
            }
            scheduleButton.setOnClickListener { _ ->
                showSchedule()
            }
            nowRoot.setOnScrollChangeListener { _, _, y, _, _ ->
                (requireActivity() as MainActivity).toggleAppBarBackground(y > 0)
            }
            (requireActivity() as MainActivity).toggleAppBarBackground(nowRoot.scrollY > 0)
        }

        binding = inflated
        setupMediaController()
        return inflated.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaController.unregisterCallback(controllerCallback)
        binding = null
    }

    private fun showSchedule() {
        val action = NowFragmentDirections.actionNavigationNowToNavigationWebpage(
            ConfigFetcher.getConfig("scheduleUrl") ?: ""
        )
        activity?.findNavController(R.id.nav_host_fragment_activity_main)?.navigate(action)
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
            .addListener(programImageListener).into(binding.programImage)

        val djImageSrc = metadata.getString(MediaPlaybackService.DJ_IMAGE)
        if (djImageSrc.isNullOrBlank() || djImageSrc == "about:blank") {
            djImageLoaded = true
        } else {
            Glide.with(this).load(djImageSrc).addListener(djImageListener).into(binding.djImage)
        }

        Glide.with(this).load(metadata.getString(MediaPlaybackService.SONG_IMAGE))
            .addListener(songImageListener).into(binding.songImageView)

        lastUpdatedAt = metadata.getLong(MediaPlaybackService.LAST_UPDATED_TIME)
        maybeFinishUpdatingMetadata()
    }

    private fun maybeFinishUpdatingMetadata() {
        val binding = this.binding ?: return

        if (!programImageLoaded || !djImageLoaded || !songImageLoaded || isMetadataEmpty()) {
            return
        }

        binding.constraintLayout.hideSkeleton()

        val djImageSrc = metadata.getString(MediaPlaybackService.DJ_IMAGE)
        binding.djImage.visibility =
            if (djImageSrc.isNullOrBlank() || djImageSrc == "about:blank") View.GONE else View.VISIBLE

        binding.programLabel.apply {
            val content = metadata.getString(MediaPlaybackService.PROGRAM_NAME)
            if (content != text) {
                text = content
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
        }
        binding.djLabel.apply {
            var djName = metadata.getString(MediaPlaybackService.DJ_NAME)

            djName = if (djName.isNullOrBlank()) {
                getString(R.string.playlist)
            } else getString(R.string.dj_string, djName)

            val content = getString(
                R.string.dj_and_genre,
                djName,
                metadata.getString(MediaPlaybackService.PROGRAM_GENRE)
            )
            if (content != text) {
                text = content
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
        }

        binding.descriptionLabel.text = metadata.getString(MediaPlaybackService.PROGRAM_DESCRIPTION)

        binding.songHistory0.visibility = View.GONE
        binding.songHistory1.visibility = View.GONE
        binding.songHistory2.visibility = View.GONE
        binding.songHistory3.visibility = View.GONE

        val songHistory = metadata.getStringArray(MediaPlaybackService.SONG_HISTORY) ?: emptyArray()
        for ((index, line) in songHistory.slice(songHistory.size - 4..<songHistory.size)
            .withIndex()) {
            if (index + 1 >= binding.songHistoryWrapper.size) {
                break
            }

            (binding.songHistoryWrapper[index + 1] as TextView).apply {
                visibility = View.VISIBLE
                text = line
            }
        }

        binding.songLabel.apply {
            val content = metadata.getString(MediaPlaybackService.SONG_TITLE)
            if (content != text) {
                text = content
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
        }
        binding.artistLabel.apply {
            val content = metadata.getString(MediaPlaybackService.SONG_ARTIST)
            if (content != text) {
                text = content
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
        }
        binding.albumLabel.apply {
            val content = metadata.getString(MediaPlaybackService.SONG_ALBUM)
            if (content != text) {
                text = content
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
        }
        binding.requesterLabel.apply {
            var content = metadata.getString(MediaPlaybackService.SONG_REQUESTER, "")

            if (content.isNotBlank()) {
                content = getString(R.string.requester_string, content)
            }

            if (content != text) {
                text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
        }

        songDuration = metadata.getLong(MediaPlaybackService.SONG_DURATION)
        songProgress = metadata.getLong(MediaPlaybackService.CURRENT_TIME) - metadata.getLong(
            MediaPlaybackService.SONG_START_TIME
        )

        val lyrics = metadata.getString(MediaPlaybackService.SONG_LYRICS)
        binding.lyricsText.text = lyrics

        if (lyrics.isNullOrBlank()) {
            binding.lyricsPane.visibility = View.GONE
            binding.missingLyricsText.visibility = View.VISIBLE
        } else {
            binding.lyricsPane.visibility = View.VISIBLE
            binding.missingLyricsText.visibility = View.GONE
        }

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
        var progress = songProgress + getNow() - lastUpdatedAt

        if (duration >= 0) {
            progress = min(duration, progress)
        }

        binding.timeLabel.text = formatTime(progress)
        binding.durationLabel.text =
            if (duration < 0) getString(R.string.live) else formatTime(duration)
        binding.nowProgress.max = duration.toInt()
        binding.nowProgress.progress = progress.toInt()
    }

    private fun formatTime(time: Long): String {
        return DateUtils.formatElapsedTime(time / 1000)
    }

    private fun toggleLyrics() {
        val binding = binding ?: return
        if (binding.lyricsButton.isChecked) {
            binding.lyricsWrapper.expand(200, true)
            binding.historyButton.isChecked = false
        } else {
            binding.lyricsWrapper.collapse(200)
        }
    }

    private fun toggleSongHistory() {
        val binding = binding ?: return
        if (binding.historyButton.isChecked) {
            binding.songHistoryWrapper.expand(200)
            binding.lyricsButton.isChecked = false
        } else {
            binding.songHistoryWrapper.collapse(200)
        }
    }
}