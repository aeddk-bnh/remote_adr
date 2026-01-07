package com.arcs.client.automation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Macro data model
 * Represents a recorded sequence of actions
 */
@Parcelize
data class Macro(
    val id: String,
    val name: String,
    val description: String = "",
    val steps: List<MacroStep>,
    val createdAt: Long,
    val totalDuration: Long
) : Parcelable

@Parcelize
data class MacroStep(
    val type: StepType,
    val action: String,
    val parameters: Map<String, String>,
    val delay: Long,  // Delay before this step in ms
    val condition: MacroCondition? = null
) : Parcelable

enum class StepType {
    TOUCH,
    KEY,
    SYSTEM,
    WAIT,
    CONDITION
}

@Parcelize
data class MacroCondition(
    val type: ConditionType,
    val parameters: Map<String, String>,
    val action: ConditionAction
) : Parcelable

enum class ConditionType {
    OCR_MATCH,      // Check if text is visible
    UI_ELEMENT,     // Check if UI element exists
    TIMEOUT,        // Wait for timeout
    ALWAYS          // Always execute
}

enum class ConditionAction {
    CONTINUE,       // Continue to next step
    SKIP,           // Skip next step
    STOP,           // Stop macro execution
    RETRY           // Retry current step
}
