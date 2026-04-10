package com.galiltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // ✅ اعتراض جميع الروابط وفتحها بالمشغلات
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null) {
                        // ✅ إذا كان رابط فيديو (.ts, .m3u8, .mp4)
                        if (url.contains(".ts") || url.contains(".m3u8") || url.contains("video")) {
                            openWithPlayer(url)
                            return true
                        }
                        
                        // ✅ إذا كان رابط intent://
                        if (url.startsWith("intent://")) {
                            try {
                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                // جرّب فتح VLC مباشرة
                                openWithPlayer(url.replace("intent://", "http://").split("#")[0])
                                return true
                            }
                        }
                    }
                    return false
                }
            }
            
            loadUrl("file:///android_asset/index.html")
        }
        
        setContentView(webView)
    }

    private fun openWithPlayer(url: String) {
        val players = listOf(
            "com.vexo.player",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.brouken.player"
        )

        for (player in players) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    setPackage(player)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                return // ✅ خرج عند النجاح
            } catch (e: Exception) {
                continue
            }
        }

        // ❌ إذا فشل الكل، افتح بأي مشغل متاح
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {}
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
