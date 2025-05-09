package com.radiojhero.app.fetchers

import android.content.Context
import androidx.core.net.toUri
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.radiojhero.app.getNow
import org.json.JSONObject
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.max
import kotlin.math.min

class MetadataFetcher {

    val currentData get() = mData
    val lastUpdatedAt get() = mLastUpdatedAt
    val error get() = mError
    var offset = 0L
    private val interval = 15000L
    private var mLastUpdatedAt = 0L
    private var mIsRunning = false
    private var mData: JSONObject? = null
    private var mMetadataDelay: Timer? = null
    private lateinit var mNetwork: NetworkSingleton
    private var mCurrentRequest: JsonObjectRequest? = null
    private var mCallback: () -> Unit = { }
    private var mError: VolleyError? = null

    fun prepare(context: Context) {
        mNetwork = NetworkSingleton.getInstance(context)
    }

    fun start(callback: () -> Unit = { }) {
        if (mIsRunning) {
            return
        }

        mIsRunning = true
        println("Metadata fetcher started.")
        mCallback = callback
        fetch()
    }

    fun stop() {
        if (!mIsRunning) {
            return
        }

        mIsRunning = false
        mMetadataDelay?.cancel()
        mMetadataDelay = null
        mCurrentRequest?.cancel()
        mCurrentRequest = null
        mCallback = { }
        println("Metadata fetcher stopped.")
    }

    fun forceFetch() {
        fetch()
    }

    private fun fetch() {
        val url = (ConfigFetcher.getConfig("metadataUrl") ?: "about:blank").toUri().buildUpon()
            .appendQueryParameter("offset", offset.toString()).build()
        println("Fetching metadata... $url")

        mCurrentRequest = JsonObjectRequest(Request.Method.GET, url.toString(), null, { data ->
            mError = null
            mData = data
            mLastUpdatedAt = getNow()
            println("Metadata fetched and parsed.")
            mCallback()

            var delay = interval
            try {
                val song = data.getJSONArray("song_history").getJSONObject(0)
                val songDuration = song.optLong("duration", -1L)
                if (songDuration >= 0) {
                    delay = max(
                        0L, min(
                            delay, song.getLong("start_time") + songDuration - data.getLong(
                                "current_time"
                            )
                        )
                    )
                }
            } catch (_: Exception) {
            }
            setTimeout(delay)
        }, { error ->
            println("Error while fetching metadata: $error")
            mError = error
            mCallback()
            setTimeout(interval)
        })

        mNetwork.requestQueue.add(mCurrentRequest)
    }

    private fun setTimeout(delay: Long) {
        mMetadataDelay?.cancel()
        mMetadataDelay = Timer().apply {
            schedule(delay) {
                fetch()
            }
        }
    }
}