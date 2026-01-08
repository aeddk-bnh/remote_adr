package com.arcs.client.input

import com.arcs.client.accessibility.RemoteAccessibilityService
import timber.log.Timber

/**
 * Handles touch injection commands
 * Maps remote coordinates to device coordinates
 */
class TouchInjector(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    
    /**
     * Inject tap at specified coordinates
     */
    suspend fun tap(x: Int, y: Int): Boolean {
        val service = RemoteAccessibilityService.getInstance()
        if (service == null) {
            Timber.e("AccessibilityService not available")
            return false
        }
        
        val mappedX = mapX(x.toFloat())
        val mappedY = mapY(y.toFloat())
        
        Timber.d("Tap: ($x,$y) -> ($mappedX,$mappedY)")
        val result = service.performTap(mappedX, mappedY)
        Timber.i("Tap result: success=%s at (%s,%s)", result, mappedX, mappedY)
        return result
    }
    
    /**
     * Inject swipe gesture
     */
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 300
    ): Boolean {
        val service = RemoteAccessibilityService.getInstance()
        if (service == null) {
            Timber.e("AccessibilityService not available")
            return false
        }
        
        val mappedStartX = mapX(startX.toFloat())
        val mappedStartY = mapY(startY.toFloat())
        val mappedEndX = mapX(endX.toFloat())
        val mappedEndY = mapY(endY.toFloat())
        
        Timber.d("Swipe: ($startX,$startY)->($endX,$endY), ${duration}ms")
        val result = service.performSwipe(
            mappedStartX, mappedStartY,
            mappedEndX, mappedEndY,
            duration
        )
        Timber.i("Swipe result: success=%s from (%s,%s) to (%s,%s) dur=%sms", result, mappedStartX, mappedStartY, mappedEndX, mappedEndY, duration)
        return result
    }
    
    /**
     * Inject long press
     */
    suspend fun longPress(x: Int, y: Int, duration: Long = 1000): Boolean {
        val service = RemoteAccessibilityService.getInstance()
        if (service == null) {
            Timber.e("AccessibilityService not available")
            return false
        }
        
        val mappedX = mapX(x.toFloat())
        val mappedY = mapY(y.toFloat())
        
        Timber.d("Long press: ($x,$y), ${duration}ms")
        val result = service.performLongPress(mappedX, mappedY, duration)
        Timber.i("LongPress result: success=%s at (%s,%s) dur=%sms", result, mappedX, mappedY, duration)
        return result
    }
    
    /**
     * Inject pinch/zoom gesture
     */
    suspend fun pinch(
        centerX: Int,
        centerY: Int,
        startDistance: Int,
        endDistance: Int,
        duration: Long = 500
    ): Boolean {
        val service = RemoteAccessibilityService.getInstance()
        if (service == null) {
            Timber.e("AccessibilityService not available")
            return false
        }
        
        val mappedCenterX = mapX(centerX.toFloat())
        val mappedCenterY = mapY(centerY.toFloat())
        
        Timber.d("Pinch: center=($centerX,$centerY), $startDistance->$endDistance")
        val result = service.performPinch(
            mappedCenterX, mappedCenterY,
            startDistance.toFloat(), endDistance.toFloat(),
            duration
        )
        Timber.i("Pinch result: success=%s center=(%s,%s) %s->%s dur=%sms", result, mappedCenterX, mappedCenterY, startDistance, endDistance, duration)
        return result
    }
    
    /**
     * Map X coordinate (identity mapping if same resolution)
     * Can be extended for resolution scaling
     */
    private fun mapX(x: Float): Float {
        return x.coerceIn(0f, screenWidth.toFloat())
    }
    
    /**
     * Map Y coordinate (identity mapping if same resolution)
     * Can be extended for resolution scaling
     */
    private fun mapY(y: Float): Float {
        return y.coerceIn(0f, screenHeight.toFloat())
    }
}
