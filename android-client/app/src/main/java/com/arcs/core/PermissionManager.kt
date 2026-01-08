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
     * Note: On Android 14+ (API 34+), reading ENABLED_ACCESSIBILITY_SERVICES is restricted.
     * We use try-catch to handle SecurityException gracefully.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            if (enabledServices == null) return false
            
            // Accessibility services may be listed in different forms depending on the device:
            // - short form: "com.pkg/.accessibility.RemoteAccessibilityService"
            // - long form:  "com.pkg/com.pkg.accessibility.RemoteAccessibilityService"
            val shortName = "${context.packageName}/.accessibility.RemoteAccessibilityService"
            val longName = "${context.packageName}/${context.packageName}.accessibility.RemoteAccessibilityService"

            enabledServices.contains(shortName) || enabledServices.contains(longName)
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot read accessibility settings on Android 14+, assuming not enabled")
            false
        } catch (e: Exception) {
            Timber.w(e, "Error checking accessibility status")
            false
        }
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
     * Note: On Android 14+ (API 34+), we cannot read ENABLED_INPUT_METHODS due to privacy restrictions.
     * We'll use InputMethodManager to check instead.
     */
    fun isIMEEnabled(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val enabledIMEs = imm.enabledInputMethodList
            val myIME = "${context.packageName}/.input.RemoteIME"
            
            enabledIMEs.any { it.id == myIME || it.id == "${context.packageName}/com.arcs.client.input.RemoteIME" }
        } catch (e: Exception) {
            Timber.w(e, "Cannot check IME status, assuming not enabled")
            false
        }
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
        val status = PermissionStatus(
            hasNotificationPermission = hasNotificationPermission(),
            accessibilityServiceEnabled = isAccessibilityServiceEnabled(),
            imeEnabled = isIMEEnabled()
        )
        Timber.d("PermissionStatus: hasNotification=%s, accessibility=%s, ime=%s, missing=%s",
            status.hasNotificationPermission,
            status.accessibilityServiceEnabled,
            status.imeEnabled,
            status.getMissingPermissions().joinToString(", ")
        )
        return status
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
