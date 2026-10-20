package com.radiojhero.app.ui.interact

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.android.volley.toolbox.StringRequest
import com.radiojhero.app.R
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.NetworkSingleton
import com.radiojhero.app.fetchers.SongsFetcher
import com.radiojhero.app.services.MediaPlaybackService

class InteractViewModel(private val app: Application) : AndroidViewModel(app) {

    data class Alert(val title: String, val message: String)

    var name by mutableStateOf("")
        private set
    var song by mutableStateOf("")
        private set
    var message by mutableStateOf("")
        private set

    /** Text typed in the song search field (playlist requests). */
    var songQuery by mutableStateOf("")
        private set
    var selectedSong by mutableStateOf<SongsFetcher.Song?>(null)
        private set
    var songs by mutableStateOf<List<SongsFetcher.Song>>(emptyList())
        private set

    @get:StringRes
    var nameError by mutableStateOf<Int?>(null)
        private set

    @get:StringRes
    var songError by mutableStateOf<Int?>(null)
        private set

    @get:StringRes
    var messageError by mutableStateOf<Int?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set
    var alert by mutableStateOf<Alert?>(null)
        private set

    init {
        SongsFetcher.fetch(app) {
            songs = it ?: listOf()
        }
    }

    fun onNameChange(value: String) {
        name = value.take(50)
    }

    fun onSongChange(value: String) {
        song = value
        val currentMessage = message.trim()
        if (currentMessage.isBlank() || currentMessage.length >= 10) {
            messageError = null
        }
    }

    fun onMessageChange(value: String) {
        message = value.take(320)
        val currentSong = song.trim()
        if (currentSong.isBlank() || currentSong.length >= 8) {
            songError = null
        }
    }

    fun onSongQueryChange(value: String) {
        if (selectedSong != null) {
            selectedSong = null
            songQuery = ""
            return
        }

        songQuery = value
    }

    fun onSongSelected(song: SongsFetcher.Song) {
        selectedSong = song
        songQuery = song.toString()
        songError = null
    }

    fun onSongQueryFocusLost() {
        if (selectedSong == null) {
            songQuery = ""
        }

        validateSongAutocomplete()
    }

    fun dismissAlert() {
        alert = null
    }

    @Synchronized
    fun send(programType: Int) {
        if (isLoading || !validateFields(programType)) {
            return
        }

        val isLive = programType == MediaPlaybackService.PROGRAM_TYPE_LIVE
        val url = ConfigFetcher.getConfig(if (isLive) "interactUrl" else "offlineInteractUrl") ?: ""
        val params = if (isLive) mutableMapOf(
            "nome" to name,
            "pedido" to song,
            "recado" to message,
        ) else mutableMapOf(
            "name" to name,
            "file" to (selectedSong?.path ?: ""),
            "message" to message,
        )

        val request = object : StringRequest(Method.POST, url, { responseString ->
            isLoading = false

            if (if (isLive) responseString == "true" else responseString.isBlank()) {
                showAlert(
                    R.string.success,
                    app.getString(if (isLive) R.string.interact_success else R.string.interact_success_playlist)
                )
                name = ""
                song = ""
                message = ""
                songQuery = ""
                selectedSong = null
            } else {
                showAlert(R.string.error, responseString)
            }
        }, { error ->
            println(error)
            isLoading = false
            showAlert(R.string.error, app.getString(R.string.error_long))
        }) {
            override fun getParams(): MutableMap<String, String> = params
        }

        isLoading = true
        NetworkSingleton.getInstance(app).requestQueue.add(request)
    }

    private fun showAlert(@StringRes titleId: Int, message: String) {
        alert = Alert(app.getString(titleId), message)
    }

    private fun validateFields(programType: Int): Boolean {
        val isLive = programType == MediaPlaybackService.PROGRAM_TYPE_LIVE
        val isNameValid = validateName()
        val isSongValid = if (isLive) validateSong() else validateSongAutocomplete()
        val isMessageValid = validateMessage(programType)
        return isNameValid && isSongValid && isMessageValid
    }

    fun validateName(): Boolean {
        val value = name.trim()
        nameError = when {
            value.isBlank() -> R.string.required_field
            value.length < 3 -> R.string.value_too_short
            else -> null
        }

        return nameError == null
    }

    fun validateSong(): Boolean {
        songError = when {
            song.isBlank() && message.isBlank() -> R.string.required_song_field
            else -> null
        }

        return songError == null
    }

    private fun validateSongAutocomplete(): Boolean {
        songError = if (selectedSong == null) R.string.required_field else null
        return songError == null
    }

    fun validateMessage(programType: Int): Boolean {
        val value = message.trim()
        messageError = when {
            value.isBlank() -> when {
                song.isBlank() && programType == MediaPlaybackService.PROGRAM_TYPE_LIVE -> R.string.required_message_field
                else -> null
            }

            value.length < 10 -> R.string.value_too_short
            else -> null
        }

        return messageError == null
    }
}
