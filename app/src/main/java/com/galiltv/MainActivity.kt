package com.galiltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : AppCompatActivity() {
    
    // ✅ مهم: lateinit var بدون private
    lateinit var webView: WebView
    
    private lateinit var adView: AdView
    private var interstitialAd: InterstitialAd? = null
    
    // ✅ متغير لتتبع وقت الضغط على زر الرجوع (لخاصية الخروج المزدوج)
    private var backPressedTime: Long = 0
    
    private val HTML_URL = "https://sbatanapoli-blip.github.io/galil-tv-web/"
    private val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MobileAds.initialize(this) {}
        loadInterstitialAd()
        
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    if (url.startsWith("tg://") || url.contains("t.me/")) { openTelegram(url); return true }
                    if (url.startsWith("intent://")) {
                        try {
                            startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME))                            return true
                        } catch (e: Exception) {
                            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.substringAfter("url=").substringBefore("#")))) } catch (e2: Exception) {}
                        }
                        return true
                    }
                    if (url.contains(".ts") || url.contains(".m3u8") || url.contains("video")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), "video/*"); flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {}
                    }
                    return false
                }
            }
            
            webChromeClient = WebChromeClient()
            addJavascriptInterface(WebAppInterface(this@MainActivity, this@MainActivity), "Android")
            loadUrl(HTML_URL)
        }
        
        setupBannerAd()
        
        val rootLayout = FrameLayout(this)
        rootLayout.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(adView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.BOTTOM })
        
        setContentView(rootLayout)
    }
    
    private fun setupBannerAd() {
        adView = AdView(this).apply { setAdSize(AdSize.BANNER); adUnitId = BANNER_AD_UNIT_ID; loadAd(AdRequest.Builder().build()) }
    }
    
    private fun loadInterstitialAd() {
        InterstitialAd.load(this, INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
        })
    }
    
    fun showInterstitialAd() {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { interstitialAd = null; loadInterstitialAd() }
            }
            interstitialAd?.show(this)
        } else { loadInterstitialAd() }
    }    
    private fun openTelegram(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val webUrl = if (url.startsWith("tg://")) "https://t.me/" + url.substringAfter("domain=").substringBefore("&") else url.replace("http://", "https://")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            } catch (e2: Exception) {}
        }
    }

    // ============================================
    // ✅ هنا التعديل المهم لزر الرجوع والخروج المزدوج
    // ============================================
    override fun onBackPressed() {
        // 1. إذا كان هناك سجل تصفح حقيقي داخل الويب، ارجع خطوة
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            // 2. إذا لم يكن هناك سجل، نتحقق من حالة الصفحة الحالية في HTML
            // نرسل كود جافاسكريبت ليعودنا بـ true إذا كنا في صفحة القنوات
            webView.evaluateJavascript(
                "(function() { return !document.getElementById('channelsPage').classList.contains('hidden'); })();",
                { value ->
                    // إذا كانت النتيجة true (أي أننا في صفحة القنوات)
                    if (value == "true") {
                        // نستدعي دالة الرجوع الموجودة في index.html
                        webView.evaluateJavascript("window.goBack();", null)
                    } else {
                        // إذا كنا في الصفحة الرئيسية، نطبق منطق الخروج
                        handleDoubleTapExit()
                    }
                }
            )
        }
    }

    // دالة التحكم بالخروج عند الضغط مرتين
    private fun handleDoubleTapExit() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            // إذا تم الضغط خلال ثانيتين، اخرج من التطبيق
            super.onBackPressed()
        } else {
            // وإلا أظهر رسالة التنبيه واحفظ وقت الضغط
            Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()
            backPressedTime = System.currentTimeMillis()
        }
    }    
    override fun onDestroy() {
        adView.destroy()
        super.onDestroy()
    }
}
