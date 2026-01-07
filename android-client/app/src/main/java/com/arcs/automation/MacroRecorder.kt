package com.arcs.client.automation

import timber.log.Timber
import java.util.UUID

/**
 * Records user actions into macro sequences
 */
class MacroRecorder {
    
    private var isRecording = false
    private var recordingStartTime = 0L
    private var lastStepTime = 0L
    private val recordedSteps = mutableListOf<MacroStep>()
    private var currentMacroName = ""
    
    /**
     * Start recording macro
     */
    fun startRecording(macroName: String) {
        if (isRecording) {
            Timber.w("Already recording")
            return
        }
        
        currentMacroName = macroName
        recordedSteps.clear()
        recordingStartTime = System.currentTimeMillis()
        lastStepTime = recordingStartTime
        isRecording = true
        
        Timber.i("Started recording macro: $macroName")
    }
    
    /**
     * Stop recording and return macro
     */
    fun stopRecording(): Macro? {
        if (!isRecording) {
            Timber.w("Not recording")
            return null
        }
        
        isRecording = false
        val totalDuration = System.currentTimeMillis() - recordingStartTime
        
        val macro = Macro(
            id = UUID.randomUUID().toString(),
            name = currentMacroName,
            description = "",
            steps = recordedSteps.toList(),
            createdAt = recordingStartTime,
            totalDuration = totalDuration
        )
        
        Timber.i("Stopped recording macro: $currentMacroName (${recordedSteps.size} steps, ${totalDuration}ms)")
        return macro
    }
    
    /**
     * Record touch action
     */
    fun recordTouch(action: String, x: Int, y: Int, duration: Long? = null) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        val delay = now - lastStepTime
        
        val parameters = mutableMapOf(
            "action" to action,
            "x" to x.toString(),
            "y" to y.toString()
        )
        
        if (duration != null) {
            parameters["duration"] = duration.toString()
        }
        
        val step = MacroStep(
            type = StepType.TOUCH,
            action = action,
            parameters = parameters,
            delay = delay
        )
        
        recordedSteps.add(step)
        lastStepTime = now
        
        Timber.d("Recorded touch: $action ($x, $y)")
    }
    
    /**
     * Record swipe action
     */
    fun recordSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        val delay = now - lastStepTime
        
        val parameters = mapOf(
            "action" to "swipe",
            "start_x" to startX.toString(),
            "start_y" to startY.toString(),
            "end_x" to endX.toString(),
            "end_y" to endY.toString(),
            "duration" to duration.toString()
        )
        
        val step = MacroStep(
            type = StepType.TOUCH,
            action = "swipe",
            parameters = parameters,
            delay = delay
        )
        
        recordedSteps.add(step)
        lastStepTime = now
        
        Timber.d("Recorded swipe: ($startX,$startY) -> ($endX,$endY)")
    }
    
    /**
     * Record key action
     */
    fun recordKey(action: String, text: String? = null, keycode: String? = null) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        val delay = now - lastStepTime
        
        val parameters = mutableMapOf("action" to action)
        text?.let { parameters["text"] = it }
        keycode?.let { parameters["keycode"] = it }
        
        val step = MacroStep(
            type = StepType.KEY,
            action = action,
            parameters = parameters,
            delay = delay
        )
        
        recordedSteps.add(step)
        lastStepTime = now
        
        Timber.d("Recorded key: $action")
    }
    
    /**
     * Record system action
     */
    fun recordSystem(action: String) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        val delay = now - lastStepTime
        
        val step = MacroStep(
            type = StepType.SYSTEM,
            action = action,
            parameters = mapOf("action" to action),
            delay = delay
        )
        
        recordedSteps.add(step)
        lastStepTime = now
        
        Timber.d("Recorded system: $action")
    }
    
    /**
     * Record wait/delay
     */
    fun recordWait(duration: Long) {
        if (!isRecording) return
        
        val step = MacroStep(
            type = StepType.WAIT,
            action = "wait",
            parameters = mapOf("duration" to duration.toString()),
            delay = 0
        )
        
        recordedSteps.add(step)
        
        Timber.d("Recorded wait: ${duration}ms")
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording() = isRecording
    
    /**
     * Get current step count
     */
    fun getStepCount() = recordedSteps.size
}
