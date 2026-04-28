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

    @JavascriptInterface
    fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun showInterstitialAd() {
        Handler(Looper.getMainLooper()).post {
            activity.showInterstitialAd()
        }
    }

    @JavascriptInterface
    fun loadRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            Log.d("GalilTV", "🔄 Loading rewarded ad...")
            
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
                        
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadRewardedAd()
                        }, 30000)
                    }
                }
            )
        }
    }
    
    @JavascriptInterface
    fun showRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            Log.d("GalilTV", "🎬 Showing rewarded ad...")
            rewardGranted = false
            
            if (rewardedAd != null) {
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("GalilTV", "📺 Rewarded ad dismissed")
                        rewardedAd = null
                        
                        if (!rewardGranted) {
                            Log.d("GalilTV", "⚠️ Ad closed without reward")
                            notifyWeb("onAdNotAvailable")
                        }
                        
                        loadRewardedAd()
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Log.e("GalilTV", "❌ Failed to show rewarded ad: ${error.message}")
                        rewardedAd = null
                        rewardGranted = false
                        notifyWeb("onAdNotAvailable")
                        loadRewardedAd()
                    }
                    
                    override fun onAdShowedFullScreenContent() {
                        Log.d("GalilTV", "👀 Rewarded ad shown")
                    }
                }
                
                rewardedAd?.show(activity) { rewardItem ->
                    val rewardAmount = rewardItem.amount
                    val rewardType = rewardItem.type
                    Log.d("GalilTV", "💰 Reward granted: $rewardAmount $rewardType")
                    
                    rewardGranted = true
                    
                    // ✅ تأخير بسيط لضمان استقبال الجافا سكريبت
                    Handler(Looper.getMainLooper()).postDelayed({
                        notifyWeb("onAdRewarded")
                    }, 500)
                    
                    loadRewardedAd()
                }
            } else {
                Log.e("GalilTV", "❌ Rewarded ad not ready")
                notifyWeb("onAdNotAvailable")
                loadRewardedAd()
            }
        }
    }
    
    @JavascriptInterface
    fun refreshRewardedAd() {
        Handler(Looper.getMainLooper()).post {
            Log.d("GalilTV", "🔄 Refreshing rewarded ad...")
            rewardedAd = null
            rewardGranted = false
            loadRewardedAd()
        }
    }
    
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        val isReady = rewardedAd != null
        Log.d("GalilTV", "🔍 Rewarded ad ready: $isReady")
        return isReady
    }
    
    // ✅ دالة محسنة لإرسال الأحداث إلى الويب
    private fun notifyWeb(eventName: String) {
        activity.runOnUiThread {
            if (activity.webView == null) {
                Log.e("GalilTV", "❌ WebView is null, cannot notify")
                return@runOnUiThread
            }
            
            val jsCode = when (eventName) {
                "onAdLoaded" -> """
                    (function() {
                        console.log('📡 Android: onAdLoaded');
                        if (window.RewardedAds && window.RewardedAds.onAdLoaded) {
                            window.RewardedAds.onAdLoaded();
                        }
                    })();
                """.trimIndent()
                "onAdFailed" -> """
                    (function() {
                        console.log('📡 Android: onAdFailed');
                        if (window.RewardedAds && window.RewardedAds.onAdFailed) {
                            window.RewardedAds.onAdFailed();
                        }
                    })();
                """.trimIndent()
                "onAdRewarded" -> """
                    (function() {
                        console.log('📡 Android: onAdRewarded - Opening category now!');
                        if (window.RewardedAds && window.RewardedAds.onAdRewarded) {
                            window.RewardedAds.onAdRewarded();
                        } else if (window.onAdRewarded) {
                            window.onAdRewarded();
                        } else {
                            console.error('❌ No callback found for onAdRewarded');
                        }
                    })();
                """.trimIndent()
                "onAdNotAvailable" -> """
                    (function() {
                        console.log('📡 Android: onAdNotAvailable');
                        if (window.RewardedAds && window.RewardedAds.onAdNotAvailable) {
                            window.RewardedAds.onAdNotAvailable();
                        }
                    })();
                """.trimIndent()
                else -> ""
            }
            
            if (jsCode.isNotEmpty()) {
                activity.webView.evaluateJavascript(jsCode) { result ->
                    Log.d("GalilTV-JS", "📤 JS result: $result")
                }
            }
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
