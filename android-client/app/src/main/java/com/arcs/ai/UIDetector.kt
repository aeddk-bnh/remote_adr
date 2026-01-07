package com.arcs.client.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * UI element detection using ML Kit object detection
 */
class UIDetector(private val context: Context) {
    
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()
    
    private val detector = ObjectDetection.getClient(options)
    
    data class UIElement(
        val type: ElementType,
        val confidence: Float,
        val bounds: Rect,
        val label: String? = null
    )
    
    enum class ElementType {
        BUTTON,
        TEXT_FIELD,
        CHECKBOX,
        IMAGE,
        ICON,
        UNKNOWN
    }
    
    /**
     * Detect UI elements in bitmap
     */
    suspend fun detectElements(bitmap: Bitmap): List<UIElement> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                
                detector.process(image)
                    .addOnSuccessListener { detectedObjects ->
                        val elements = detectedObjects.mapNotNull { obj ->
                            val bounds = obj.boundingBox
                            
                            // Determine element type from labels
                            val type = classifyElement(obj.labels.firstOrNull()?.text)
                            
                            UIElement(
                                type = type,
                                confidence = obj.labels.firstOrNull()?.confidence ?: 0f,
                                bounds = bounds,
                                label = obj.labels.firstOrNull()?.text
                            )
                        }
                        
                        Timber.d("Detected ${elements.size} UI elements")
                        
                        if (continuation.isActive) {
                            continuation.resume(elements)
                        }
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "UI detection failed")
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                    
            } catch (e: Exception) {
                Timber.e(e, "UI detection error")
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * Detect specific element type
     */
    suspend fun detectElementType(
        bitmap: Bitmap,
        type: ElementType
    ): List<UIElement> {
        val allElements = detectElements(bitmap)
        return allElements.filter { it.type == type }
    }
    
    /**
     * Find clickable elements
     */
    suspend fun findClickableElements(bitmap: Bitmap): List<UIElement> {
        val allElements = detectElements(bitmap)
        return allElements.filter { 
            it.type in listOf(ElementType.BUTTON, ElementType.CHECKBOX, ElementType.ICON)
        }
    }
    
    /**
     * Get center point of element
     */
    fun getCenterPoint(element: UIElement): Pair<Int, Int> {
        return Pair(
            element.bounds.centerX(),
            element.bounds.centerY()
        )
    }
    
    /**
     * Classify element from label
     */
    private fun classifyElement(label: String?): ElementType {
        return when (label?.lowercase()) {
            "button" -> ElementType.BUTTON
            "text field", "edit text" -> ElementType.TEXT_FIELD
            "checkbox" -> ElementType.CHECKBOX
            "image view" -> ElementType.IMAGE
            "icon" -> ElementType.ICON
            else -> ElementType.UNKNOWN
        }
    }
    
    /**
     * Close detector
     */
    fun close() {
        detector.close()
    }
}
