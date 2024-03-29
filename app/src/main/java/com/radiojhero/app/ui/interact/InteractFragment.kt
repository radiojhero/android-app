package com.radiojhero.app.ui.interact

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.android.volley.toolbox.StringRequest
import com.radiojhero.app.R
import com.radiojhero.app.databinding.FragmentInteractBinding
import com.radiojhero.app.endEditing
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.NetworkSingleton
import com.radiojhero.app.services.MediaPlaybackService


class InteractFragment : Fragment() {

    private var binding: FragmentInteractBinding? = null

    private var availability = false
    private lateinit var network: NetworkSingleton

    private lateinit var mediaController: MediaControllerCompat

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onExtrasChanged(extras: Bundle?) {
            maybeToggleForm(extras?.getBoolean(MediaPlaybackService.IS_LIVE) ?: false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
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
                    if (value.isEmpty() || value.length >= 10) {
                        error = null
                    }
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
                    if (value.isEmpty() || value.length >= 8) {
                        error = null
                    }
                }
            }
        }

        binding = inflated

        mediaController = MediaControllerCompat.getMediaController(requireActivity()).apply {
            registerCallback(controllerCallback)
            maybeToggleForm(extras.getBoolean(MediaPlaybackService.IS_LIVE))
        }

        return inflated.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    @Synchronized
    private fun makeSongRequest() {
        activity?.endEditing()

        if (!validateFields()) {
            return
        }

        val binding = this.binding ?: return

        val song = (binding.editSong.text ?: "").toString()
        val url = ConfigFetcher.getConfig("interactUrl") ?: ""

        val request = object : StringRequest(Method.POST, url, { responseString ->
            binding.apply {
                sendButton.visibility = View.VISIBLE
                progressIndicator.visibility = View.INVISIBLE
            }

            if (responseString == "true") {
                alert(R.string.success, R.string.interact_success)
                binding.apply {
                    editName.text = null
                    editSong.text = null
                    editMessage.text = null
                }
            } else {
                alert(
                    R.string.error,
                    HtmlCompat.fromHtml(responseString, HtmlCompat.FROM_HTML_MODE_COMPACT)
                        .toString()
                )
            }
        }, { error ->
            println(error)
            alert(R.string.error, R.string.error_long)
        }) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "nome" to (binding.editName.text ?: "").toString(),
                    "email" to "deprecated@example.com",
                    "cidade" to "deprecated",
                    "pedido" to song,
                    "recado" to (binding.editMessage.text ?: "").toString(),
                    "aba" to if (song == "") "recado" else "pedido",
                    "request" to ConfigFetcher.getConfig("interactKey") as String,
                    "is_app" to "true",
                )
            }
        }

        binding.apply {
            sendButton.visibility = View.INVISIBLE
            progressIndicator.visibility = View.VISIBLE
        }
        network.requestQueue.add(request)
    }

    private fun validateFields(): Boolean {
        return validateName() && validateSong() && validateMessage()
    }

    private fun validateName(): Boolean {
        val binding = this.binding ?: return false

        binding.editName.apply {
            val value = text?.trim() ?: ""
            error = when {
                value.isEmpty() -> context.getString(R.string.required_field)
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
                value.isEmpty() -> when {
                    binding.editMessage.text?.trim()
                        ?.isEmpty() ?: true -> context.getString(R.string.required_song_field)
                    else -> null
                }
                value.length < 8 -> context.getString(R.string.value_too_short)
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
                value.isEmpty() -> when {
                    binding.editSong.text?.trim()
                        ?.isEmpty() ?: true -> context.getString(R.string.required_message_field)
                    else -> null
                }
                value.length < 10 -> context.getString(R.string.value_too_short)
                else -> null
            }

            return error == null
        }
    }

    private fun maybeToggleForm(toggle: Boolean) {
        val binding = this.binding ?: return


        if (availability) {
            return
        }

        binding.unavailable.visibility = if (toggle) View.GONE else View.VISIBLE
        binding.requestsForm.visibility = if (toggle) View.VISIBLE else View.GONE
        availability = toggle
    }

    private fun alert(@StringRes titleId: Int, @StringRes messageId: Int) {
        alert(getString(titleId), getString(messageId))
    }

    private fun alert(@StringRes titleId: Int, message: String) {
        alert(getString(titleId), message)
    }

    private fun alert(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.button_dismiss, null)
            .show()
    }
}