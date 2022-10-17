package com.radiojhero.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.radiojhero.app.R
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.MetadataFetcher
import kotlin.math.roundToLong

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
        const val PROGRAM_DESCRIPTION = "com.radiojhero.app.PROGRAM_DESCRIPTION"
        const val LAST_UPDATED_TIME = "com.radiojhero.app.LAST_UPDATED_TIME"
        const val IS_LIVE = "com.radiojhero.app.IS_LIVE"
    }

    private val fetcher = MetadataFetcher().apply {
        prepare(this@MediaPlaybackService)
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    private var playbackState = PlaybackStateCompat.STATE_NONE
    private val metadataBuilder = MediaMetadataCompat.Builder()
    private var bundle = Bundle()

    private var programImageSrc = ""

    private val player = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (player.isPlaying) {
                return
            }

            player.apply {
                val source = ConfigFetcher.getConfig("streamingUrl")
                setDataSource(source)
                prepareAsync()
                setOnPreparedListener {
                    println("Playing at $source")
                    it.start()
                }
            }

            super.onPlay()
            playbackState = PlaybackStateCompat.STATE_PLAYING
            updateMetadata()
        }

        override fun onPause() {
            if (!player.isPlaying) {
                return
            }

            super.onPause()
            clear()
        }

        override fun onStop() {
            if (!player.isPlaying) {
                return
            }

            super.onStop()
            clear()
        }

        private fun clear() {
            player.reset()
            println("Player stopped.")
            playbackState = PlaybackStateCompat.STATE_NONE
            updateMetadata()
        }
    }

    override fun onCreate() {
        super.onCreate()

        fetcher.start {
            updateMetadata()
        }

        stateBuilder =
            PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_PAUSE
            )

        mediaSession = MediaSessionCompat(baseContext, "MediaPlaybackService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

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
        player.reset()
        fetcher.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onGetRoot(
        clientPackageName: String, clientUid: Int, rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("@empty@", null)
    }

    override fun onLoadChildren(
        parentMediaId: String, result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(null)
    }

    private fun updateMetadata() {
        val metadata = fetcher.currentData
        val song = metadata.getJSONArray("song_history").getJSONObject(0)
        val program = metadata.getJSONObject("program")
        val programCovers = program.getJSONArray("cover")
        val programImageSrc =
            programCovers.getJSONObject(programCovers.length() - 1).getString("src")
        val songProgress = metadata.getDouble("current_time") - song.getDouble("start_time")
        val djs = program.getJSONArray("djs")
        val dj = if (djs.length() > 0) djs.getJSONObject(0) else null

        bundle.apply {
            putString(PROGRAM_IMAGE, programImageSrc)
            putString(DJ_IMAGE, dj?.getString("avatar"))
            putString(PROGRAM_NAME, program.getString("name"))
            putString(DJ_NAME, dj?.getString("name"))
            putString(PROGRAM_GENRE, program.getString("genre"))
            putString(SONG_TITLE, song.getString("title"))
            putString(SONG_ARTIST, song.getString("artist"))
            putDouble(CURRENT_TIME, metadata.getDouble("current_time"))
            putDouble(SONG_START_TIME, song.getDouble("start_time"))
            putDouble(SONG_DURATION, song.getDouble("duration"))
            putString(PROGRAM_DESCRIPTION, program.getString("description"))
            putDouble(LAST_UPDATED_TIME, fetcher.lastUpdatedAt)
            putBoolean(IS_LIVE, metadata.getBoolean("is_live"))
        }

        metadataBuilder
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getString("title"))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getString("artist"))
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                (song.getDouble("duration") * 1000).roundToLong()
            )

        if (this.programImageSrc != programImageSrc) {
            Glide.with(this).asBitmap().load(programImageSrc).into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    this@MediaPlaybackService.programImageSrc = programImageSrc
                    println("image loaded: $programImageSrc")
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
                    (songProgress * 1000).roundToLong(),
                    1f,
                    SystemClock.elapsedRealtime()
                ).build()
            )
        }

        displayNotification()
    }

    private fun displayNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(
                    "MediaPlayback", "MediaPlaybackService", NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_STOP
        )

        val description = mediaSession.controller.metadata.description

        val action =
            if (playbackState == PlaybackStateCompat.STATE_NONE)
                NotificationCompat.Action(
                    R.drawable.ic_baseline_play_arrow_24,
                    getString(R.string.play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this, PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            else
                NotificationCompat.Action(
                    R.drawable.ic_baseline_pause_24,
                    getString(R.string.pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this, PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )

        startForeground(
            1,
            NotificationCompat.Builder(this, "MediaPlayback")
                .setContentTitle(description.title)
                .setContentText(description.subtitle)
                .setSubText(description.description)
                .setLargeIcon(description.iconBitmap)
                .setContentIntent(mediaSession.controller.sessionActivity)
                .setDeleteIntent(stopIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_onesignal_large_icon_default)
                .setColor(ContextCompat.getColor(this, R.color.jh_1))
                .addAction(action)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0)
                        .setShowCancelButton(true).setCancelButtonIntent(stopIntent)
                )
                .build()
        )
    }
}