package com.radiojhero.app.fetchers

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import java.nio.charset.Charset

class SongsFetcher {

    data class Song(
        val album: String,
        val artist: String,
        val title: String,
        val cover: Uri,
        val path: String,
    ) {
        override fun toString(): String {
            return listOf(album, artist, title).filter { it.isNotBlank() }.joinToString(" - ")
        }
    }

    companion object {
        private var isFetching = false
        private var currentRequest: JsonObjectRequest? = null

        fun fetch(
            context: Context, completionHandler: (metadata: List<Song>?) -> Unit
        ) {
            if (isFetching) {
                return
            }

            val url = ConfigFetcher.getConfig("songsUrl")

            currentRequest = JsonObjectRequest(Request.Method.GET, url, null, { response ->
                try {
                    val songs = mutableListOf<Song>()

                    for (path in response.keys()) {
                        val obj = response.getJSONObject(path)
                        songs.add(
                            Song(
                                obj.getString("album"),
                                obj.getString("artist"),
                                obj.getString("title"),
                                obj.getString("cover").toUri(),
                                path,
                            )
                        )
                    }

                    songs.sortBy {
                        it.toString()
                    }

                    println("Songs fetched and parsed.")
                    completionHandler(songs)
                } catch (error: Throwable) {
                    println("Error while fetching songs: $error")
                    completionHandler(null)
                } finally {
                    isFetching = false
                }
            }, { error ->
                println(
                    "Error while fetching songs: $error ${
                        error.networkResponse?.data?.toString(
                            Charset.forName("utf-8")
                        ) ?: "unknown"
                    }"
                )
                completionHandler(null)
                isFetching = false
            })

            println("Fetching posts...")
            isFetching = true
            NetworkSingleton.getInstance(context).requestQueue.add(currentRequest)
        }
    }
}