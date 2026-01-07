package com.arcs.client.network

import com.arcs.client.input.KeyInjector
import com.arcs.client.input.TouchInjector
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dispatches received commands to appropriate handlers
 * Parses JSON commands and routes them to input injectors
 */
class CommandDispatcher(
    private val touchInjector: TouchInjector,
    private val keyInjector: KeyInjector,
    private val scope: CoroutineScope
) {
    
    private val gson = Gson()
    
    /**
     * Dispatch command
     */
    fun dispatch(json: String) {
        try {
            val command = gson.fromJson(json, JsonObject::class.java)
            val type = command.get("type")?.asString
            
            when (type) {
                "touch" -> handleTouchCommand(command)
                "key" -> handleKeyCommand(command)
                "system" -> handleSystemCommand(command)
                "app_control" -> handleAppControl(command)
                "macro" -> handleMacroCommand(command)
                "ai" -> handleAICommand(command)
                "ping" -> handlePing()
                else -> {
                    Timber.w("Unknown command type: $type")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error dispatching command")
        }
    }
    
    /**
     * Handle touch commands
     */
    private fun handleTouchCommand(cmd: JsonObject) {
        val action = cmd.get("action")?.asString
        
        scope.launch(Dispatchers.IO) {
            try {
                when (action) {
                    "tap" -> {
                        val x = cmd.get("x")?.asInt ?: return@launch
                        val y = cmd.get("y")?.asInt ?: return@launch
                        touchInjector.tap(x, y)
                    }
                    
                    "swipe" -> {
                        val startX = cmd.get("start_x")?.asInt ?: return@launch
                        val startY = cmd.get("start_y")?.asInt ?: return@launch
                        val endX = cmd.get("end_x")?.asInt ?: return@launch
                        val endY = cmd.get("end_y")?.asInt ?: return@launch
                        val duration = cmd.get("duration")?.asLong ?: 300L
                        touchInjector.swipe(startX, startY, endX, endY, duration)
                    }
                    
                    "long_press" -> {
                        val x = cmd.get("x")?.asInt ?: return@launch
                        val y = cmd.get("y")?.asInt ?: return@launch
                        val duration = cmd.get("duration")?.asLong ?: 1000L
                        touchInjector.longPress(x, y, duration)
                    }
                    
                    "pinch" -> {
                        val centerX = cmd.get("center_x")?.asInt ?: return@launch
                        val centerY = cmd.get("center_y")?.asInt ?: return@launch
                        val startDist = cmd.get("start_distance")?.asInt ?: return@launch
                        val endDist = cmd.get("end_distance")?.asInt ?: return@launch
                        val duration = cmd.get("duration")?.asLong ?: 500L
                        touchInjector.pinch(centerX, centerY, startDist, endDist, duration)
                    }
                    
                    else -> Timber.w("Unknown touch action: $action")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling touch command")
            }
        }
    }
    
    /**
     * Handle keyboard commands
     */
    private fun handleKeyCommand(cmd: JsonObject) {
        val action = cmd.get("action")?.asString
        
        when (action) {
            "text" -> {
                val text = cmd.get("text")?.asString ?: return
                keyInjector.sendText(text)
            }
            
            "press" -> {
                val keycodeName = cmd.get("keycode")?.asString ?: return
                val keycode = keyInjector.parseKeyCode(keycodeName)
                keyInjector.sendKeyCode(keycode)
            }
            
            "combination" -> {
                val keysArray = cmd.getAsJsonArray("keys") ?: return
                val keycodes = keysArray.map { 
                    keyInjector.parseKeyCode(it.asString) 
                }.toIntArray()
                keyInjector.sendKeyCombination(*keycodes)
            }
            
            else -> Timber.w("Unknown key action: $action")
        }
    }
    
    /**
     * Handle system commands
     */
    private fun handleSystemCommand(cmd: JsonObject) {
        val action = cmd.get("action")?.asString
        
        scope.launch(Dispatchers.IO) {
            val service = com.arcs.client.accessibility.RemoteAccessibilityService.getInstance()
            if (service == null) {
                Timber.e("AccessibilityService not available")
                return@launch
            }
            
            when (action) {
                "home" -> service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                )
                "back" -> service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                )
                "recents" -> service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                )
                "notifications" -> service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                )
                "quick_settings" -> service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                )
                "lock" -> service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                )
                else -> Timber.w("Unknown system action: $action")
            }
        }
    }
    
    /**
     * Handle app control commands
     */
    private fun handleAppControl(cmd: JsonObject) {
        val action = cmd.get("action")?.asString
        Timber.i("App control: $action")
        // TODO: Implement app launching/stopping
    }
    
    /**
     * Handle macro commands
     */
    private fun handleMacroCommand(cmd: JsonObject) {
        val action = cmd.get("action")?.asString
        Timber.i("Macro command: $action")
        // TODO: Implement in automation module
    }
    
    /**
     * Handle AI commands
     */
    private fun handleAICommand(cmd: JsonObject) {
        val action = cmd.get("action")?.asString
        Timber.i("AI command: $action")
        // TODO: Implement in AI module
    }
    
    /**
     * Handle ping
     */
    private fun handlePing() {
        // Respond with pong (handled by service)
        Timber.d("Ping received")
    }
}
