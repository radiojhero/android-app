package com.radiojhero.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.radiojhero.app.R
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.MetadataFetcher
import com.radiojhero.app.toDate
import java.text.SimpleDateFormat
import java.util.*

class MediaPlaybackService : MediaBrowserServiceCompat() {

    companion object {
        const val PROGRAM_IMAGE = "com.radiojhero.app.PROGRAM_IMAGE"
        const val DJ_IMAGE = "com.radiojhero.app.DJ_IMAGE"
        const val PROGRAM_NAME = "com.radiojhero.app.PROGRAM_NAME"
        const val DJ_NAME = "com.radiojhero.app.DJ_NAME"
        const val PROGRAM_GENRE = "com.radiojhero.app.PROGRAM_GENRE"
        const val CURRENT_TIME = "com.radiojhero.app.CURRENT_TIME"
        const val SONG_START_TIME = "com.radiojhero.app.SONG_START_TIME"
        const val SONG_DURATION = "com.radiojhero.app.SONG_DURATION"
        const val SONG_TITLE = "com.radiojhero.app.SONG_TITLE"
        const val SONG_ARTIST = "com.radiojhero.app.SONG_ARTIST"
        const val SONG_ALBUM = "com.radiojhero.app.SONG_ALBUM"
        const val SONG_IMAGE = "com.radiojhero.app.SONG_IMAGE"
        const val SONG_HISTORY = "com.radiojhero.app.SONG_HISTORY"
        const val PROGRAM_DESCRIPTION = "com.radiojhero.app.PROGRAM_DESCRIPTION"
        const val LAST_UPDATED_TIME = "com.radiojhero.app.LAST_UPDATED_TIME"
        const val PROGRAM_TYPE = "com.radiojhero.app.PROGRAM_TYPE"
        const val HAS_ERROR = "com.radiojhero.app.HAS_ERROR"

        const val PROGRAM_TYPE_LIVE = 1
        const val PROGRAM_TYPE_PODCAST = 2
        const val PROGRAM_TYPE_PLAYLIST = 3
    }

    private val ROOT = "@root@"

    private val fetcher = MetadataFetcher().apply {
        prepare(this@MediaPlaybackService)
    }

    private lateinit var audioManager: AudioManager
    private lateinit var audioFocus: AudioFocusRequestCompat
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    private var isDirty = false
    private var playbackState = PlaybackStateCompat.STATE_STOPPED
        set(value) {
            field = value
            if (value != PlaybackStateCompat.STATE_STOPPED) {
                isDirty = true
            }
        }
    private val metadataBuilder = MediaMetadataCompat.Builder()
    private val bundle = Bundle()

    private var songImageSrc = ""

    private lateinit var player: ExoPlayer
    private lateinit var selectedMediaId: String

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (player.isPlaying) {
                println("Player already playing, bail.")
                return
            }

            println("Starting player...")

            audioFocus = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener {
                    onStop()
                }
                .build()

            player.apply {
                val source = reinitPlayer()
                prepare()

                println("Requesting audio focus...")
                val result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocus)

                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    println("Failed to get audio focus.")
                    return@apply
                }

                println("Playing at $source")
                play()
                updateMetadata()
            }

            updateMetadata()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            selectedMediaId = mediaId ?: "mp3"
            if (player.isPlaying) {
                onPause()
                onPlay()
            }
        }

        override fun onPause() {
            println("Pausing player...")
            clear()
        }

        override fun onStop() {
            println("Stopping player...")
            isDirty = false
            clear()
        }
    }

    private val formatPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmm")
    private val dateFormat = SimpleDateFormat(formatPattern, Locale.getDefault())

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
        }.build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                this@MediaPlaybackService.playbackState = when (playbackState) {
                    Player.STATE_IDLE -> PlaybackStateCompat.STATE_STOPPED
                    Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
                    Player.STATE_READY -> PlaybackStateCompat.STATE_PLAYING
                    else -> return
                }
                updateMetadata()
            }
        })
        selectedMediaId =
            PreferenceManager.getDefaultSharedPreferences(this).getString("format", "mp3") ?: "mp3"
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        fetcher.start {
            updateMetadata()
        }

        stateBuilder =
            PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            )

        mediaSession = MediaSessionCompat(baseContext, "MediaPlaybackService").apply {
            setCallback(callback)
            setSessionToken(sessionToken)
            setExtras(Bundle(bundle))
            setMetadata(metadataBuilder.build())
            setPlaybackState(stateBuilder.build())
            isActive = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        fetcher.stop()
        mediaSession.run {
            isActive = false
            release()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onGetRoot(
        clientPackageName: String, clientUid: Int, rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT, null)
    }

    override fun onLoadChildren(
        parentMediaId: String, result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(
            if (parentMediaId == ROOT) listOf(
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setTitle("MP3 320 kbps")
                        .setMediaId("mp3")
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                ),
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setTitle("AAC 128 kbps")
                        .setMediaId("aac")
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                ),
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setTitle("Ogg Opus 32 kbps")
                        .setMediaId("ogg")
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                ),
            ) else null
        )
    }

    private fun reinitPlayer(): String {
        try {
            player.stop()
        } catch (error: Throwable) {
            println(error)
            // never mind exceptions
        }

        PreferenceManager.getDefaultSharedPreferences(this@MediaPlaybackService).edit {
            putString("format", selectedMediaId)
        }

        val source = ConfigFetcher.getConfig("streamingUrlTemplate") + selectedMediaId
        player.setMediaItem(MediaItem.fromUri(source), true)
        return source
    }

    private fun clear() {
        reinitPlayer()
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocus)
        println("Player stopped.")
        playbackState = PlaybackStateCompat.STATE_STOPPED
        updateMetadata()
    }

    private fun updateMetadata() {
        val metadata = fetcher.currentData

        if (fetcher.error != null || metadata == null) {
            bundle.putBoolean(HAS_ERROR, true)
            mediaSession.apply {
                setExtras(Bundle(bundle))
                setPlaybackState(
                    stateBuilder.setState(
                        playbackState,
                        0,
                        1f,
                        SystemClock.elapsedRealtime()
                    ).build()
                )
            }
            return
        }

        val songHistory = metadata.getJSONArray("song_history")
        val song = songHistory.getJSONObject(0)
        val program = metadata.getJSONObject("program")
        val programCovers = program.getJSONArray("cover")
        val programImageSrc =
            programCovers.getJSONObject(programCovers.length() - 1).getString("src")
        val songProgress = metadata.getLong("current_time") - song.getLong("start_time")
        val djs = program.getJSONArray("djs")
        val dj = if (djs.length() > 0) djs.getJSONObject(0) else null

        val formattedSongHistory = mutableListOf<String>()

        for (i in songHistory.length() - 1 downTo 1) {
            val songInstance = songHistory.getJSONObject(i)
            val title = songInstance.getString("title")
            val artist = songInstance.getString("artist")
            val album = songInstance.getString("album")
            val line = listOf(album, artist, title).filter { item -> item.isNotBlank() }
                .joinToString(" - ")

            val startTime = (songInstance.getLong("start_time")).toDate()
            formattedSongHistory.add("${dateFormat.format(startTime)} · $line")
        }

        var djImageSrc = "about:blank"
        if (dj != null) {
            val avatars = dj.getJSONArray("avatar")
            for (i in 0 until avatars.length()) {
                val avatar = avatars.getJSONObject(i)
                if (avatar.getString("sizes") == "any") {
                    djImageSrc = avatar.getString("src")
                    break
                }
            }
        }

        var songImageSrc = "about:blank"
        val covers = song.optJSONArray("cover")
        if (covers != null) {
            for (i in 0 until covers.length()) {
                val avatar = covers.getJSONObject(i)
                if (avatar.getString("sizes") == "any") {
                    songImageSrc = avatar.getString("src")
                    break
                }
            }
        }

        bundle.apply {
            putString(PROGRAM_IMAGE, programImageSrc)
            putString(DJ_IMAGE, djImageSrc)
            putString(PROGRAM_NAME, program.getString("name"))
            putString(DJ_NAME, dj?.getString("name"))
            putString(PROGRAM_GENRE, program.getString("genre"))
            putString(SONG_TITLE, song.getString("title"))
            putString(SONG_ARTIST, song.getString("artist"))
            putString(SONG_ALBUM, song.getString("album"))
            putString(SONG_IMAGE, songImageSrc)
            putStringArray(SONG_HISTORY, formattedSongHistory.toTypedArray())
            putLong(CURRENT_TIME, metadata.getLong("current_time"))
            putLong(SONG_START_TIME, song.getLong("start_time"))
            putLong(SONG_DURATION, song.optLong("duration", -1L))
            putString(PROGRAM_DESCRIPTION, program.getString("description"))
            putLong(LAST_UPDATED_TIME, fetcher.lastUpdatedAt)
            putInt(
                PROGRAM_TYPE, when (program.getString("type")) {
                    "live" -> PROGRAM_TYPE_LIVE
                    "podcast" -> PROGRAM_TYPE_PODCAST
                    "playlist" -> PROGRAM_TYPE_PLAYLIST
                    else -> PROGRAM_TYPE_PLAYLIST
                }
            )
            putBoolean(HAS_ERROR, false)
        }

        metadataBuilder
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getString("title"))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getString("artist"))
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.getString("album"))
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                (song.optLong("duration", -1L))
            )

        if (this.songImageSrc != songImageSrc) {
            Glide.with(this).asBitmap().load(songImageSrc).into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    this@MediaPlaybackService.songImageSrc = songImageSrc
                    println("image loaded: $songImageSrc")
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
        }

        mediaSession.apply {
            setExtras(Bundle(bundle))
            setMetadata(metadataBuilder.build())
            setPlaybackState(
                stateBuilder.setState(
                    playbackState,
                    songProgress,
                    1f,
                    SystemClock.elapsedRealtime()
                ).build()
            )
        }

        displayNotification()
    }

    private fun displayNotification() {
        if (!isDirty) {
            return
        }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(
                "MediaPlayback", "MediaPlaybackService", NotificationManager.IMPORTANCE_LOW
            )
        )

        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_PLAY_PAUSE
        )

        val stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_STOP
        )

        val description = mediaSession.controller.metadata.description

        val action =
            if (playbackState == PlaybackStateCompat.STATE_STOPPED)
                NotificationCompat.Action(
                    R.drawable.ic_baseline_play_arrow_24,
                    getString(R.string.play),
                    playPauseIntent
                )
            else
                NotificationCompat.Action(
                    R.drawable.ic_baseline_pause_24,
                    getString(R.string.pause),
                    playPauseIntent
                )

        startForeground(
            1,
            NotificationCompat.Builder(this, "MediaPlayback")
                .setContentTitle(description.title)
                .setContentText(description.subtitle)
                .setLargeIcon(description.iconBitmap)
                .setContentIntent(mediaSession.controller.sessionActivity)
                .setDeleteIntent(stopIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_onesignal_large_icon_default)
                .setColor(ContextCompat.getColor(this, R.color.md_theme_primary))
                .addAction(action)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
                        .setShowActionsInCompactView(0)
                )
                .build()
        )
    }
}