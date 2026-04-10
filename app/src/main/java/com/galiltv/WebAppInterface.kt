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
            val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage("org.videolan.vlc")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(vlcIntent)
        } catch (e: Exception) {
            try {
                val mxIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                    setPackage("com.mxtech.videoplayer.ad")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(mxIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Install VLC/MX Player for best experience", Toast.LENGTH_LONG).show()
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(fallback)
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
