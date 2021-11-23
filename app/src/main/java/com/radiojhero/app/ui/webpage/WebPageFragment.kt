package com.radiojhero.app.ui.webpage

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
import androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
import androidx.webkit.WebViewFeature
import com.radiojhero.app.R
import com.radiojhero.app.databinding.FragmentWebpageBinding
import com.radiojhero.app.fetchers.ConfigFetcher


class WebPageFragment : Fragment() {

    private var _binding: FragmentWebpageBinding? = null
    private val binding get() = _binding!!

    private val args: WebPageFragmentArgs by navArgs()
    private var hasLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebpageBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    WebSettingsCompat.setForceDark(binding.webpageWebview.settings, FORCE_DARK_ON)
                }
                Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                    WebSettingsCompat.setForceDark(binding.webpageWebview.settings, FORCE_DARK_OFF)
                }
                else -> {
                    //
                }
            }
        }

        updatePaddingBottom()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hasLoaded = false
        _binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val webView = binding.webpageWebview

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(webView: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(webView, url, favicon)
                binding.webpageProgress.visibility = View.GONE
                binding.webpageWebview.visibility = View.VISIBLE
                updatePaddingBottom()
            }

            override fun onPageFinished(webView: WebView, url: String) {
                super.onPageFinished(webView, url)
                hasLoaded = true
            }

            override fun shouldOverrideUrlLoading(webView: WebView?, url: String?): Boolean {
                if (!hasLoaded) {
                    return false
                }

                if (Uri.parse(url).host?.startsWith(ConfigFetcher.getConfig("host") ?: "") == true) {
                    val action = WebPageFragmentDirections.actionNavigationWebpageSelf(url ?: "")
                    findNavController().navigate(action)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url ?: ""))
                    startActivity(intent)
                }

                return true
            }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 RadioJHero/1.0 (Android)"

        webView.loadUrl(args.url)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.webpage_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_share) {
            return super.onOptionsItemSelected(item)
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, binding.webpageWebview.url)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
        return true
    }

    private fun updatePaddingBottom() {
        val webView = binding.webpageWebview
        val paddingBottom = webView.paddingBottom / resources.displayMetrics.density
        webView.loadUrl("javascript:document.documentElement.style.setProperty('--android-extra-bottom-padding', '${paddingBottom}px')")
    }
}