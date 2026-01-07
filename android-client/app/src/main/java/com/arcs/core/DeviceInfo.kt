package com.arcs.client.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Device information provider
 * Collects device metadata for server registration
 */
class DeviceInfo(private val context: Context) {
    
    val deviceId: String by lazy {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }
    
    val model: String = Build.MODEL
    val manufacturer: String = Build.MANUFACTURER
    val androidVersion: String = Build.VERSION.RELEASE
    val sdkVersion: Int = Build.VERSION.SDK_INT
    
    val screenWidth: Int
    val screenHeight: Int
    val densityDpi: Int
    
    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            densityDpi = context.resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            densityDpi = displayMetrics.densityDpi
        }
    }
    
    /**
     * Convert to JSON-compatible map
     */
    fun toMap(): Map<String, Any> = mapOf(
        "device_id" to deviceId,
        "model" to "$manufacturer $model",
        "android_version" to androidVersion,
        "sdk_version" to sdkVersion,
        "screen_width" to screenWidth,
        "screen_height" to screenHeight,
        "dpi" to densityDpi
    )
    
    override fun toString(): String {
        return "DeviceInfo(id=$deviceId, model=$manufacturer $model, " +
                "android=$androidVersion, screen=${screenWidth}x${screenHeight}, dpi=$densityDpi)"
    }
}
