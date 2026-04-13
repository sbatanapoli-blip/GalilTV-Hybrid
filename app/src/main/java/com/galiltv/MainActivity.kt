package com.galiltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : AppCompatActivity() {
    
    // ✅ مهم: lateinit var بدون private ليكون متاحاً لـ WebAppInterface
    lateinit var webView: WebView
    
    private lateinit var adView: AdView
    private var interstitialAd: InterstitialAd? = null
    
    private val HTML_URL = "https://sbatanapoli-blip.github.io/galil-tv-web/"
    private val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ تهيئة AdMob
        MobileAds.initialize(this) {}
        loadInterstitialAd()
        
        // ✅ إعداد WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            
            // ✅ معالجة الروابط
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    
                    // دعم تيليجرام
                    if (url.startsWith("tg://") || url.contains("t.me/")) {
                        openTelegram(url)
                        return true
                    }
                                        // دعم intent:// (مثل Vexo)
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
                    
                    // دعم روابط الفيديو
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
            
            webChromeClient = WebChromeClient()
            
            // ✅ ربط الجسر بين الويب والأندرويد
            addJavascriptInterface(WebAppInterface(this@MainActivity, this@MainActivity), "Android")
            
            loadUrl(HTML_URL)
        }
        
        // ✅ إعداد البانر
        setupBannerAd()
        
        // ✅ تخطيط العرض
        val rootLayout = FrameLayout(this)
        rootLayout.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootLayout.addView(adView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT        ).apply {
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
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }
    
    // ✅ دالة عرض الإعلان البيني (يستدعيها WebAppInterface)
    fun showInterstitialAd() {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
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
            val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            tgIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK            this.startActivity(tgIntent)
        } catch (e: Exception) {
            try {
                val webUrl = if (url.startsWith("tg://")) {
                    val username = url.substringAfter("domain=").substringBefore("&")
                    "https://t.me/$username"
                } else {
                    url.replace("http://", "https://")
                }
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                this.startActivity(webIntent)
            } catch (e2: Exception) {
                // Do nothing
            }
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        adView.destroy()
        super.onDestroy()
    }
}
