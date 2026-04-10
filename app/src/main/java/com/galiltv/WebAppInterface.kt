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
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
