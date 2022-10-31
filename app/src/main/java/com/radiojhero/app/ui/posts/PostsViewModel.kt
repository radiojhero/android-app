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
    private val _hasError = MutableStateFlow(false)
    val hasError = _hasError.asStateFlow()

    fun fetch(reset: Boolean) {
        _hasError.update { false }
        PostsFetcher.fetch(app, reset) {
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
}
