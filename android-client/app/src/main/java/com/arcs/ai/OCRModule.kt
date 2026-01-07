package com.arcs.client.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR module using ML Kit
 * Extracts text from screen frames
 */
class OCRModule(private val context: Context) {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    data class TextBlock(
        val text: String,
        val confidence: Float,
        val bounds: Rect
    )
    
    /**
     * Extract text from bitmap
     */
    suspend fun extractText(bitmap: Bitmap): List<TextBlock> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val textBlocks = mutableListOf<TextBlock>()
                        
                        for (block in visionText.textBlocks) {
                            val bounds = block.boundingBox ?: continue
                            
                            textBlocks.add(
                                TextBlock(
                                    text = block.text,
                                    confidence = 1.0f,
                                    bounds = bounds
                                )
                            )
                        }
                        
                        Timber.d("Extracted ${textBlocks.size} text blocks")
                        
                        if (continuation.isActive) {
                            continuation.resume(textBlocks)
                        }
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "OCR failed")
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                    
            } catch (e: Exception) {
                Timber.e(e, "OCR error")
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * Extract text from specific region
     */
    suspend fun extractTextFromRegion(
        bitmap: Bitmap,
        region: Rect
    ): List<TextBlock> {
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            region.left,
            region.top,
            region.width(),
            region.height()
        )
        
        val blocks = extractText(croppedBitmap)
        
        // Adjust coordinates back to full image
        return blocks.map { block ->
            val adjustedBounds = Rect(
                block.bounds.left + region.left,
                block.bounds.top + region.top,
                block.bounds.right + region.left,
                block.bounds.bottom + region.top
            )
            block.copy(bounds = adjustedBounds)
        }
    }
    
    /**
     * Find text in extracted blocks
     */
    fun findText(
        blocks: List<TextBlock>,
        searchText: String,
        matchType: MatchType = MatchType.CONTAINS
    ): List<TextBlock> {
        return blocks.filter { block ->
            when (matchType) {
                MatchType.EXACT -> block.text.equals(searchText, ignoreCase = true)
                MatchType.CONTAINS -> block.text.contains(searchText, ignoreCase = true)
                MatchType.STARTS_WITH -> block.text.startsWith(searchText, ignoreCase = true)
                MatchType.REGEX -> Regex(searchText, RegexOption.IGNORE_CASE).matches(block.text)
            }
        }
    }
    
    /**
     * Get center point of text block
     */
    fun getCenterPoint(block: TextBlock): Pair<Int, Int> {
        return Pair(
            block.bounds.centerX(),
            block.bounds.centerY()
        )
    }
    
    /**
     * Get all visible text as string
     */
    fun getAllText(blocks: List<TextBlock>): String {
        return blocks.joinToString("\n") { it.text }
    }
    
    /**
     * Close recognizer
     */
    fun close() {
        recognizer.close()
    }
    
    enum class MatchType {
        EXACT,
        CONTAINS,
        STARTS_WITH,
        REGEX
    }
}
