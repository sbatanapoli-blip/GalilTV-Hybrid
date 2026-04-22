package com.galiltv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
    
    // ✅ معرف إعلان المكافأة (اختباري)
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-2734159647347391/3988846505"

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
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
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
    // 🎁 دوال إعلان المكافأة (Rewarded Ads)
    // المعدلة لتتوافق مع كود JavaScript
    // ============================================
    
    // 1️⃣ تحميل الإعلان مسبقاً (بنفس الاسم الذي يطلبه JavaScript)
    @JavascriptInterface
    fun loadRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            RewardedAd.load(
                context,
                REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        // ✅ إعلام JavaScript بأن الإعلان جاهز
                        notifyWebAdLoaded()
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        // ✅ إعلام JavaScript بفشل التحميل
                        notifyWebAdFailed()
                    }
                }
            )
        }
    }
    
    // 2️⃣ عرض الإعلان (بنفس الاسم الذي يطلبه JavaScript)
    @JavascriptInterface
    fun showRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            if (rewardedAd != null) {
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        // ✅ تحميل إعلان جديد بعد الإغلاق
                        loadRewardedAd()
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        rewardedAd = null
                        // ✅ إعلام JavaScript بأن الإعلان غير متاح
                        notifyWebAdNotAvailable()
                    }
                }
                
                rewardedAd?.show(activity) { rewardItem ->
                    // ✅ مكافأة المستخدم (نمرر الـ rewardAmount و rewardType)
                    val rewardAmount = rewardItem.amount
                    val rewardType = rewardItem.type
                    notifyWebAdRewarded()
                    loadRewardedAd() // تحميل إعلان جديد
                }
            } else {
                // ✅ إذا لم يكن الإعلان جاهزاً، نعلم JavaScript
                notifyWebAdNotAvailable()
            }
        }
    }
    
    // ============================================
    // 📡 دوال إرسال الأحداث للويب (معدلة لتتوافق مع كود HTML)
    // ============================================
    
    private fun notifyWebAdLoaded() {
        Handler(Looper.getMainLooper()).post {
            // ✅ استدعاء الدالة المناسبة في JavaScript
            activity.webView.evaluateJavascript(
                "if (window.RewardedAds && window.RewardedAds.onAdLoaded) window.RewardedAds.onAdLoaded(); " +
                "else if (typeof RewardedAds !== 'undefined' && RewardedAds.preloadAd) RewardedAds.preloadAd(); " +
                "else if (window.Android && window.Android.onAdLoaded) window.Android.onAdLoaded();",
                null
            )
        }
    }
    
    private fun notifyWebAdFailed() {
        Handler(Looper.getMainLooper()).post {
            activity.webView.evaluateJavascript(
                "if (window.RewardedAds && window.RewardedAds.onAdFailed) window.RewardedAds.onAdFailed();",
                null
            )
        }
    }
    
    private fun notifyWebAdRewarded() {
        Handler(Looper.getMainLooper()).post {
            // ✅ استدعاء الدالة التي تمنح المكافأة في JavaScript
            activity.webView.evaluateJavascript(
                "if (window.RewardedAds && window.RewardedAds.onAdRewarded) window.RewardedAds.onAdRewarded(); " +
                "else if (typeof RewardedAds !== 'undefined' && RewardedAds.onAdRewarded) RewardedAds.onAdRewarded();",
                null
            )
        }
    }
    
    private fun notifyWebAdNotAvailable() {
        Handler(Looper.getMainLooper()).post {
            activity.webView.evaluateJavascript(
                "if (window.RewardedAds && window.RewardedAds.onAdNotAvailable) window.RewardedAds.onAdNotAvailable();",
                null
            )
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
