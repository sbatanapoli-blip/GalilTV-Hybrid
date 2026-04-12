package com.galiltv

import android.content.ActivityNotFoundException
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
    
    // ✅ معرفات الإعلانات (استخدم الاختبارية أثناء التطوير)
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // اختباري

    // ============================================
    // 🎬 دالة تشغيل الفيديو (نفس الكود الرائع)
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
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP                        putExtra("title", "Galil TV")
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
    // 🔲 دالة الإعلان البيني (Interstitial)
    // ============================================
    @JavascriptInterface
    fun showInterstitialAd() {
        Handler(Looper.getMainLooper()).post {
            activity.showInterstitialAd()
        }
    }

    // ============================================
    // 🎁 دوال إعلان المكافأة (Rewarded Ads)
    // ============================================
    
    // 1️⃣ تحميل الإعلان مسبقاً
    @JavascriptInterface
    fun loadRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            RewardedAd.load(                context,
                REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        notifyWeb("onAdLoaded")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        notifyWeb("onAdFailed")
                    }
                }
            )
        }
    }
    
    // 2️⃣ عرض الإعلان
    @JavascriptInterface
    fun showRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            if (rewardedAd != null) {
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadRewardedAd() // إعادة التحميل للعرض القادم
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        rewardedAd = null
                        notifyWeb("onAdNotAvailable")
                    }
                }
                
                rewardedAd?.show(activity) { rewardItem ->
                    // ✅ المستخدم شاهد الإعلان كاملاً → كافئه!
                    notifyWeb("onAdRewarded")
                    loadRewardedAd() // أعد التحميل
                }
            } else {
                notifyWeb("onAdNotAvailable")
            }
        }
    }
    
    // ============================================
    // 🔗 دالة مساعدة لإرسال الأحداث للويب
    // ============================================
    private fun notifyWeb(eventName: String) {        Handler(Looper.getMainLooper()).post {
            activity.webView.evaluateJavascript(
                "if (typeof RewardedAds !== 'undefined') RewardedAds.$eventName();",
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
