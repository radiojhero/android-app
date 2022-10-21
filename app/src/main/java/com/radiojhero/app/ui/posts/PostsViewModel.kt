package com.radiojhero.app.ui.posts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.radiojhero.app.fetchers.PostsFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


class PostsViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<List<PostsFetcher.Post>>(emptyList())
    val uiState = _uiState.asStateFlow()

    fun fetch(reset: Boolean) {
        PostsFetcher.fetch(app, reset) {
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
}
