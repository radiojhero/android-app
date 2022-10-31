package com.radiojhero.app.fetchers

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PostsFetcher {

    data class Post(
        val title: String = "",
        val subtitle: String = "",
        val category: String = "",
        val coverImage: String = "",
        val link: String = "",
        val date: Date = Date(),
    )

    companion object {
        private const val query = """
        query getPosts(${'$'}cursor: String!) {
          posts(first: 60, after: ${'$'}cursor) {
            edges {
              cursor
              node {
                title(format: RENDERED)
                subtitle
                link
                dateGmt
                featuredImage {
                  node {
                    sourceUrl
                  }
                }
                categories {
                  nodes {
                    name
                  }
                }
              }
            }
          }
        }
        """
        private var cursor = ""
        private var isFetching = false
        private var currentRequest: JsonObjectRequest? = null
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        fun fetch(
            context: Context,
            reset: Boolean,
            completionHandler: (metadata: List<Post>?) -> Unit
        ) {
            if (isFetching) {
                return
            }

            if (reset) {
                cursor = ""
            }

            val url = ConfigFetcher.getConfig("postsUrl")

            val body = JSONObject(
                mapOf(
                    "query" to query,
                    "operationName" to "getPosts",
                    "variables" to mapOf("cursor" to cursor),
                )
            )

            currentRequest = JsonObjectRequest(Request.Method.POST, url, body, { response ->
                try {
                    val data =
                        response.getJSONObject("data").getJSONObject("posts").getJSONArray("edges")
                    val posts = mutableListOf<Post>()

                    for (index in 0 until data.length()) {
                        val edge = data.getJSONObject(index)
                        cursor = edge.getString("cursor")
                        val node = edge.getJSONObject("node")
                        posts.add(
                            Post(
                                node.getString("title"),
                                node.getString("subtitle"),
                                node.getJSONObject("categories").getJSONArray("nodes")
                                    .getJSONObject(0).getString("name"),
                                node.getJSONObject("featuredImage").getJSONObject("node")
                                    .getString("sourceUrl"),
                                node.getString("link").replace("wp.", ""),
                                dateFormatter.parse(node.getString("dateGmt"))!!
                            )
                        )
                    }

                    println("Posts fetched and parsed.")
                    completionHandler(posts)
                } catch (error: Throwable) {
                    println("Error while fetching posts: $error")
                    completionHandler(null)
                } finally {
                    isFetching = false
                }
            }, { error ->
                println("Error while fetching posts: $error")
                completionHandler(null)
                isFetching = false
            })

            println("Fetching posts...")
            isFetching = true
            NetworkSingleton.getInstance(context).requestQueue.add(currentRequest)
        }
    }
}