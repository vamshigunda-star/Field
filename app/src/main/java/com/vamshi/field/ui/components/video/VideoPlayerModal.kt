package com.vamshi.field.ui.components.video

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private enum class VideoLoadState { LOADING, READY, ERROR }

/** Offline-first field use: give slow connections a fair chance before declaring failure. */
private const val PLAYER_READY_TIMEOUT_MS = 15_000L

private val YOUTUBE_ID_FORMAT = Regex("^[a-zA-Z0-9_-]{11}$")

/**
 * Called from the WebView's JS thread — every callback re-posts to main before touching
 * Compose state.
 */
private class YouTubePlayerBridge(
    private val onReady: () -> Unit,
    private val onError: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post(onReady)
    }

    @JavascriptInterface
    fun onPlayerError(code: Int) {
        // 2=bad id, 5=HTML5 error, 100=not found, 101/150=embedding disabled
        mainHandler.post(onError)
    }
}

// The page is served with loadDataWithBaseURL(origin, ...) where origin is a synthetic
// https://<packageName> URL — the base URL is load-bearing: YouTube's embed player refuses
// playback (error 152/153) when it can't attribute the embedding page to an allowed origin,
// which is what happens both with loadUrl() on a bare /embed/ URL and with a spoofed
// youtube.com origin. The same origin must be repeated in the player's `origin` var.
// (Same scheme the android-youtube-player library uses.)
private fun playerHtml(youtubeId: String, origin: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
      <style>
        html, body { margin: 0; padding: 0; background: #000; height: 100%; overflow: hidden; }
        #player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
      </style>
    </head>
    <body>
      <div id="player"></div>
      <script src="https://www.youtube.com/iframe_api"></script>
      <script>
        function onYouTubeIframeAPIReady() {
          new YT.Player('player', {
            width: '100%',
            height: '100%',
            videoId: '$youtubeId',
            playerVars: { autoplay: 1, playsinline: 1, rel: 0, fs: 1, enablejsapi: 1, origin: '$origin' },
            events: {
              onReady: function(e) { FieldNative.onPlayerReady(); e.target.playVideo(); },
              onError: function(e) { FieldNative.onPlayerError(e.data); }
            }
          });
        }
      </script>
    </body>
    </html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayerModal(
    youtubeId: String,
    onDismiss: () -> Unit,
    videoTitle: String? = null,
) {
    // IDs are validated at seed time, but this string is interpolated into HTML/JS —
    // never render a page from an id that doesn't match the strict format.
    val isValidId = remember(youtubeId) { YOUTUBE_ID_FORMAT.matches(youtubeId) }

    var loadState by remember { mutableStateOf(if (isValidId) VideoLoadState.LOADING else VideoLoadState.ERROR) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val isFullscreen = fullscreenView != null

    val exitFullscreen = { fullscreenCallback?.onCustomViewHidden() ?: Unit }

    Dialog(
        onDismissRequest = { if (isFullscreen) exitFullscreen() else onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
        )
    ) {
        BackHandler {
            if (isFullscreen) exitFullscreen() else onDismiss()
        }

        DisposableEffect(Unit) {
            onDispose {
                webViewRef?.let { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.loadUrl("about:blank")
                    webView.destroy()
                }
            }
        }

        LaunchedEffect(Unit) {
            delay(PLAYER_READY_TIMEOUT_MS)
            if (loadState == VideoLoadState.LOADING) {
                loadState = VideoLoadState.ERROR
            }
        }

        Column(
            modifier = (if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .background(Color.Black)
        ) {
            // Close sits in its own strip above the player. Overlaid on the video it landed on
            // YouTube's own top-right controls (share, watch later), so a tap was ambiguous.
            if (!isFullscreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close ${videoTitle ?: "video"}",
                            tint = Color.White
                        )
                    }
                }
            }
            Box(
                modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (isValidId) {
                    AndroidView(
                        modifier = if (isFullscreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        },
                        factory = { context ->
                            FrameLayout(context).apply {
                                val webView = WebView(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    addJavascriptInterface(
                                        YouTubePlayerBridge(
                                            onReady = {
                                                if (loadState != VideoLoadState.ERROR) {
                                                    loadState = VideoLoadState.READY
                                                }
                                            },
                                            onError = { loadState = VideoLoadState.ERROR },
                                        ),
                                        "FieldNative"
                                    )
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                            if (view == null) {
                                                callback?.onCustomViewHidden()
                                                return
                                            }
                                            // Only one custom view at a time per WebView contract
                                            fullscreenCallback?.onCustomViewHidden()
                                            fullscreenView = view
                                            fullscreenCallback = callback
                                            addView(
                                                view,
                                                ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                )
                                            )
                                        }

                                        override fun onHideCustomView() {
                                            fullscreenView?.let { removeView(it) }
                                            fullscreenView = null
                                            fullscreenCallback = null
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        // The player lives in iframes; any main-frame navigation is
                                        // an escape hatch (YouTube logo, "Watch on YouTube") — hand
                                        // it to the system instead of hijacking the modal.
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url ?: return true
                                            if (request.isForMainFrame) {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
                                                } catch (_: Exception) {
                                                    // No browser/YouTube app — swallow, stay in modal
                                                }
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                loadState = VideoLoadState.ERROR
                                            }
                                        }
                                    }
                                    val origin = "https://${context.packageName}"
                                    loadDataWithBaseURL(
                                        origin,
                                        playerHtml(youtubeId, origin),
                                        "text/html",
                                        "utf-8",
                                        null
                                    )
                                }
                                webViewRef = webView
                                addView(webView)
                            }
                        }
                    )
                }

                if (loadState == VideoLoadState.LOADING) {
                    CircularProgressIndicator(color = Color.White)
                }

                if (loadState == VideoLoadState.ERROR) {
                    Text(
                        text = "Video unavailable. Check your connection and try again.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}
