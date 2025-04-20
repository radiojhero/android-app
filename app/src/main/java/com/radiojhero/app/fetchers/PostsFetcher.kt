package com.radiojhero.app.fetchers

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.radiojhero.app.getNow
import org.json.JSONObject
import java.nio.charset.Charset
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class PostsFetcher {

    data class Post(
        val title: String = "",
        val subtitle: String = "",
        val category: String = "",
        val coverImage: String = "",
        val link: String = "",
        val date: OffsetDateTime = OffsetDateTime.now(),
        val random: Long = getNow(),
    )

    companion object {
        private const val QUERY = """
        query GetPosts(${'$'}offset: Int! = 0) {
          posts(
            where: { status: PUBLISHED, type: ARTICLE, deletedAt: null }
            options: {
              limit: 60
              offset: ${'$'}offset
              sort: [{ sticky: DESC }, { publishedAt: DESC }]
            }
          ) {
            id
            slug
            revisionsConnection(where: { edge: { status: CURRENT } }) {
              edges {
                node {
                  title
                  lead
                  sticky
                  publishedAt
                  cover {
                    url
                  }
                  category {
                    id
                    title
                    slug
                  }
                }
              }
            }
          }
        }
        """
        private var offset = 0
        private var isFetching = false
        private var currentRequest: JsonObjectRequest? = null

        fun fetch(
            context: Context, reset: Boolean, completionHandler: (metadata: List<Post>?) -> Unit
        ) {
            if (isFetching) {
                return
            }

            if (reset) {
                offset = 0
            }

            val url = ConfigFetcher.getConfig("postsUrl")

            val body = JSONObject(
                mapOf(
                    "query" to QUERY,
                    "operationName" to "GetPosts",
                    "variables" to mapOf("offset" to offset),
                )
            )

            currentRequest = JsonObjectRequest(Request.Method.POST, url, body, { response ->
                try {
                    val data = response.getJSONObject("data").getJSONArray("posts")
                    val posts = mutableListOf<Post>()

                    offset += 1
                    for (index in 0 until data.length()) {
                        val post = data.getJSONObject(index)
                        val node = post.getJSONObject("revisionsConnection").getJSONArray("edges")
                            .getJSONObject(0).getJSONObject("node")

                        val date = OffsetDateTime.parse(node.getString("publishedAt"))
                        val link = "/${node.getJSONObject("category").getString("slug")}/${
                            date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
                        }/${post.getString("slug")}"

                        posts.add(
                            Post(
                                node.getString("title"),
                                node.getString("lead"),
                                node.getJSONObject("category").getString("title"),
                                node.getJSONObject("cover").getString("url"),
                                link,
                                date,
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
                println(
                    "Error while fetching posts: $error ${
                        error.networkResponse.data.toString(
                            Charset.forName("utf-8")
                        )
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