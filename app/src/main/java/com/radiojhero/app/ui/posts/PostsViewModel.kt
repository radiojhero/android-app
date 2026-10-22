package com.radiojhero.app.ui.posts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.algolia.client.api.SearchClient
import com.algolia.client.model.search.HighlightResultOption
import com.algolia.client.model.search.Hit
import com.algolia.client.model.search.IgnorePlurals
import com.algolia.client.model.search.SearchParamsObject
import com.algolia.client.model.search.SnippetResultOption
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.PostsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max


class PostsViewModel(private val app: Application) : AndroidViewModel(app) {

    data class SearchStats(val nbHits: Int, val processingTimeMS: Long)

    private val _uiState = MutableStateFlow<List<PostsFetcher.Post>>(emptyList())
    val uiState = _uiState.asStateFlow()
    private val _hasError = MutableStateFlow(false)
    val hasError = _hasError.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<PostsFetcher.Post>>(emptyList())
        private set
    var searchStats by mutableStateOf<SearchStats?>(null)
        private set

    private val searchClient by lazy {
        SearchClient(
            appId = ConfigFetcher.getConfig("algoliaApp") ?: "",
            apiKey = ConfigFetcher.getConfig("algoliaKey") ?: "",
        )
    }
    private var searchPage = 0
    private var searchRemainingHits = 0
    private var isSearching = false

    init {
        fetch(true)
    }

    fun fetch(reset: Boolean) {
        _hasError.update { false }
        PostsFetcher.fetch(app, reset) {
            _isRefreshing.update { false }
            _hasError.update { _ ->
                it == null && reset
            }
            _uiState.update { current ->
                if (it == null) {
                    current
                } else if (reset) {
                    it
                } else {
                    current + it
                }
            }
        }
    }

    fun refresh() {
        _isRefreshing.update { true }
        fetch(true)
    }

    fun search(query: String) {
        searchQuery = query
        searchPage = 0
        doSearch()
    }

    fun loadMoreSearchResults() {
        if (searchRemainingHits == 0 || isSearching) {
            return
        }

        searchPage++
        doSearch()
    }

    private fun doSearch() {
        val query = searchQuery
        val page = searchPage

        if (query.isBlank()) {
            return
        }

        isSearching = true
        viewModelScope.launch {
            try {
                val params = SearchParamsObject(
                    query = query,
                    page = page,
                    ignorePlurals = IgnorePlurals.of(true),
                    advancedSyntax = true,
                    hitsPerPage = 60,
                    highlightPreTag = "<mark>",
                    highlightPostTag = "</mark>",
                    snippetEllipsisText = "…",
                    attributesToRetrieve = arrayListOf(
                        "title",
                        "body",
                        "path",
                        "cover",
                    ),
                    attributesToHighlight = arrayListOf(
                        "title",
                        "body",
                    ),
                    attributesToSnippet = arrayListOf(
                        "body",
                    ),
                )

                val response = withContext(Dispatchers.IO) {
                    searchClient.searchSingleIndex(INDEX, params)
                }
                if (response.query != searchQuery) {
                    return@launch
                }

                searchRemainingHits = max(0, (response.nbHits ?: 0) - (page + 1) * 60)
                val hits = deserialize(response.hits)

                if (page > 0) {
                    searchResults = searchResults + hits
                } else {
                    searchResults = hits
                    searchStats = SearchStats(
                        response.nbHits ?: 0, response.processingTimeMS?.toLong() ?: 0
                    )
                }
            } catch (error: Throwable) {
                println("Error while searching posts: $error")
            } finally {
                isSearching = false
            }
        }
    }

    private fun deserialize(hits: List<Hit>) = hits.map {
        val title = (it.highlightResult?.get("title") as HighlightResultOption).value
        val excerpt = (it.snippetResult?.get("body") as SnippetResultOption).value
        val permalink = it.additionalProperties?.get("path")!!.jsonPrimitive.content
        val thumbnail = try {
            it.additionalProperties?.get("cover")?.jsonPrimitive?.content ?: ""
        } catch (_: Throwable) {
            ""
        }

        PostsFetcher.Post(
            title = title,
            subtitle = excerpt,
            link = permalink,
            coverImage = thumbnail,
        )
    }

    private companion object {
        const val INDEX = "site_pages"
    }
}
