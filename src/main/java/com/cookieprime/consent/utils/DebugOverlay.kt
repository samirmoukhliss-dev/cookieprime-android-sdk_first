package com.cookieprime.consent.utils

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.coroutines.runBlocking

object DebugOverlay {
    private var overlayView: TextView? = null

    fun install(context: Context) {
        try {
            remove()
            
            // Check if we have overlay permission
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(context)) {
                    Log.w("DebugOverlay", "No overlay permission, skipping debug overlay")
                    return
                }
            }
            
            overlayView = TextView(context).apply {
                text = "🍪 CookiePrime Debug"
                setBackgroundColor(0x80000000.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(16, 8, 16, 8)
                gravity = Gravity.CENTER
                
                setOnClickListener {
                    showDebugInfo(context)
                }
            }
            
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams().apply {
                type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    android.view.WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }
            
            windowManager.addView(overlayView, params)
            Log.d("DebugOverlay", "Debug overlay installed")
            
        } catch (e: Exception) {
            Log.e("DebugOverlay", "Failed to install debug overlay", e)
        }
    }
    
    fun remove() {
        overlayView?.let { view ->
            try {
                val context = view.context
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(view)
                overlayView = null
            } catch (e: Exception) {
                Log.e("DebugOverlay", "Failed to remove debug overlay", e)
            }
        }
    }
    
    private fun showDebugInfo(context: Context) {
        try {
            val sdk = runBlocking { com.cookieprime.consent.ConsentSDK.getInstanceOrNull() }
            val info = if (sdk != null) {
                """
                    🍪 CookiePrime Debug Info
                    
                    App ID: ${sdk.appId}
                    Detected SDKs: ${sdk.detectedSDKs.size}
                    Required Categories: ${sdk.requiredCategories.size}
                    Region: ${sdk.regionDetector.getCurrentRegion()}
                    
                    Tap to dismiss
                """.trimIndent()
            } else {
                "SDK not initialized"
            }
            
            android.app.AlertDialog.Builder(context)
                .setTitle("CookiePrime Debug")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
                
        } catch (e: Exception) {
            Log.e("DebugOverlay", "Failed to show debug info", e)
        }
    }
}