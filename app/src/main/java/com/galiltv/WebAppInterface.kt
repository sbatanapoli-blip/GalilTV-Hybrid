package com.galiltv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class WebAppInterface(
    private val context: Context,
    private val activity: MainActivity
) {
    // ✅ متغير إعلان المكافأة
    private var rewardedAd: RewardedAd? = null
    
    // ✅ معرف إعلان المكافأة (تأكد من أنه من نوع Rewarded في AdMob)
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-2734159647347391/2152173198"
    
    // ✅ متغير لتتبع منح المكافأة (لحل مشكلة إغلاق الإعلان مبكراً)
    private var rewardGranted = false

    // ============================================
    // 🎬 دالة تشغيل الفيديو
    // ============================================
    @JavascriptInterface
    fun playVideo(url: String) {
        val players = listOf(
            "com.vexo.player",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.brouken.player",
            "com.archos.mediacenter.videofree"
        )

        var launched = false

        for (playerPackage in players) {
            if (isAppInstalled(playerPackage)) {
                try {                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), "video/*")
                        setPackage(playerPackage)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("title", "Galil TV")
                    }
                    context.startActivity(intent)
                    launched = true
                    break
                } catch (e: Exception) {
                    continue
                }
            }
        }

        if (!launched) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "No video player found. Install VLC or Vexo.", Toast.LENGTH_LONG).show()
            }
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc")))
            } catch (e: Exception) {}
        }
    }

    // ============================================
    // 🔔 دالة عرض الرسائل
    // ============================================
    @JavascriptInterface
    fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // 🔲 دالة الإعلان البيني
    // ============================================
    @JavascriptInterface
    fun showInterstitialAd() {
        Handler(Looper.getMainLooper()).post {
            activity.showInterstitialAd()
        }
    }

    // ============================================
    // 🎁 دوال إعلان المكافأة (Rewarded Ads) - ✅ مُصححة بالكامل
    // ============================================
    
    // 1️⃣ تحميل الإعلان مسبقاً    @JavascriptInterface
    fun loadRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            Log.d("GalilTV", "🔄 Loading rewarded ad...")
            
            // تجنب التحميل المتكرر إذا كان الإعلان موجوداً بالفعل
            if (rewardedAd != null) {
                Log.d("GalilTV", "✅ Rewarded ad already loaded")
                notifyWeb("onAdLoaded")
                return@post
            }
            
            RewardedAd.load(
                context,
                REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        Log.d("GalilTV", "✅ Rewarded ad loaded successfully")
                        notifyWeb("onAdLoaded")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        Log.e("GalilTV", "❌ Rewarded ad failed to load: ${error.message}")
                        notifyWeb("onAdFailed")
                        
                        // ✅ محاولة إعادة التحميل بعد 30 ثانية
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadRewardedAd()
                        }, 30000)
                    }
                }
            )
        }
    }
    
    // 2️⃣ عرض الإعلان - ✅ النسخة المُصححة بالكامل
    @JavascriptInterface
fun showRewardedAd() {
    Handler(Looper.getMainLooper()).post {
        Log.d("GalilTV", "🎬 showRewardedAd() called")
        rewardGranted = false
        
        if (rewardedAd != null) {
            Log.d("GalilTV", "✅ rewardedAd is not null")
            
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("GalilTV", "📺 onAdDismissedFullScreenContent()")
                    Log.d("GalilTV", "💰 rewardGranted = $rewardGranted")
                    rewardedAd = null
                    
                    if (!rewardGranted) {
                        Log.d("GalilTV", "⚠️ Ad closed without reward")
                        notifyWeb("onAdNotAvailable")
                    }
                    
                    loadRewardedAd()
                }
                
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e("GalilTV", "❌ onAdFailedToShow: ${error.message}")
                    rewardedAd = null
                    rewardGranted = false
                    notifyWeb("onAdNotAvailable")
                    loadRewardedAd()
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d("GalilTV", "👀 onAdShowedFullScreenContent")
                }
            }
            
            Log.d("GalilTV", "🎬 Showing ad...")
            rewardedAd?.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d("GalilTV", "💰 Reward granted: $rewardAmount $rewardType")
                
                rewardGranted = true
                
                // ✅ أظهر Toast
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "✅ Reward Granted! Opening...", Toast.LENGTH_SHORT).show()
                }
                
                // ✅ استخدم postDelayed أطول (2 ثانية)
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d("GalilTV", "📡 Calling notifyWeb...")
                    
                    // ✅ نفذ الكود مباشرة في WebView
                    activity.webView.post {
                        val jsCode = """
                            (function() {
                                console.log('🎁 Executing onAdRewarded...');
                                if (window.RewardedAds && typeof window.RewardedAds.onAdRewarded === 'function') {
                                    try {
                                        window.RewardedAds.onAdRewarded();
                                        console.log('✅ Success!');
                                    } catch (e) {
                                        console.error('❌ Error:', e);
                                    }
                                } else {
                                    console.error('❌ onAdRewarded not found!');
                                }
                            })();
                        """.trimIndent()
                        
                        Log.d("GalilTV-JS", "📝 Executing: $jsCode")
                        activity.webView.evaluateJavascript(jsCode) { result ->
                            Log.d("GalilTV-JS", "✅ Result: $result")
                        }
                    }
                }, 2000)  // ✅ انتظر ثانيتين
                
                loadRewardedAd()
            }
        } else {
            Log.e("GalilTV", "❌ rewardedAd is null!")
            notifyWeb("onAdNotAvailable")
            loadRewardedAd()
        }
    }
}
    
    // 3️⃣ دالة لإعادة تحميل الإعلان (للحالات الطارئة)
    @JavascriptInterface
    fun refreshRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            Log.d("GalilTV", "🔄 Refreshing rewarded ad...")
            rewardedAd = null
            rewardGranted = false
            loadRewardedAd()
        }
    }
    
    // 4️⃣ دالة للتحقق من جاهزية الإعلان
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        val isReady = rewardedAd != null
        Log.d("GalilTV", "🔍 Rewarded ad ready: $isReady")
        return isReady
    }
    
    // ============================================
    // 📡 دوال إرسال الأحداث للويب - ✅ محسّنة باستخدام loadUrl + evaluateJavascript
    // ============================================
    
    private fun notifyWeb(eventName: String) {
        Log.d("GalilTV-JS", "📡 notifyWeb called: $eventName")
        
        activity.runOnUiThread {
            val jsCode = when (eventName) {
                "onAdRewarded" -> "window.RewardedAds && window.RewardedAds.onAdRewarded && window.RewardedAds.onAdRewarded();"
                "onAdLoaded" -> "window.RewardedAds && window.RewardedAds.onAdLoaded && window.RewardedAds.onAdLoaded();"
                "onAdFailed" -> "window.RewardedAds && window.RewardedAds.onAdFailed && window.RewardedAds.onAdFailed();"
                "onAdNotAvailable" -> "window.RewardedAds && window.RewardedAds.onAdNotAvailable && window.RewardedAds.onAdNotAvailable();"
                else -> ""
            }
            
            if (jsCode.isNotEmpty()) {                // ✅ استخدم loadUrl أولاً (أكثر موثوقية)
                Log.d("GalilTV-JS", "📝 Executing via loadUrl: $eventName")
                activity.webView.loadUrl("javascript:$jsCode")
                
                // ✅ أيضاً جرب evaluateJavascript كاحتياط
                activity.webView.evaluateJavascript(jsCode) { result ->
                    Log.d("GalilTV-JS", "✅ evaluateJavascript result: $result")
                }
            }
        }
    }

    // ============================================
    // 🔍 دالة التحقق من تثبيت التطبيقات
    // ============================================
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
