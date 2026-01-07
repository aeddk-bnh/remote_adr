package com.arcs.client.automation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.File

/**
 * Persistent storage for macros
 */
class MacroStorage(private val context: Context) {
    
    private val gson = Gson()
    private val macrosDir = File(context.filesDir, "macros")
    
    init {
        if (!macrosDir.exists()) {
            macrosDir.mkdirs()
        }
    }
    
    /**
     * Save macro to storage
     */
    fun saveMacro(macro: Macro): Boolean {
        return try {
            val file = File(macrosDir, "${macro.id}.json")
            val json = gson.toJson(macro)
            file.writeText(json)
            Timber.i("Saved macro: ${macro.name}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save macro")
            false
        }
    }
    
    /**
     * Load macro by ID
     */
    fun loadMacro(id: String): Macro? {
        return try {
            val file = File(macrosDir, "$id.json")
            if (!file.exists()) {
                return null
            }
            
            val json = file.readText()
            gson.fromJson(json, Macro::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load macro: $id")
            null
        }
    }
    
    /**
     * Load all macros
     */
    fun loadAllMacros(): List<Macro> {
        return try {
            macrosDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        val json = file.readText()
                        gson.fromJson(json, Macro::class.java)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load macro file: ${file.name}")
                        null
                    }
                }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load macros")
            emptyList()
        }
    }
    
    /**
     * Delete macro
     */
    fun deleteMacro(id: String): Boolean {
        return try {
            val file = File(macrosDir, "$id.json")
            val deleted = file.delete()
            if (deleted) {
                Timber.i("Deleted macro: $id")
            }
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete macro: $id")
            false
        }
    }
    
    /**
     * Export macro to JSON string
     */
    fun exportMacro(macro: Macro): String {
        return gson.toJson(macro)
    }
    
    /**
     * Import macro from JSON string
     */
    fun importMacro(json: String): Macro? {
        return try {
            gson.fromJson(json, Macro::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import macro")
            null
        }
    }
    
    /**
     * Get macro count
     */
    fun getMacroCount(): Int {
        return macrosDir.listFiles()?.count { it.extension == "json" } ?: 0
    }
}
