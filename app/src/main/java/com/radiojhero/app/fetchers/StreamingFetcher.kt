package com.radiojhero.app.fetchers

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject

class StreamingFetcher {

    data class StreamingStateChangeEvent(val isRunning: Boolean)

    companion object {
        val isRunning get() = mIsRunning
        private val mPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
        }
        private var mIsRunning = false
        private var mAlbumArt = ""
        private var mFirstTime = true

        fun start() {
            if (mFirstTime) {
                mFirstTime = false
                registerForNotifications()
                setupRemoteTransportControls()
            }

            if (mIsRunning) {
                return
            }

            mIsRunning = true

            mPlayer.apply {
                setDataSource(ConfigFetcher.getConfig("streamingUrl"))
                prepareAsync()
                setOnPreparedListener {
                    it.start()
                }
            }
            setupNowPlaying(MetadataFetcher.currentData)
            postEvent()
            println("Streaming is now playing.")

            // TODO: start config fetcher and metadata fetcher if app is currently in background
        }

        fun stop() {
            if (!mIsRunning) {
                return
            }

            mIsRunning = false
            mPlayer.reset()
            postEvent()
            println("Streaming has been paused.")

            // TODO: stop config fetcher and metadata fetcher if app is currently in background
        }

        fun toggle() {
            if (mIsRunning) stop() else start()
        }

        private fun postEvent() {
            EventBus.getDefault().post(StreamingStateChangeEvent(mIsRunning))
        }

        private fun registerForNotifications() {
            // TODO: when receiving metadata, call setupNowPlaying()
        }

        private fun setupRemoteTransportControls() {
            // TODO: configure the OS's play and pause buttons
        }

        private fun setupNowPlaying(metadata: JSONObject?) {
            // TODO: update the OS's now playing info
        }
    }
}