package com.arcs.client.input

import android.view.KeyEvent
import timber.log.Timber

/**
 * Handles keyboard input injection
 */
class KeyInjector {
    
    /**
     * Send text via IME
     */
    fun sendText(text: String): Boolean {
        val ime = RemoteIME.getInstance()
        if (ime == null) {
            Timber.e("RemoteIME not available")
            return false
        }
        
        Timber.d("Sending text: $text")
        return ime.insertText(text)
    }
    
    /**
     * Send keycode
     */
    fun sendKeyCode(keyCode: Int): Boolean {
        val ime = RemoteIME.getInstance()
        if (ime == null) {
            Timber.e("RemoteIME not available")
            return false
        }
        
        Timber.d("Sending keycode: $keyCode")
        return ime.sendKey(keyCode)
    }
    
    /**
     * Send key combination (e.g., Ctrl+C)
     * Note: Limited support on Android
     */
    fun sendKeyCombination(vararg keyCodes: Int): Boolean {
        // Android has limited support for key combinations
        // This is a simplified implementation
        var success = true
        for (keyCode in keyCodes) {
            success = success && sendKeyCode(keyCode)
        }
        return success
    }
    
    /**
     * Map keycode name string to Android KeyEvent constant
     */
    fun parseKeyCode(keyCodeName: String): Int {
        return when (keyCodeName.uppercase()) {
            "KEYCODE_BACK" -> KeyEvent.KEYCODE_BACK
            "KEYCODE_HOME" -> KeyEvent.KEYCODE_HOME
            "KEYCODE_MENU" -> KeyEvent.KEYCODE_MENU
            "KEYCODE_ENTER" -> KeyEvent.KEYCODE_ENTER
            "KEYCODE_DEL" -> KeyEvent.KEYCODE_DEL
            "KEYCODE_TAB" -> KeyEvent.KEYCODE_TAB
            "KEYCODE_SPACE" -> KeyEvent.KEYCODE_SPACE
            "KEYCODE_DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
            "KEYCODE_DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "KEYCODE_DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "KEYCODE_DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "KEYCODE_DPAD_CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
            "KEYCODE_VOLUME_UP" -> KeyEvent.KEYCODE_VOLUME_UP
            "KEYCODE_VOLUME_DOWN" -> KeyEvent.KEYCODE_VOLUME_DOWN
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }
}
