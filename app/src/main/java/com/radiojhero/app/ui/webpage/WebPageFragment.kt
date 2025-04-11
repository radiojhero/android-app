package com.radiojhero.app.ui.webpage

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.webkit.WebViewClientCompat
import com.radiojhero.app.R
import com.radiojhero.app.databinding.FragmentWebpageBinding
import com.radiojhero.app.fetchers.ConfigFetcher


class WebPageFragment : Fragment() {

    private var binding: FragmentWebpageBinding? = null

    private val args: WebPageFragmentArgs by navArgs()
    private var hasLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inflated = FragmentWebpageBinding.inflate(inflater, container, false)
        binding = inflated
        return inflated.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hasLoaded = false
        binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                createMenu(menu, menuInflater)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return selectMenu(item)
            }
        }, viewLifecycleOwner, Lifecycle.State.STARTED)

        val webView = binding?.webpageWebview ?: return

        webView.webViewClient = object : WebViewClientCompat() {
            override fun onPageStarted(webView: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(webView, url, favicon)
                binding?.webpageProgress?.visibility = View.GONE
                binding?.webpageWebview?.visibility = View.VISIBLE
                updatePaddingBottom()
            }

            override fun onPageFinished(webView: WebView, url: String) {
                super.onPageFinished(webView, url)
                updatePaddingBottom()
                hasLoaded = true
            }

            override fun shouldOverrideUrlLoading(
                webView: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!hasLoaded) {
                    return false
                }

                if (request.url.host == ConfigFetcher.getConfig("host")) {
                    val action =
                        WebPageFragmentDirections.actionNavigationWebpageSelf(request.url.toString())
                    findNavController().navigate(action)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, request.url)
                    startActivity(intent)
                }

                return true
            }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 RadioJHero/1.0 (Android)"

        webView.loadUrl(args.url)
    }

    private fun createMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.webpage_menu, menu)
    }

    private fun selectMenu(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_share) {
            return false
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, binding?.webpageWebview?.url ?: "")
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
        return true
    }

    private fun updatePaddingBottom() {
        val webView = binding?.webpageWebview ?: return
        val paddingBottom = webView.paddingBottom / resources.displayMetrics.density
        webView.loadUrl("javascript:document.documentElement.style.setProperty('--android-extra-bottom-padding', '${paddingBottom}px')")
    }
}