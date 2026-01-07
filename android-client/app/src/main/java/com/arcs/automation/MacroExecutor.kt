package com.arcs.client.automation

import com.arcs.client.input.KeyInjector
import com.arcs.client.input.TouchInjector
import com.arcs.client.accessibility.RemoteAccessibilityService
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Executes recorded macros with support for conditions and loops
 */
class MacroExecutor(
    private val touchInjector: TouchInjector,
    private val keyInjector: KeyInjector,
    private val scope: CoroutineScope
) {
    
    private var isExecuting = false
    private var currentJob: Job? = null
    
    sealed class ExecutionResult {
        object Success : ExecutionResult()
        data class Failed(val error: String, val step: Int) : ExecutionResult()
        object Cancelled : ExecutionResult()
    }
    
    /**
     * Execute macro
     * @param macro Macro to execute
     * @param loop If true, repeat indefinitely
     * @param speed Playback speed multiplier (1.0 = normal)
     * @param onProgress Callback for progress updates
     */
    fun execute(
        macro: Macro,
        loop: Boolean = false,
        speed: Float = 1.0f,
        onProgress: (step: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: (ExecutionResult) -> Unit = {}
    ) {
        if (isExecuting) {
            Timber.w("Already executing macro")
            onComplete(ExecutionResult.Failed("Already executing", 0))
            return
        }
        
        isExecuting = true
        
        currentJob = scope.launch(Dispatchers.IO) {
            try {
                do {
                    val result = executeMacroOnce(macro, speed, onProgress)
                    
                    if (result !is ExecutionResult.Success) {
                        onComplete(result)
                        return@launch
                    }
                    
                } while (loop && isActive)
                
                onComplete(ExecutionResult.Success)
                
            } catch (e: CancellationException) {
                Timber.i("Macro execution cancelled")
                onComplete(ExecutionResult.Cancelled)
                
            } catch (e: Exception) {
                Timber.e(e, "Macro execution error")
                onComplete(ExecutionResult.Failed(e.message ?: "Unknown error", 0))
                
            } finally {
                isExecuting = false
            }
        }
    }
    
    /**
     * Execute macro once
     */
    private suspend fun executeMacroOnce(
        macro: Macro,
        speed: Float,
        onProgress: (step: Int, total: Int) -> Unit
    ): ExecutionResult {
        
        Timber.i("Executing macro: ${macro.name} (${macro.steps.size} steps)")
        
        macro.steps.forEachIndexed { index, step ->
            if (!isExecuting) {
                return ExecutionResult.Cancelled
            }
            
            onProgress(index + 1, macro.steps.size)
            
            // Check condition if present
            if (step.condition != null) {
                val conditionMet = checkCondition(step.condition)
                
                when (step.condition.action) {
                    ConditionAction.SKIP -> if (conditionMet) {
                        // Skip this step
                        delay((step.delay / speed).toLong())
                    }
                    ConditionAction.STOP -> if (conditionMet) {
                        return ExecutionResult.Success
                    }
                    ConditionAction.RETRY -> if (!conditionMet) {
                        // Retry current step
                        delay((step.delay / speed).toLong())
                        return executeMacroOnce(macro, speed, onProgress)
                    }
                    else -> {}
                }
            }
            
            // Apply delay
            if (step.delay > 0) {
                delay((step.delay / speed).toLong())
            }
            
            // Execute step
            val success = executeStep(step)
            
            if (!success) {
                return ExecutionResult.Failed("Step execution failed", index)
            }
        }
        
        return ExecutionResult.Success
    }
    
    /**
     * Execute single step
     */
    private suspend fun executeStep(step: MacroStep): Boolean {
        return try {
            when (step.type) {
                StepType.TOUCH -> executeTouchStep(step)
                StepType.KEY -> executeKeyStep(step)
                StepType.SYSTEM -> executeSystemStep(step)
                StepType.WAIT -> executeWaitStep(step)
                StepType.CONDITION -> true  // Handled separately
            }
        } catch (e: Exception) {
            Timber.e(e, "Error executing step: ${step.action}")
            false
        }
    }
    
    /**
     * Execute touch step
     */
    private suspend fun executeTouchStep(step: MacroStep): Boolean {
        val action = step.parameters["action"] ?: return false
        
        return when (action) {
            "tap" -> {
                val x = step.parameters["x"]?.toIntOrNull() ?: return false
                val y = step.parameters["y"]?.toIntOrNull() ?: return false
                touchInjector.tap(x, y)
            }
            
            "swipe" -> {
                val startX = step.parameters["start_x"]?.toIntOrNull() ?: return false
                val startY = step.parameters["start_y"]?.toIntOrNull() ?: return false
                val endX = step.parameters["end_x"]?.toIntOrNull() ?: return false
                val endY = step.parameters["end_y"]?.toIntOrNull() ?: return false
                val duration = step.parameters["duration"]?.toLongOrNull() ?: 300L
                touchInjector.swipe(startX, startY, endX, endY, duration)
            }
            
            "long_press" -> {
                val x = step.parameters["x"]?.toIntOrNull() ?: return false
                val y = step.parameters["y"]?.toIntOrNull() ?: return false
                val duration = step.parameters["duration"]?.toLongOrNull() ?: 1000L
                touchInjector.longPress(x, y, duration)
            }
            
            else -> {
                Timber.w("Unknown touch action: $action")
                false
            }
        }
    }
    
    /**
     * Execute key step
     */
    private suspend fun executeKeyStep(step: MacroStep): Boolean {
        val action = step.parameters["action"] ?: return false
        
        return when (action) {
            "text" -> {
                val text = step.parameters["text"] ?: return false
                keyInjector.sendText(text)
            }
            
            "press" -> {
                val keycode = step.parameters["keycode"] ?: return false
                val code = keyInjector.parseKeyCode(keycode)
                keyInjector.sendKeyCode(code)
            }
            
            else -> {
                Timber.w("Unknown key action: $action")
                false
            }
        }
    }
    
    /**
     * Execute system step
     */
    private suspend fun executeSystemStep(step: MacroStep): Boolean {
        val action = step.parameters["action"] ?: return false
        val service = com.arcs.client.accessibility.RemoteAccessibilityService.getInstance() ?: return false
        
        val globalAction = when (action) {
            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return false
        }
        
        return service.doGlobalAction(globalAction)
    }
    
    /**
     * Execute wait step
     */
    private suspend fun executeWaitStep(step: MacroStep): Boolean {
        val duration = step.parameters["duration"]?.toLongOrNull() ?: return false
        delay(duration)
        return true
    }
    
    /**
     * Check if condition is met
     */
    private suspend fun checkCondition(condition: MacroCondition): Boolean {
        return when (condition.type) {
            ConditionType.ALWAYS -> true
            ConditionType.TIMEOUT -> {
                val duration = condition.parameters["duration"]?.toLongOrNull() ?: 0L
                delay(duration)
                true
            }
            ConditionType.OCR_MATCH -> {
                // TODO: Implement OCR check
                Timber.w("OCR condition not yet implemented")
                true
            }
            ConditionType.UI_ELEMENT -> {
                // TODO: Implement UI element check
                Timber.w("UI element condition not yet implemented")
                true
            }
        }
    }
    
    /**
     * Stop macro execution
     */
    fun stop() {
        currentJob?.cancel()
        isExecuting = false
        Timber.i("Macro execution stopped")
    }
    
    /**
     * Check if currently executing
     */
    fun isExecuting() = isExecuting
}
