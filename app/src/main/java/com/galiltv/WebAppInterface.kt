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

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun playVideo(url: String) {
        // ✅ قائمة بالمشغلات المدعومة بالترتيب
        val players = listOf(
            "com.vexo.player",           // Vexo Player
            "org.videolan.vlc",          // VLC
            "com.mxtech.videoplayer.ad", // MX Player
            "com.brouken.player",        // Just Player
            "com.archos.mediacenter.videofree" // Archos
        )

        var launched = false

        // ✅ جرّب كل مشغل حتى ينجح واحد
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
                    break // ✅ خرج من الحلقة عند النجاح
                } catch (e: Exception) {
                    // جرّب المشغل التالي
                    continue
                }
            }
        }

        // ❌ إذا لم ينجح أي مشغل
        if (!launched) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "No video player found. Install VLC or Vexo.", Toast.LENGTH_LONG).show()
            }
            // افتح المتجر لاقتراح تثبيت مشغل
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

    // ✅ دالة للتحقق مما إذا كان التطبيق مثبتاً
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
