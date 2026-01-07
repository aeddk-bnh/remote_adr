package com.arcs.client.ai

import android.content.Context
import android.graphics.Bitmap
import com.arcs.client.input.TouchInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * AI-assisted interaction manager
 * Combines OCR and UI detection for smart control
 */
class AIAssistant(
    private val context: Context,
    private val touchInjector: TouchInjector,
    private val scope: CoroutineScope
) {
    
    private val ocrModule = OCRModule(context)
    private val uiDetector = UIDetector(context)
    
    /**
     * Click on element containing specific text
     */
    suspend fun clickByText(
        bitmap: Bitmap,
        text: String,
        matchType: OCRModule.MatchType = OCRModule.MatchType.CONTAINS
    ): Boolean {
        return try {
            // Extract text blocks
            val textBlocks = ocrModule.extractText(bitmap)
            
            // Find matching text
            val matches = ocrModule.findText(textBlocks, text, matchType)
            
            if (matches.isEmpty()) {
                Timber.w("Text not found: $text")
                return false
            }
            
            // Click on first match
            val target = matches.first()
            val (x, y) = ocrModule.getCenterPoint(target)
            
            Timber.i("Clicking on text '$text' at ($x, $y)")
            touchInjector.tap(x, y)
            
        } catch (e: Exception) {
            Timber.e(e, "Click by text failed")
            false
        }
    }
    
    /**
     * Click on UI element of specific type
     */
    suspend fun clickElementType(
        bitmap: Bitmap,
        elementType: UIDetector.ElementType,
        index: Int = 0
    ): Boolean {
        return try {
            // Detect elements
            val elements = uiDetector.detectElementType(bitmap, elementType)
            
            if (elements.size <= index) {
                Timber.w("Element not found: $elementType at index $index")
                return false
            }
            
            // Click on element
            val target = elements[index]
            val (x, y) = uiDetector.getCenterPoint(target)
            
            Timber.i("Clicking on $elementType at ($x, $y)")
            touchInjector.tap(x, y)
            
        } catch (e: Exception) {
            Timber.e(e, "Click element failed")
            false
        }
    }
    
    /**
     * Get all visible text
     */
    suspend fun extractAllText(bitmap: Bitmap): String {
        return try {
            val textBlocks = ocrModule.extractText(bitmap)
            ocrModule.getAllText(textBlocks)
        } catch (e: Exception) {
            Timber.e(e, "Extract text failed")
            ""
        }
    }
    
    /**
     * Check if text exists on screen
     */
    suspend fun hasText(
        bitmap: Bitmap,
        text: String,
        matchType: OCRModule.MatchType = OCRModule.MatchType.CONTAINS
    ): Boolean {
        return try {
            val textBlocks = ocrModule.extractText(bitmap)
            val matches = ocrModule.findText(textBlocks, text, matchType)
            matches.isNotEmpty()
        } catch (e: Exception) {
            Timber.e(e, "Text check failed")
            false
        }
    }
    
    /**
     * Get clickable element count
     */
    suspend fun getClickableCount(bitmap: Bitmap): Int {
        return try {
            val elements = uiDetector.findClickableElements(bitmap)
            elements.size
        } catch (e: Exception) {
            Timber.e(e, "Get clickable count failed")
            0
        }
    }
    
    /**
     * Find and click button with text
     */
    suspend fun clickButton(bitmap: Bitmap, buttonText: String): Boolean {
        return try {
            // First try OCR-based click
            val textClicked = clickByText(bitmap, buttonText)
            if (textClicked) {
                return true
            }
            
            // Fallback to UI detection
            val buttons = uiDetector.detectElementType(bitmap, UIDetector.ElementType.BUTTON)
            
            // Try each button (could be enhanced with position heuristics)
            for (button in buttons) {
                val (x, y) = uiDetector.getCenterPoint(button)
                Timber.i("Trying button at ($x, $y)")
                touchInjector.tap(x, y)
                return true
            }
            
            false
        } catch (e: Exception) {
            Timber.e(e, "Click button failed")
            false
        }
    }
    
    /**
     * Close resources
     */
    fun close() {
        ocrModule.close()
        uiDetector.close()
    }
}
