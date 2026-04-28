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
    // 🎁 دوال إعلان المكافأة (Rewarded Ads) - ✅ مُصححة
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
        Log.d("GalilTV", "🎬 showRewardedAd started")
        rewardGranted = false
        
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("GalilTV", "📺 Ad dismissed, rewardGranted=$rewardGranted")
                    rewardedAd = null
                    if (!rewardGranted) {
                        notifyWeb("onAdNotAvailable")
                    }
                    loadRewardedAd()
                }
                
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e("GalilTV", "❌ Ad failed: ${error.message}")
                    rewardedAd = null
                    rewardGranted = false
                    notifyWeb("onAdNotAvailable")
                    loadRewardedAd()
                }
            }
            
            // ✅ عرض الإعلان
            rewardedAd?.show(activity) { rewardItem ->
                Log.d("GalilTV", "💰 REWARD GRANTED!")
                rewardGranted = true
                
                // ✅ أغلق الـ Modal فوراً وأفتح الفئة
                Handler(Looper.getMainLooper()).post {
                    try {
                        // الطريقة 1: أغلق الـ Modal مباشرة
                        activity.webView.loadUrl("javascript:document.getElementById('premiumModal').classList.add('hidden')")
                        
                        // الطريقة 2: افتح الفئة
                        val jsCode = """
                            (function() {
                                console.log('🎁 Opening category...');
                                if (window.RewardedAds && typeof window.RewardedAds.onAdRewarded === 'function') {
                                    window.RewardedAds.onAdRewarded();
                                }
                                if (window.Channels && typeof window.Channels.load === 'function' && window.RewardedAds && window.RewardedAds.currentCategoryId) {
                                    window.Channels.load(window.RewardedAds.currentCategoryId, 'Premium');
                                }
                            })();
                        """.trimIndent()
                        
                        activity.webView.evaluateJavascript(jsCode, null)
                        
                        Toast.makeText(context, "✅ Opening category...", Toast.LENGTH_SHORT).show()
                        
                    } catch (e: Exception) {
                        Log.e("GalilTV", "❌ Error: ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                loadRewardedAd()
            }
        } else {
            Log.e("GalilTV", "❌ Ad not ready")
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
    // 📡 دوال إرسال الأحداث للويب - ✅ محسّنة
    // ============================================
    
    private fun notifyWeb(eventName: String) {
    activity.runOnUiThread {
        val jsCode = when (eventName) {
            "onAdRewarded" -> """
                console.log('📡 onAdRewarded called');
                if (window.RewardedAds && window.RewardedAds.onAdRewarded) {
                    window.RewardedAds.onAdRewarded();
                }
            """.trimIndent()
            "onAdLoaded" -> "if(window.RewardedAds&&window.RewardedAds.onAdLoaded){window.RewardedAds.onAdLoaded();}"
            "onAdFailed" -> "if(window.RewardedAds&&window.RewardedAds.onAdFailed){window.RewardedAds.onAdFailed();}"
            "onAdNotAvailable" -> "if(window.RewardedAds&&window.RewardedAds.onAdNotAvailable){window.RewardedAds.onAdNotAvailable();}"
            else -> ""
        }
        
        if (jsCode.isNotEmpty()) {
            activity.webView.loadUrl("javascript:$jsCode")
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
