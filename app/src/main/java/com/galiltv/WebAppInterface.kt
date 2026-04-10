package com.galiltv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun playVideo(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                type = "video/*" // مهم جداً للمشغلات
                setPackage("com.vexo.player")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // ✅ Vexo غير موجود أو لا يستجيب للرابط
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Vexo not responding. Check if it supports direct URLs.", Toast.LENGTH_LONG).show()
            }
            // فتح متجر جوجل كحل بديل (اختياري)
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.vexo.player")))
            } catch (e2: Exception) {}
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @JavascriptInterface
    fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
