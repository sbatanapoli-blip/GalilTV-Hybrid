package com.galiltv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
    
    private var rewardedAd: RewardedAd? = null
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-2734159647347391/2152173198"
    private var rewardGranted = false

    // ============================================
    // 🎬 دالة تشغيل الفيديو
    // ============================================
    @JavascriptInterface
    fun playVideo(url: String) {
        val players = listOf(
            "com.vexo.player", "org.videolan.vlc", "com.mxtech.videoplayer.ad",
            "com.brouken.player", "com.archos.mediacenter.videofree"
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
                } catch (e: Exception) { continue }            }
        }
        if (!launched) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "No video player found. Install VLC or Vexo.", Toast.LENGTH_LONG).show()
            }
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc"))) } 
            catch (e: Exception) {}
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
    // 🎁 دوال إعلان المكافأة
    // ============================================
    
    @JavascriptInterface
    fun loadRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            if (rewardedAd != null) { notifyWeb("onAdLoaded"); return@post }
            RewardedAd.load(context, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad; notifyWeb("onAdLoaded")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null; notifyWeb("onAdFailed")
                        Handler(Looper.getMainLooper()).postDelayed({ loadRewardedAd() }, 30000)
                    }
                })
        }    }
    
    // ✅ دالة عرض الإعلان - النسخة الصحيحة
    @JavascriptInterface
    fun showRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            rewardGranted = false
            if (rewardedAd != null) {
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        if (!rewardGranted) notifyWeb("onAdNotAvailable")
                        loadRewardedAd()
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        rewardedAd = null; rewardGranted = false
                        notifyWeb("onAdNotAvailable"); loadRewardedAd()
                    }
                }
                rewardedAd?.show(activity) { rewardItem ->
                    rewardGranted = true
                    Toast.makeText(context, "✅ Reward Granted!", Toast.LENGTH_SHORT).show()
                    
                    // ✅ احصل على Category ID من الويب وافتح الفئة
                    activity.webView.evaluateJavascript(
                        "(function(){ return RewardedAds.currentCategoryId || ''; })();"
                    ) { categoryId ->
                        val catId = categoryId?.replace("\"", "") ?: ""
                        val catName = "Premium"
                        if (catId.isNotEmpty()) {
                            openCategoryAfterReward(catId, catName)
                        } else {
                            // ✅ إذا لم نحصل على ID، نستخدم الطريقة العادية
                            Handler(Looper.getMainLooper()).postDelayed({
                                activity.webView.loadUrl("javascript:window.RewardedAds.onAdRewarded()")
                            }, 500)
                        }
                    }
                    loadRewardedAd()
                }
            } else {
                notifyWeb("onAdNotAvailable"); loadRewardedAd()
            }
        }
    }
    
    // ✅ دالة فتح الفئة مباشرة من الأندرويد
    @JavascriptInterface
    fun openCategoryAfterReward(categoryId: String, categoryName: String) {
        activity.runOnUiThread {            val jsCode = "if(window.Channels&&window.Channels.load){window.Channels.load('$categoryId', '$categoryName');}"
            try {
                activity.webView.loadUrl("javascript:$jsCode")
                Toast.makeText(context, "🎬 Opening $categoryName...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("GalilTV", "❌ Failed: ${e.message}")
            }
        }
    }
    
    @JavascriptInterface
    fun refreshRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            rewardedAd = null; rewardGranted = false; loadRewardedAd()
        }
    }
    
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        return rewardedAd != null
    }
    
    // ============================================
    // 📡 دوال إرسال الأحداث للويب
    // ============================================
    private fun notifyWeb(eventName: String) {
        activity.runOnUiThread {
            val jsCode = when (eventName) {
                "onAdRewarded" -> "window.RewardedAds.onAdRewarded()"
                "onAdLoaded" -> "window.RewardedAds.onAdLoaded()"
                "onAdFailed" -> "window.RewardedAds.onAdFailed()"
                "onAdNotAvailable" -> "window.RewardedAds.onAdNotAvailable()"
                else -> ""
            }
            if (jsCode.isNotEmpty()) {
                try { activity.webView.loadUrl("javascript:$jsCode") } 
                catch (e: Exception) { Log.e("GalilTV", "❌ notifyWeb: ${e.message}") }
            }
        }
    }

    // ============================================
    // 🔍 دالة التحقق من تثبيت التطبيقات
    // ============================================
    private fun isAppInstalled(packageName: String): Boolean {
        return try { context.packageManager.getPackageInfo(packageName, 0); true } 
        catch (e: PackageManager.NameNotFoundException) { false }
    }
}
