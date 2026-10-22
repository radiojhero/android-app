package com.radiojhero.app.ui.webpage

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewClientCompat
import com.radiojhero.app.R
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.ui.components.AppTopBar

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPageScreen(
    url: String,
    onNavigateUp: () -> Unit,
    onOpenWebPage: (url: String) -> Unit,
) {
    val context = LocalContext.current
    val userAgent = "Mozilla/5.0 RadioJHero/${stringResource(R.string.version_name)} (Android)"
    val openWebPage by rememberUpdatedState(onOpenWebPage)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var hasStarted by remember { mutableStateOf(false) }
    var scrolled by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            scrolled = scrolled,
            onNavigateUp = onNavigateUp,
            actions = {
                IconButton(onClick = { share(context, webView?.url ?: url) }) {
                    Icon(
                        painterResource(R.drawable.ic_baseline_share_24),
                        contentDescription = stringResource(R.string.action_share),
                    )
                }
            },
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        var hasLoaded = false

                        webViewClient = object : WebViewClientCompat() {
                            override fun onPageStarted(webView: WebView, url: String, favicon: Bitmap?) {
                                super.onPageStarted(webView, url, favicon)
                                hasStarted = true
                            }

                            override fun onPageFinished(webView: WebView, url: String) {
                                super.onPageFinished(webView, url)
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
                                    openWebPage(request.url.toString())
                                } else {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                    } catch (error: ActivityNotFoundException) {
                                        println(error)
                                    }
                                }

                                return true
                            }
                        }

                        settings.javaScriptEnabled = true
                        settings.userAgentString = userAgent
                        setOnScrollChangeListener { _, _, y, _, _ ->
                            scrolled = y > 0
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (hasStarted) 1f else 0f),
                onRelease = { it.destroy() },
            )

            if (!hasStarted) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun share(context: Context, url: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, url)
        type = "text/plain"
    }

    context.startActivity(Intent.createChooser(sendIntent, null))
}
