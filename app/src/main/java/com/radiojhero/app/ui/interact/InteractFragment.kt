package com.radiojhero.app.ui.interact

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.annotation.StringRes
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.android.volley.toolbox.StringRequest
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radiojhero.app.MainActivity
import com.radiojhero.app.R
import com.radiojhero.app.databinding.FragmentInteractBinding
import com.radiojhero.app.endEditing
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.NetworkSingleton
import com.radiojhero.app.fetchers.SongsFetcher
import com.radiojhero.app.services.MediaPlaybackService
import java.util.*
import kotlin.concurrent.schedule


class InteractFragment : Fragment() {

    private var binding: FragmentInteractBinding? = null

    private var programType = MediaPlaybackService.PROGRAM_TYPE_PLAYLIST
    private lateinit var network: NetworkSingleton

    private lateinit var mediaController: MediaControllerCompat

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onExtrasChanged(extras: Bundle?) {
            programType = extras?.getInt(MediaPlaybackService.PROGRAM_TYPE)
                ?: MediaPlaybackService.PROGRAM_TYPE_PLAYLIST
        }
    }

    private var songList = listOf<SongsFetcher.Song>()

    private var selectedFile = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        network = NetworkSingleton.getInstance(requireContext())
        val inflated = FragmentInteractBinding.inflate(inflater, container, false)
        inflated.apply {
            sendButton.setOnClickListener {
                makeSongRequest()
            }

            editName.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    return@setOnFocusChangeListener
                }

                validateName()
            }

            editSong.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    return@setOnFocusChangeListener
                }

                validateSong()
            }

            editSong.addTextChangedListener {
                editMessage.apply {
                    val value = text?.trim() ?: ""
                    if (value.isBlank() || value.length >= 10) {
                        error = null
                    }
                }
            }

            editSongAutocomplete.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && selectedFile.isBlank()) {
                    editSongAutocomplete.setText("")
                }

                if (hasFocus) {
                    return@setOnFocusChangeListener
                }

                validateSongAutocomplete()
            }

            editSongAutocomplete.addTextChangedListener {
                if (selectedFile.isNotBlank()) {
                    selectedFile = ""
                    editSongAutocomplete.setText("")
                }
            }

            editSongAutocomplete.onItemClickListener =
                AdapterView.OnItemClickListener { parent, _, position, _ ->
                    selectedFile = (parent.getItemAtPosition(position) as SongsFetcher.Song).path
                }
            SongsFetcher.fetch(requireActivity()) {
                songList = it ?: listOf()
                SongsAdapter(activity ?: return@fetch, R.layout.song_item, songList).apply {
                    editSongAutocomplete.setAdapter(this)
                }
            }

            editMessage.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    return@setOnFocusChangeListener
                }

                validateMessage()
            }

            editMessage.addTextChangedListener {
                editSong.apply {
                    val value = text?.trim() ?: ""
                    if (value.isBlank() || value.length >= 8) {
                        error = null
                    }
                }
            }
            interactRoot.setOnScrollChangeListener { _, _, y, _, _ ->
                (requireActivity() as MainActivity).toggleAppBarBackground(y > 0)
            }
            (requireActivity() as MainActivity).toggleAppBarBackground(interactRoot.scrollY > 0)
        }

        binding = inflated
        setupMediaController()

        activity?.findViewById<BottomNavigationView>(R.id.nav_view)
            ?.setOnItemReselectedListener { item ->
                if (item.itemId == R.id.navigation_interact) {
                    inflated.interactRoot.smoothScrollTo(0, 0)
                }
            }

        return inflated.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaController.unregisterCallback(controllerCallback)
        binding = null
    }

    private fun setupMediaController() {
        try {
            mediaController = MediaControllerCompat.getMediaController(requireActivity()).apply {
                registerCallback(controllerCallback)
                programType = extras.getInt(MediaPlaybackService.PROGRAM_TYPE)

                binding?.apply {
                    interactLabel.setText(
                        when (programType) {
                            MediaPlaybackService.PROGRAM_TYPE_LIVE -> R.string.label_interact
                            MediaPlaybackService.PROGRAM_TYPE_PODCAST -> R.string.interact_unavailable
                            MediaPlaybackService.PROGRAM_TYPE_PLAYLIST -> R.string.label_interact_playlist
                            else -> R.string.label_interact_playlist
                        }
                    )
                    editNameLayout.visibility =
                        if (programType == MediaPlaybackService.PROGRAM_TYPE_PODCAST) View.INVISIBLE else View.VISIBLE
                    editSongLayout.visibility =
                        if (programType == MediaPlaybackService.PROGRAM_TYPE_LIVE) View.VISIBLE else View.INVISIBLE
                    editSongAutocompleteLayout.visibility =
                        if (programType == MediaPlaybackService.PROGRAM_TYPE_PLAYLIST) View.VISIBLE else View.INVISIBLE
                    editMessageLayout.visibility =
                        if (programType == MediaPlaybackService.PROGRAM_TYPE_PODCAST) View.INVISIBLE else View.VISIBLE
                    sendButton.visibility =
                        if (programType == MediaPlaybackService.PROGRAM_TYPE_PODCAST) View.INVISIBLE else View.VISIBLE
                }
            }
        } catch (_: NullPointerException) {
            Timer().schedule(50) {
                println("Failed to set up mediaController; retrying...")
                requireActivity().runOnUiThread {
                    setupMediaController()
                }
            }
        }
    }

    @Synchronized
    private fun makeSongRequest() {
        activity?.endEditing()

        if (!validateFields()) {
            return
        }

        val binding = this.binding ?: return

        val song = if (programType == MediaPlaybackService.PROGRAM_TYPE_LIVE) (binding.editSong.text
            ?: "").toString() else selectedFile
        val url =
            ConfigFetcher.getConfig(if (programType == MediaPlaybackService.PROGRAM_TYPE_LIVE) "interactUrl" else "offlineInteractUrl")
                ?: ""

        val request = object : StringRequest(Method.POST, url, { responseString ->
            setLoading(false)

            if (if (programType == MediaPlaybackService.PROGRAM_TYPE_LIVE) responseString == "true" else responseString.isBlank()) {
                alert(
                    R.string.success,
                    if (programType == MediaPlaybackService.PROGRAM_TYPE_LIVE) R.string.interact_success else R.string.interact_success_playlist
                )
                binding.apply {
                    editName.text = null
                    editSong.text = null
                    editSongAutocomplete.text = null
                    editMessage.text = null
                    selectedFile = ""
                }
            } else {
                alert(R.string.error, responseString)
            }
        }, { error ->
            println(error)
            alert(R.string.error, R.string.error_long)
        }) {
            override fun getParams(): MutableMap<String, String> {
                return if (programType == MediaPlaybackService.PROGRAM_TYPE_LIVE) mutableMapOf(
                    "nome" to (binding.editName.text ?: "").toString(),
                    "pedido" to song,
                    "recado" to (binding.editMessage.text ?: "").toString(),
                ) else mutableMapOf(
                    "name" to (binding.editName.text ?: "").toString(),
                    "file" to song,
                    "message" to (binding.editMessage.text ?: "").toString(),
                )
            }
        }

        setLoading(true)
        network.requestQueue.add(request)
    }

    private fun validateFields(): Boolean {
        return validateName() && (if (programType == MediaPlaybackService.PROGRAM_TYPE_LIVE) validateSong() else validateSongAutocomplete()) && validateMessage()
    }

    private fun validateName(): Boolean {
        val binding = this.binding ?: return false

        binding.editName.apply {
            val value = text?.trim() ?: ""
            error = when {
                value.isBlank() -> context.getString(R.string.required_field)
                value.length < 3 -> context.getString(R.string.value_too_short)
                else -> null
            }

            return error == null
        }
    }

    private fun validateSong(): Boolean {
        val binding = this.binding ?: return false

        binding.editSong.apply {
            val value = text?.trim() ?: ""
            error = when {
                value.isBlank() -> when {
                    binding.editMessage.text?.trim()?.isBlank()
                        ?: true -> context.getString(R.string.required_song_field)

                    else -> null
                }

                value.length < 8 -> context.getString(R.string.value_too_short)
                else -> null
            }

            return error == null
        }
    }

    private fun validateSongAutocomplete(): Boolean {
        val binding = this.binding ?: return false

        binding.editSongAutocomplete.apply {
            error = when {
                selectedFile.isBlank() -> context.getString(R.string.required_field)
                else -> null
            }

            return error == null
        }
    }

    private fun validateMessage(): Boolean {
        val binding = this.binding ?: return false

        binding.editMessage.apply {
            val value = text?.trim() ?: ""
            error = when {
                value.isBlank() -> when {
                    (binding.editSong.text?.trim()?.isBlank()
                        ?: true) && programType == MediaPlaybackService.PROGRAM_TYPE_LIVE -> context.getString(
                        R.string.required_message_field
                    )

                    else -> null
                }

                value.length < 10 -> context.getString(R.string.value_too_short)
                else -> null
            }

            return error == null
        }
    }

    private fun setLoading(toggle: Boolean) {
        binding?.apply {
            sendButton.visibility = if (toggle) View.INVISIBLE else View.VISIBLE
            progressIndicator.visibility = if (toggle) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun alert(@StringRes titleId: Int, @StringRes messageId: Int) {
        alert(getString(titleId), getString(messageId))
    }

    private fun alert(@StringRes titleId: Int, message: String) {
        alert(getString(titleId), message)
    }

    private fun alert(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.button_dismiss, null).show()
    }
}