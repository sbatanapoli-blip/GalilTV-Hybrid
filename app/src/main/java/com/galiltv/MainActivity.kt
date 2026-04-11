package com.galiltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    // ✅ رابط HTML على GitHub Pages
    private val HTML_URL = "https://sbatanapoli-blip.github.io/galil-tv-web/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setAppCacheEnabled(true)
            
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        loadErrorPage()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                                        if (url.startsWith("intent://")) {
                        try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            val cleanUrl = url.substringAfter("url=").substringBefore("#")
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
                            } catch (e2: Exception) {}
                        }
                        return true
                    }
                    
                    if (url.contains(".ts") || url.contains(".m3u8") || url.contains("video")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(url), "video/*")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {}
                    }
                    
                    return false
                }
            }
            
            webChromeClient = WebChromeClient()
        }

        setContentView(webView)
        loadHtmlWithCheck()
    }

    private fun loadHtmlWithCheck() {
        if (isOnline()) {
            webView.loadUrl(HTML_URL)
        } else {
            loadErrorPage()
        }
    }

    private fun loadErrorPage() {
        val errorHtml = """
            <html>
            <body style="background:#05060a;color:#fff;font-family:sans-serif;text-align:center;padding:40px;">
                <h2>⚠️ لا يوجد اتصال بالإنترنت</h2>
                <p>يرجى تفعيل البيانات أو الواي فاي ثم إعادة تحميل التطبيق.</p>                <button onclick="location.reload()" style="padding:12px 24px;background:#f59e0b;border:none;border-radius:8px;color:#000;font-weight:bold;margin-top:20px;">إعادة المحاولة</button>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
