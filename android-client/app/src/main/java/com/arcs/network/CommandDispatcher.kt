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
    private val scope: CoroutineScope,
    private val onCommandResult: ((String) -> Unit)? = null
) {
    
    private val gson = Gson()
    
    /**
     * Dispatch command
     */
    fun dispatch(json: String) {
        try {
            Timber.i("Dispatching command: %s", json.take(200))
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
                    Timber.w("Unknown command type: %s", type)
                    // send ack for unknown
                    val ack = mapOf(
                        "type" to "command_result",
                        "original_type" to type,
                        "success" to false,
                        "message" to "unknown_command"
                    )
                    onCommandResult?.invoke(gson.toJson(ack))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error dispatching command")
            val ack = mapOf(
                "type" to "command_result",
                "original_type" to "parsing_error",
                "success" to false,
                "message" to (e.message ?: "")
            )
            onCommandResult?.invoke(gson.toJson(ack))
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
                        val ok = touchInjector.tap(x, y)
                        val ack = mapOf(
                            "type" to "command_result",
                            "original_type" to "touch",
                            "action" to "tap",
                            "x" to x,
                            "y" to y,
                            "success" to ok
                        )
                        onCommandResult?.invoke(gson.toJson(ack))
                    }
                    
                    "swipe" -> {
                        val startX = cmd.get("start_x")?.asInt ?: return@launch
                        val startY = cmd.get("start_y")?.asInt ?: return@launch
                        val endX = cmd.get("end_x")?.asInt ?: return@launch
                        val endY = cmd.get("end_y")?.asInt ?: return@launch
                        val duration = cmd.get("duration")?.asLong ?: 300L
                        val ok = touchInjector.swipe(startX, startY, endX, endY, duration)
                        val ack = mapOf(
                            "type" to "command_result",
                            "original_type" to "touch",
                            "action" to "swipe",
                            "start_x" to startX,
                            "start_y" to startY,
                            "end_x" to endX,
                            "end_y" to endY,
                            "duration" to duration,
                            "success" to ok
                        )
                        onCommandResult?.invoke(gson.toJson(ack))
                    }
                    
                    "long_press" -> {
                        val x = cmd.get("x")?.asInt ?: return@launch
                        val y = cmd.get("y")?.asInt ?: return@launch
                        val duration = cmd.get("duration")?.asLong ?: 1000L
                        val ok = touchInjector.longPress(x, y, duration)
                        val ack = mapOf(
                            "type" to "command_result",
                            "original_type" to "touch",
                            "action" to "long_press",
                            "x" to x,
                            "y" to y,
                            "duration" to duration,
                            "success" to ok
                        )
                        onCommandResult?.invoke(gson.toJson(ack))
                    }
                    
                    "pinch" -> {
                        val centerX = cmd.get("center_x")?.asInt ?: return@launch
                        val centerY = cmd.get("center_y")?.asInt ?: return@launch
                        val startDist = cmd.get("start_distance")?.asInt ?: return@launch
                        val endDist = cmd.get("end_distance")?.asInt ?: return@launch
                        val duration = cmd.get("duration")?.asLong ?: 500L
                        val ok = touchInjector.pinch(centerX, centerY, startDist, endDist, duration)
                        val ack = mapOf(
                            "type" to "command_result",
                            "original_type" to "touch",
                            "action" to "pinch",
                            "center_x" to centerX,
                            "center_y" to centerY,
                            "start_distance" to startDist,
                            "end_distance" to endDist,
                            "duration" to duration,
                            "success" to ok
                        )
                        onCommandResult?.invoke(gson.toJson(ack))
                    }
                    
                    else -> Timber.w("Unknown touch action: $action")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling touch command")
                val ack = mapOf(
                    "type" to "command_result",
                    "original_type" to "touch",
                    "success" to false,
                    "message" to (e.message ?: "")
                )
                onCommandResult?.invoke(gson.toJson(ack))
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
                val ok = keyInjector.sendText(text)
                val ack = mapOf(
                    "type" to "command_result",
                    "original_type" to "key",
                    "action" to "text",
                    "text" to text,
                    "success" to ok
                )
                onCommandResult?.invoke(gson.toJson(ack))
            }
            
            "press" -> {
                val keycodeName = cmd.get("keycode")?.asString ?: return
                val keycode = keyInjector.parseKeyCode(keycodeName)
                val ok = keyInjector.sendKeyCode(keycode)
                val ack = mapOf(
                    "type" to "command_result",
                    "original_type" to "key",
                    "action" to "press",
                    "keycode" to keycodeName,
                    "success" to ok
                )
                onCommandResult?.invoke(gson.toJson(ack))
            }
            
            "combination" -> {
                val keysArray = cmd.getAsJsonArray("keys") ?: return
                val keycodes = keysArray.map { 
                    keyInjector.parseKeyCode(it.asString) 
                }.toIntArray()
                val ok = keyInjector.sendKeyCombination(*keycodes)
                val ack = mapOf(
                    "type" to "command_result",
                    "original_type" to "key",
                    "action" to "combination",
                    "keys" to keysArray.toString(),
                    "success" to ok
                )
                onCommandResult?.invoke(gson.toJson(ack))
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
                val ack = mapOf(
                    "type" to "command_result",
                    "original_type" to "system",
                    "action" to action,
                    "success" to false,
                    "message" to "accessibility_unavailable"
                )
                onCommandResult?.invoke(gson.toJson(ack))
                return@launch
            }

            try {
                var success = false
                when (action) {
                    "home" -> success = service.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                    )
                    "back" -> success = service.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                    )
                    "recents" -> success = service.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                    )
                    "notifications" -> success = service.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                    )
                    "quick_settings" -> success = service.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                    )
                    "lock" -> success = service.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                    )
                    else -> {
                        Timber.w("Unknown system action: %s", action)
                    }
                }

                val ack = mapOf(
                    "type" to "command_result",
                    "original_type" to "system",
                    "action" to action,
                    "success" to success
                )
                onCommandResult?.invoke(gson.toJson(ack))
            } catch (e: Exception) {
                Timber.e(e, "Error performing system action")
                val ack = mapOf(
                    "type" to "command_result",
                    "original_type" to "system",
                    "action" to action,
                    "success" to false,
                    "message" to (e.message ?: "")
                )
                onCommandResult?.invoke(gson.toJson(ack))
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
