package com.galiltv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    // ✅ رابط HTML على السيرفر (غيّره برابطك الفعلي)
    private val HTML_URL = "https://sbatanapoli-blip.github.io/galil-tv-web/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            
            // ✅ تفعيل الـ Cache لتسريع التحميل والعمل عند انقطاع النت
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setAppCacheEnabled(true)
            settings.setAppCachePath(cacheDir.path)

            // ✅ معالجة الأخطاء وروابط intent://
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?, 
                    request: WebResourceRequest?, 
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        // ⚠️ عرض رسالة عند فقدان الاتصال أو فشل التحميل
                        loadErrorPage()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    
                    if (url.startsWith("intent://")) {
                        try {                            val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            val cleanUrl = url.substringAfter("url=").substringBefore("#")
                            try {
                                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cleanUrl)))
                            } catch (e2: Exception) {}
                        }
                        return true
                    }
                    
                    if (url.contains(".ts") || url.contains(".m3u8") || url.contains("video")) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(url), "video/*")
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {}
                    }
                    
                    return false
                }
            }
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
                <p>يرجى تفعيل البيانات أو الواي فاي ثم إعادة تحميل التطبيق.</p>
                <button onclick="location.reload()" style="padding:12px 24px;background:#f59e0b;border:none;border-radius:8px;color:#000;font-weight:bold;margin-top:20px;">إعادة المحاولة</button>
            </body>
            </html>
        """.trimIndent()        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
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
