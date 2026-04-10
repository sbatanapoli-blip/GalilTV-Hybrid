package com.galiltv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun playVideo(url: String) {
        try {
            // ✅ فتح Vexo Player فقط
            val vexoIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage("com.vexo.player")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(vexoIntent)
        } catch (e: Exception) {
            // ❌ إذا لم يكن Vexo مثبتاً
            Toast.makeText(context, "Please install Vexo Player", Toast.LENGTH_LONG).show()
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
