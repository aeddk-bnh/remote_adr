package com.arcs.client.input

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import timber.log.Timber

/**
 * Input Method Service for remote text input
 * Allows injection of text and key events
 */
class RemoteIME : InputMethodService() {
    
    companion object {
        private var instance: RemoteIME? = null
        
        fun getInstance(): RemoteIME? = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.i("RemoteIME created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Timber.i("RemoteIME destroyed")
    }
    
    /**
     * Insert text at current cursor position
     */
    fun insertText(text: String): Boolean {
        return try {
            currentInputConnection?.commitText(text, 1) != null
        } catch (e: Exception) {
            Timber.e(e, "Error inserting text")
            false
        }
    }
    
    /**
     * Send key event
     */
    fun sendKey(keyCode: Int): Boolean {
        return try {
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            ) ?: false && 
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, keyCode)
            ) ?: false
        } catch (e: Exception) {
            Timber.e(e, "Error sending key event")
            false
        }
    }
    
    /**
     * Delete text before cursor
     */
    fun deleteText(count: Int): Boolean {
        return try {
            currentInputConnection?.deleteSurroundingText(count, 0) != null
        } catch (e: Exception) {
            Timber.e(e, "Error deleting text")
            false
        }
    }
    
    /**
     * Get text before cursor
     */
    fun getTextBeforeCursor(length: Int): CharSequence? {
        return try {
            currentInputConnection?.getTextBeforeCursor(length, 0)
        } catch (e: Exception) {
            Timber.e(e, "Error getting text")
            null
        }
    }
    
    /**
     * Clear all text
     */
    fun clearText(): Boolean {
        return try {
            val text = currentInputConnection?.getTextBeforeCursor(1000, 0)
            if (text != null) {
                currentInputConnection?.deleteSurroundingText(text.length, 0) != null
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clearing text")
            false
        }
    }
}
