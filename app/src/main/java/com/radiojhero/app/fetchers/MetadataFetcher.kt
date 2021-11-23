package com.radiojhero.app.fetchers

import android.content.Context
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.radiojhero.app.getNow
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.max
import kotlin.math.min

class MetadataFetcher {

    data class MetadataEvent(val data: JSONObject)
    data class MetadataErrorEvent(val error: VolleyError)

    companion object {
        val currentData get() = mData
        val lastUpdatedAt get() = mLastUpdatedAt
        val isRunning get() = mIsRunning
        private var mLastUpdatedAt: Double = 0.0
        private var mIsRunning = false
        private var mData: JSONObject? = null
        private var mMetadataDelay: Timer? = null
        private lateinit var mNetwork: NetworkSingleton
        private var mCurrentRequest: JsonObjectRequest? = null

        fun start(context: Context? = null) {
            if (!this::mNetwork.isInitialized) {
                if (context == null) {
                    throw Exception()
                }

                mNetwork = NetworkSingleton.getInstance(context)
            }

            if (mIsRunning) {
                return
            }

            mIsRunning = true
            println("Metadata fetcher started.")
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
            println("Metadata fetcher stopped.")
        }

        private fun fetch() {
            println("Fetching metadata...")
            val url = ConfigFetcher.getConfig("metadataUrl") ?: ""
            mCurrentRequest = JsonObjectRequest(
                Request.Method.GET, url, null,
                { data ->
                    mData = data
                    mLastUpdatedAt = getNow()
                    println("Metadata fetched and parsed.")
                    EventBus.getDefault().post(MetadataEvent(data))

                    var delay = 15.0
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
                    } catch (error: Exception) {
                    }
                    setTimeout(delay)
                },
                { error ->
                    println("Error while fetching metadata: $error")
                    EventBus.getDefault().post(MetadataErrorEvent(error))
                    setTimeout(15.0)
                }
            )

            mNetwork.requestQueue.add(mCurrentRequest)
        }

        private fun setTimeout(delay: Double) {
            mMetadataDelay?.cancel()
            mMetadataDelay = Timer()
            mMetadataDelay?.schedule((delay * 1000).toLong()) {
                fetch()
            }
        }
    }
}