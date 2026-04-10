package com.galiltv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun playVideo(url: String) {
        val players = listOf(
            // Vexo Player
            "com.vexo.player",
            // VLC
            "org.videolan.vlc",
            // MX Player
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.ar",
            // Just Player (خفيف جداً للـ TV)
            "com.brouken.player",
            // Nova Player
            "com.caydey.ffshare"
        )

        var launched = false
        
        for (player in players) {
            if (isAppInstalled(player)) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), "video/*")
                        setPackage(player)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("title", "Galil TV")
                    }
                    context.startActivity(intent)
                    launched = true
                    break
                } catch (e: Exception) {
                    // جرّب المشغل التالي
                    continue
                }
            }
        }

        // إذا لم ينجح أي مشغل
        if (!launched) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "No video player found. Install VLC or Vexo.", Toast.LENGTH_LONG).show()
            }
            // فتح المتجر لاقتراح تثبيت مشغل
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc")))
            } catch (e: Exception) {}
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

    @JavascriptInterface
    fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
