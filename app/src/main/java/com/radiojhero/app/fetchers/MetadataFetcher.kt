package com.radiojhero.app.fetchers

import android.content.Context
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
    private val interval = 15.0
    private var mLastUpdatedAt = 0.0
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

    private fun fetch() {
        println("Fetching metadata...")
        val url = ConfigFetcher.getConfig("metadataUrl") ?: ""
        mCurrentRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { data ->
                mError = null
                mData = data
                mLastUpdatedAt = getNow()
                println("Metadata fetched and parsed.")
                mCallback()

                var delay = interval
                try {
                    val song = data.getJSONArray("song_history").getJSONObject(0)
                    val songDuration = song.getDouble("duration")
                    if (songDuration >= 0) {
                        delay = max(
                            0.0,
                            min(
                                delay,
                                song.getDouble("start_time") + songDuration - data.getDouble(
                                    "current_time"
                                )
                            )
                        )
                    }
                } catch (_: Exception) {
                }
                setTimeout(delay)
            },
            { error ->
                println("Error while fetching metadata: $error")
                mError = error
                mCallback()
                setTimeout(interval)
            }
        )

        mNetwork.requestQueue.add(mCurrentRequest)
    }

    private fun setTimeout(delay: Double) {
        mMetadataDelay?.cancel()
        mMetadataDelay = Timer().apply {
            schedule((delay * 1000).toLong()) {
                fetch()
            }
        }
    }
}