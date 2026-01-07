package com.arcs.client.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Accessibility Service for injecting touch gestures
 * Requires user to enable in Settings > Accessibility
 */
class RemoteAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: RemoteAccessibilityService? = null
        
        fun getInstance(): RemoteAccessibilityService? = instance
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("RemoteAccessibilityService connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for input injection
    }
    
    override fun onInterrupt() {
        Timber.w("RemoteAccessibilityService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Timber.i("RemoteAccessibilityService destroyed")
    }
    
    /**
     * Perform tap gesture
     */
    suspend fun performTap(x: Float, y: Float, duration: Long = 50): Boolean {
        return performGesture(createTapGesture(x, y, duration))
    }
    
    /**
     * Perform swipe gesture
     */
    suspend fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 300
    ): Boolean {
        return performGesture(createSwipeGesture(startX, startY, endX, endY, duration))
    }
    
    /**
     * Perform long press
     */
    suspend fun performLongPress(x: Float, y: Float, duration: Long = 1000): Boolean {
        return performGesture(createTapGesture(x, y, duration))
    }
    
    /**
     * Perform pinch gesture
     */
    suspend fun performPinch(
        centerX: Float,
        centerY: Float,
        startDistance: Float,
        endDistance: Float,
        duration: Long = 500
    ): Boolean {
        return performGesture(createPinchGesture(centerX, centerY, startDistance, endDistance, duration))
    }
    
    /**
     * Execute gesture description
     */
    private suspend fun performGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        Timber.w("Gesture cancelled")
                        continuation.resume(false)
                    }
                }
            }
            
            val success = dispatchGesture(gesture, callback, null)
            if (!success) {
                if (continuation.isActive) {
                    Timber.e("Failed to dispatch gesture")
                    continuation.resume(false)
                }
            }
        }
    
    /**
     * Create tap gesture
     */
    private fun createTapGesture(x: Float, y: Float, duration: Long): GestureDescription {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
    
    /**
     * Create swipe gesture
     */
    private fun createSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long
    ): GestureDescription {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
    
    /**
     * Create pinch gesture (two-finger)
     */
    private fun createPinchGesture(
        centerX: Float,
        centerY: Float,
        startDistance: Float,
        endDistance: Float,
        duration: Long
    ): GestureDescription {
        val builder = GestureDescription.Builder()
        
        // First finger path
        val path1 = Path().apply {
            moveTo(centerX, centerY - startDistance / 2)
            lineTo(centerX, centerY - endDistance / 2)
        }
        builder.addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
        
        // Second finger path
        val path2 = Path().apply {
            moveTo(centerX, centerY + startDistance / 2)
            lineTo(centerX, centerY + endDistance / 2)
        }
        builder.addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
        
        return builder.build()
    }
    
    /**
     * Perform system navigation action (wrapper)
     */
    fun doGlobalAction(action: Int): Boolean {
        return performGlobalAction(action)
    }
}
