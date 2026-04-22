package com.galiltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : AppCompatActivity() {
    
    lateinit var webView: WebView
    
    private lateinit var adView: AdView
    private var interstitialAd: InterstitialAd? = null
    private var backPressedTime: Long = 0
    
    private val HTML_URL = "https://sbatanapoli-blip.github.io/galil-tv-web/"
    private val BANNER_AD_UNIT_ID = "ca-app-pub-2734159647347391/5301928179"
    private val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2734159647347391/1182706455"

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
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            
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
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // ✅ تحميل الإعلانات بعد اكتمال تحميل الصفحة
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadRewardedAdViaJS()
                    }, 3000)
                }
            }
            
            webChromeClient = WebChromeClient()
            addJavascriptInterface(WebAppInterface(this@MainActivity, this@MainActivity), "Android")
            loadUrl(HTML_URL)
        }
        
        setupBannerAd()
        
        val rootLayout = FrameLayout(this)
        rootLayout.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootLayout.addView(adView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
        })
        
        setContentView(rootLayout)
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
                val webUrl: String
                if (url.startsWith("tg://")) {
                    val username = url.substringAfter("domain=").substringBefore("&")
                    webUrl = "https://t.me/$username"
                } else {
                    webUrl = url.replace("http://", "https://")
                }
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                startActivity(webIntent)
            } catch (e2: Exception) {
                // Do nothing
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // ✅ إعادة تحميل Banner Ad عند العودة للتطبيق
        if (::adView.isInitialized) {
            adView.loadAd(AdRequest.Builder().build())
        }
        
        // ✅ إعادة تحميل Interstitial Ad
        if (interstitialAd == null) {
            loadInterstitialAd()
        }
        
        // ✅ إعادة تحميل Rewarded Ad
        loadRewardedAdViaJS()
    }
    
    override fun onPause() {
        super.onPause()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            webView.evaluateJavascript(
                "(function() { return !document.getElementById('channelsPage').classList.contains('hidden'); })();",
                { value ->
                    if (value == "true") {
                        webView.evaluateJavascript("window.goBack();", null)
                    } else {
                        handleDoubleTapExit()
                    }
                }
            )
        }
    }
    
    private fun handleDoubleTapExit() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()
            backPressedTime = System.currentTimeMillis()
        }
    }
    
    override fun onDestroy() {
        if (::adView.isInitialized) {
            adView.destroy()
        }
        webView.destroy()
        super.onDestroy()
    }
}
