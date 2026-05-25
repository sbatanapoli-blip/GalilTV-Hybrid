package com.galiltv

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    
    lateinit var webView: WebView
    
    private lateinit var adView: AdView
    private var interstitialAd: InterstitialAd? = null
    private var backPressedTime: Long = 0
    private var isOnline = true
    
    private val HTML_URL = "https://sbatanapoli-blip.github.io/Styles/"
    private val BANNER_AD_UNIT_ID = "ca-app-pub-2734159647347391/2177759745"
    private val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2734159647347391/8828448482"
    
    // 🔐 Service Token من Cloudflare Access
    companion object {
        private const val CLIENT_ID = "23c8af7197f1e760d72f5a151079a3e1.access"
        private const val CLIENT_SECRET = "f8563206529036ecbc13637f9155df01f1dc36d52400194650d8bc993afea4b9"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MobileAds.initialize(this) {}
        loadInterstitialAd()
        
        // 1️⃣ إعداد WebView وتكوينه
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // تفعيل التصحيح عن بُعد
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            
            webViewClient = object : WebViewClient() {
                
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    
                    if (url.startsWith("tg://") || url.contains("t.me/")) {
                        openTelegram(url)
                        return true
                    }
                    
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
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(Uri.parse(url), "video/*")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {}
                    }
                    
                    return false
                }
                
                // معالجة الأخطاء (أندرويد 6.0 فأحدث)
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (error?.errorCode == ERROR_HOST_LOOKUP || 
                            error?.errorCode == ERROR_CONNECT || 
                            error?.errorCode == ERROR_TIMEOUT) {
                            showOfflinePage()
                        }
                    }
                }
                
                // معالجة الأخطاء (أندرويد قديم)
                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    if (errorCode == ERROR_HOST_LOOKUP || 
                        errorCode == ERROR_CONNECT || 
                        errorCode == ERROR_TIMEOUT) {
                        showOfflinePage()
                    }
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadRewardedAdViaJS()
                    }, 3000)
                }
            }
            
            webChromeClient = WebChromeClient()
            
            // ✅ JavaScript Interface الموجود مسبقاً
            addJavascriptInterface(WebAppInterface(this@MainActivity, this@MainActivity), "Android")
            
            // ✅ JavaScript Interface الجديد لجلب البيانات مع Service Token
            addJavascriptInterface(object {
                @JavascriptInterface
                fun fetch(url: String): String {
                    return this@MainActivity.fetchWithAuth(url)
                }
            }, "NativeBridge")
        }
        
        // 2️⃣ إعداد واجهة المستخدم - الطريقة الصحيحة للـ Banner
setupBannerAd()

// استخدام RelativeLayout لضمان وجود الإعلان في الأسفل
val rootLayout = RelativeLayout(this)

// إعدادات الـ WebView
webView.layoutParams = RelativeLayout.LayoutParams(
    RelativeLayout.LayoutParams.MATCH_PARENT,
    RelativeLayout.LayoutParams.MATCH_PARENT
)

// إعدادات الـ Banner - وضعه في الأسفل
val bannerParams = RelativeLayout.LayoutParams(
    RelativeLayout.LayoutParams.MATCH_PARENT,
    RelativeLayout.LayoutParams.WRAP_CONTENT
)
bannerParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)

// إضافة الـ WebView أولاً، ثم إضافة الإعلان فوقه
rootLayout.addView(webView)
rootLayout.addView(adView, bannerParams)

setContentView(rootLayout)
        
        // 3️⃣ التحقق من الاتصال وتحميل المحتوى
        if (!isNetworkAvailable()) {
            showOfflinePage()
        } else {
            webView.loadUrl(HTML_URL)
        }
    }
    
    // 🔥 دالة جلب البيانات مع Service Token (تستدعيها JavaScript)
    @JavascriptInterface
    fun fetchWithAuth(url: String): String {
        return try {
            Log.d("GalilTV", "🔵 [DEBUG] Fetching: $url")
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("CF-Access-Client-Id", CLIENT_ID)
            connection.setRequestProperty("CF-Access-Client-Secret", CLIENT_SECRET)
            connection.setRequestProperty("User-Agent", "GalilTV-Android-App")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val responseCode = connection.responseCode
            Log.d("GalilTV", "🔵 [DEBUG] Response code: $responseCode")
            
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                Log.d("GalilTV", "🔵 [DEBUG] Response length: ${response.length}")
                response
            } else {
                val errorStream = connection.errorStream
                val reader = BufferedReader(InputStreamReader(errorStream))
                val errorResponse = reader.readText()
                Log.e("GalilTV", "🔴 [DEBUG] HTTP Error $responseCode: $errorResponse")
                "{\"error\": \"HTTP $responseCode\", \"details\": \"$errorResponse\"}"
            }
        } catch (e: Exception) {
            Log.e("GalilTV", "🔴 [DEBUG] Exception: ${e.message}")
            "{\"error\": \"${e.message}\"}"
        }
    }
    
    // ✅ دالة عرض صفحة Offline الاحترافية
    private fun showOfflinePage() {
        isOnline = false
        val offlineHTML = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>لا يوجد اتصال</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
                        height: 100vh;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        color: white;
                        padding: 20px;
                        text-align: center;
                    }
                    .container { max-width: 360px; }
                    .icon { font-size: 100px; margin-bottom: 24px; animation: float 3s ease-in-out infinite; }
                    @keyframes float {
                        0%, 100% { transform: translateY(0); }
                        50% { transform: translateY(-10px); }
                    }
                    .wifi-circle {
                        width: 140px; height: 140px; margin: 0 auto 24px;
                        background: rgba(239, 68, 68, 0.15); border: 2px solid rgba(239, 68, 68, 0.3);
                        border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 60px;
                    }
                    h1 { font-size: 24px; margin-bottom: 12px; font-weight: 700; color: #fff; }
                    p { font-size: 15px; margin-bottom: 28px; color: rgba(255,255,255,0.75); line-height: 1.6; }
                    .retry-btn {
                        background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%); color: #000; border: none;
                        padding: 14px 36px; border-radius: 50px; font-size: 15px; font-weight: 700; cursor: pointer;
                        transition: all 0.2s ease; box-shadow: 0 4px 14px rgba(245, 158, 11, 0.35); width: 100%; max-width: 280px;                    }
                    .retry-btn:active { transform: scale(0.98); box-shadow: 0 2px 8px rgba(245, 158, 11, 0.25); }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="wifi-circle">📡</div>
                    <h1>لا يوجد اتصال بالإنترنت</h1>
                    <p>يبدو أن جهازك غير متصل بالشبكة. يرجى التحقق من الإعدادات والمحاولة مرة أخرى.</p>
                    <button class="retry-btn" onclick="window.location.reload()">🔄 إعادة المحاولة</button>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL(null, offlineHTML, "text/html", "UTF-8", null)
    }
    
    // ✅ دالة التحقق من الاتصال بالإنترنت
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    // ✅ مراقبة تغيرات الاتصال (متوافق مع جميع الإصدارات)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                if (!isOnline) {
                    isOnline = true
                    Log.d("GalilTV", "🌐 Internet restored, reloading...")
                    webView.loadUrl(HTML_URL)
                    Toast.makeText(this@MainActivity, "✅ تم استعادة الاتصال", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        override fun onLost(network: Network) {
            runOnUiThread {
                if (isOnline) {
                    isOnline = false
                    Log.d("GalilTV", "🔌 Internet lost")
                    showOfflinePage()
                    Toast.makeText(this@MainActivity, "⚠️ انقطع الاتصال", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupBannerAd() {
        adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }
    
    private fun loadInterstitialAd() {
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("GalilTV", "✅ Interstitial ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e("GalilTV", "❌ Interstitial ad failed: ${error.message}")
                }
            }
        )
    }
    
    private fun loadRewardedAdViaJS() {
        webView.evaluateJavascript(
            "if (window.RewardedAds && window.RewardedAds.preloadAd) window.RewardedAds.preloadAd();",
            null
        )
    }
    
    fun showInterstitialAd() {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    loadInterstitialAd()
                }
            }
            interstitialAd?.show(this)
        } else {
            loadInterstitialAd()
        }
    }
    
    private fun openTelegram(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val webUrl = if (url.startsWith("tg://")) {
                    "https://t.me/${url.substringAfter("domain=").substringBefore("&")}"
                } else {
                    url.replace("http://", "https://")
                }
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            } catch (e2: Exception) {}
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // تسجيل مراقبة الاتصال
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        } catch (e: Exception) {
            Log.e("GalilTV", "❌ Network callback error: ${e.message}")
        }
        
        if (::adView.isInitialized) adView.loadAd(AdRequest.Builder().build())
        if (interstitialAd == null) loadInterstitialAd()
        loadRewardedAdViaJS()
    }
    
    override fun onPause() {
        super.onPause()
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {}
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            webView.evaluateJavascript(
                "(function() { return !document.getElementById('channelsPage').classList.contains('hidden'); })();",
                { value ->
                    if (value == "true") webView.evaluateJavascript("window.goBack();", null)
                    else handleDoubleTapExit()
                }
            )
        }
    }
    
    private fun handleDoubleTapExit() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show()
            backPressedTime = System.currentTimeMillis()
        }
    }
    
    override fun onDestroy() {
        if (::adView.isInitialized) adView.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
