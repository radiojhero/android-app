package com.radiojhero.app

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.radiojhero.app.services.MediaPlaybackService

/**
 * Connects the activity to [MediaPlaybackService] and exposes its playback state and metadata
 * (the media session extras) as Compose state.
 */
class PlayerConnection(private val activity: Activity) {

    /** Whether the stream is playing or buffering. */
    var isPlaying by mutableStateOf(false)
        private set

    /** Media session extras (see the keys in [MediaPlaybackService]); null until connected. */
    var extras by mutableStateOf<Bundle?>(null)
        private set

    private var controller: MediaControllerCompat? = null

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            isPlaying = isActive(state?.state)
        }

        override fun onExtrasChanged(extras: Bundle?) {
            this@PlayerConnection.extras = extras ?: Bundle().apply {
                putBoolean(MediaPlaybackService.HAS_ERROR, true)
            }
        }
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            MediaControllerCompat(activity, mediaBrowser.sessionToken).apply {
                MediaControllerCompat.setMediaController(activity, this)
                controller = this
                transportControls.prepare()
                isPlaying = isActive(playbackState?.state)
                this@PlayerConnection.extras = extras ?: Bundle()
                registerCallback(controllerCallback)
            }
        }
    }

    private val mediaBrowser: MediaBrowserCompat = MediaBrowserCompat(
        activity,
        ComponentName(activity, MediaPlaybackService::class.java),
        connectionCallbacks,
        null
    )

    fun connect() {
        mediaBrowser.connect()
    }

    fun disconnect() {
        controller?.unregisterCallback(controllerCallback)
        controller = null
        mediaBrowser.disconnect()
    }

    fun togglePlayback() {
        val transportControls = controller?.transportControls ?: return
        if (isPlaying) {
            transportControls.pause()
        } else {
            transportControls.play()
        }
    }

    fun switchFormat(format: String) {
        controller?.transportControls?.playFromMediaId(format, null)
    }

    private fun isActive(state: Int?): Boolean {
        return state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_BUFFERING
    }
}
