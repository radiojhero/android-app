package com.radiojhero.app.ui.now

import android.graphics.drawable.Drawable
import android.os.Bundle
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
import com.radiojhero.app.fetchers.MetadataFetcher
import com.radiojhero.app.getNow
import koleton.api.hideSkeleton
import koleton.api.loadSkeleton
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
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
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
        EventBus.getDefault().register(this)
        updateMetadata()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        stopUpdatingProgress()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMetadata(event: MetadataFetcher.MetadataEvent) {
        updateMetadata()
    }

    private fun updateMetadata() {
        if (_binding == null) {
            return
        }

        val metadata = MetadataFetcher.currentData ?: return
        val program = metadata.getJSONObject("program")
        val programCovers = program.getJSONArray("cover")
        val programImageSrc =
            programCovers.getJSONObject(programCovers.length() - 1).getString("src")
        Glide.with(this).load(programImageSrc).addListener(programImageListener)
            .into(binding.programImage)

        val programDjs = program.getJSONArray("djs")
        if (programDjs.length() > 0) {
            binding.djImage.visibility = View.VISIBLE
            val djImageSrc = programDjs.getJSONObject(0).getString("avatar")
            Glide.with(this).load(djImageSrc).addListener(djImageListener)
                .into(binding.djImage)
        } else {
            binding.djImage.visibility = View.GONE
            djImageLoaded = true
        }

        lastUpdatedAt = MetadataFetcher.lastUpdatedAt
        maybeFinishUpdatingMetadata()
    }

    private fun maybeFinishUpdatingMetadata() {
        if (!programImageLoaded || !djImageLoaded || _binding == null) {
            return
        }

        val metadata = MetadataFetcher.currentData ?: return
        val program = metadata.getJSONObject("program")
        val programDjs = program.getJSONArray("djs")

        binding.programLabel.text = program.getString("name")
        binding.descriptionLabel.text = program.getString("description")

        var djText = getString(R.string.playlist)
        if (programDjs.length() > 0) {
            djText = getString(R.string.dj_string, programDjs.getJSONObject(0).getString("name"))
        }
        binding.djLabel.text = getString(R.string.dj_and_genre, djText, program.getString("genre"))

        val song = metadata.getJSONArray("song_history").getJSONObject(0)
        binding.songLabel.text = song.getString("title")
        binding.artistLabel.text = song.getString("artist")

        songDuration = song.getDouble("duration")
        songProgress = metadata.getDouble("current_time") - song.getDouble("start_time")

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