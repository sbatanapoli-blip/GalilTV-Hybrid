package com.galiltv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun playVideo(url: String) {
        try {
            // ✅ إنشاء Intent لفتح Vexo Player فقط
            val intent = Intent(Intent.ACTION_VIEW).apply {
                // تعيين الرابط ونوع الفيديو
                setDataAndType(Uri.parse(url), "video/*")
                
                // تحديد حزمة التطبيق (يجب أن يكون مثبتاً)
                setPackage("com.vexo.player")
                
                // إعدادات التشغيل (مهم جداً لـ TV و الخلفية)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                
                // بيانات إضافية (اختياري)
                putExtra("title", "Galil TV")
                putExtra("subs", "")
            }
            
            // محاولة تشغيل التطبيق
            context.startActivity(intent)
            
        } catch (e: ActivityNotFoundException) {
            // ❌ إذا لم يكن التطبيق مثبتاً
            showCustomToast("Vexo Player is not installed")
        } catch (e: Exception) {
            // ❌ أي خطأ آخر
            showCustomToast("Error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun showToast(msg: String) {
        showCustomToast(msg)
    }

    // دالة مساعدة لعرض الرسالة في الخيط الرئيسي (UI Thread)
    private fun showCustomToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
