package com.galiltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    private val HTML_URL = "https://sbatanapoli-blip.github.io/galil-tv-web/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                
                if (url.startsWith("intent://")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        try {
                            val cleanUrl = url.substringAfter("url=").substringBefore("#")
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
                        } catch (e2: Exception) {}
                    }
                    return true
                }
                
                if (url.contains(".ts") || url.contains(".m3u8") || url.contains("video")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(Uri.parse(url), "video/*")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {}
                }
                
                return false
            }
        }
        
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(HTML_URL)
        setContentView(webView)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
