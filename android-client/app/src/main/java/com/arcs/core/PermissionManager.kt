package com.arcs.client.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Manages runtime permissions and service enablement
 */
class PermissionManager(private val context: Context) {
    
    companion object {
        const val REQUEST_CODE_MEDIA_PROJECTION = 1001
        const val REQUEST_CODE_POST_NOTIFICATIONS = 1002
        const val REQUEST_CODE_ACCESSIBILITY = 1003
    }
    
    /**
     * Check if MediaProjection permission is granted
     * Note: MediaProjection requires user consent dialog each session
     */
    fun requestMediaProjection(activity: Activity): Intent {
        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager
        return mediaProjectionManager.createScreenCaptureIntent()
    }
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required below Android 13
        }
    }
    
    /**
     * Request notification permission
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
        }
    }
    
    /**
     * Check if Accessibility Service is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${context.packageName}/.accessibility.RemoteAccessibilityService"
        
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return enabledServices?.contains(serviceName) == true
    }
    
    /**
     * Open Accessibility settings for user to enable service
     */
    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }
    
    /**
     * Check if IME is enabled
     */
    fun isIMEEnabled(): Boolean {
        val imeName = "${context.packageName}/.input.RemoteIME"
        
        val enabledIMEs = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        )
        
        return enabledIMEs?.contains(imeName) == true
    }
    
    /**
     * Open IME settings for user to enable service
     */
    fun openIMESettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }
    
    /**
     * Check all required permissions
     */
    fun checkAllPermissions(): PermissionStatus {
        return PermissionStatus(
            hasNotificationPermission = hasNotificationPermission(),
            accessibilityServiceEnabled = isAccessibilityServiceEnabled(),
            imeEnabled = isIMEEnabled()
        )
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllRequiredPermissions(): Boolean {
        return checkAllPermissions().allGranted
    }
    
    /**
     * Request all required permissions
     */
    fun requestAllPermissions(activity: Activity) {
        if (!hasNotificationPermission()) {
            requestNotificationPermission(activity)
        }
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings(activity)
        }
        if (!isIMEEnabled()) {
            openIMESettings(activity)
        }
    }
    
    /**
     * Handle permission result
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.d("Notification permission granted")
                } else {
                    Timber.w("Notification permission denied")
                }
            }
        }
    }
    
    data class PermissionStatus(
        val hasNotificationPermission: Boolean,
        val accessibilityServiceEnabled: Boolean,
        val imeEnabled: Boolean
    ) {
        val allGranted: Boolean
            get() = hasNotificationPermission && accessibilityServiceEnabled && imeEnabled
        
        fun getMissingPermissions(): List<String> = buildList {
            if (!hasNotificationPermission) add("Notification Permission")
            if (!accessibilityServiceEnabled) add("Accessibility Service")
            if (!imeEnabled) add("Input Method")
        }
    }
}
