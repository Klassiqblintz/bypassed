package com.kycbypasser.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared state holder for all screens.
 * Persists selections across navigation and writes to config files.
 */
class KycState(private val context: android.content.Context) {
    val context get() = context

    // --- State ---
    var isActive by mutableStateOf(false)
    var selectedTargetPackages by mutableStateOf<Set<String>>(emptySet())
    var selectedImagePath by mutableStateOf<String?>(null)
    var selectedVideoPath by mutableStateOf<String?>(null)
    var injectionMode by mutableStateOf("static") // "static" or "video"
    var hideModule by mutableStateOf(true)
    var autoLiveness by mutableStateOf(true)
    var fps by mutableStateOf("30")

    // --- File paths ---
    private val configPath = KYCApp.CONFIG_FILE
    private val targetsPath = KYCApp.TARGETS_FILE

    // --- Load persisted state on init ---
    init {
        loadState()
    }

    // --- Load state from files ---
    private fun loadState() {
        try {
            val config = android.os.FileUtils.readFileToByteArray(
                java.io.File(configPath)
            )?.decodeToString()
            if (!config.isNullOrEmpty()) {
                isActive = parseConfigBool(config, "active", false)
                injectionMode = parseConfigString(config, "mode", "static")
                selectedImagePath = parseConfigString(config, "image_path", null)
                selectedVideoPath = parseConfigString(config, "video_path", null)
            }

            val targets = java.io.File(targetsPath).readText().trim()
            if (targets.isNotEmpty()) {
                selectedTargetPackages = targets.split("\n").filter { it.isNotBlank() }.toSet()
            }
        } catch (e: Exception) {
            Log.w("KycState", "Failed to load state: ${e.message}")
        }
    }

    // --- Save all state to files ---
    fun saveAllState() {
        Log.d("KycState", "Saving all state: active=$isActive, mode=$injectionMode, " +
            "targets=${selectedTargetPackages.size}, image=$selectedImagePath, video=$selectedVideoPath")
        saveConfig()
        saveTargets()
    }

    // --- Save config.json ---
    fun saveConfig() {
        try {
            val cfg = java.io.File(configPath)
            cfg.parentFile?.mkdirs()
            cfg.writeText(
                """{
  "active": $isActive,
  "mode": "$injectionMode",
  "image_path": "${selectedImagePath ?: ""}",
  "video_path": "${selectedVideoPath ?: ""}",
  "hide_module": $hideModule,
  "auto_liveness": $autoLiveness,
  "fps": ${fps.toIntOrNull() ?: 30}
}"""
            )
            Log.d("KycState", "Config saved successfully")
        } catch (e: Exception) {
            Log.e("KycState", "Failed to save config: ${e.message}")
        }
    }

    // --- Save targets.txt ---
    fun saveTargets() {
        try {
            val tgt = java.io.File(targetsPath)
            tgt.parentFile?.mkdirs()
            tgt.writeText(selectedTargetPackages.joinToString("\n"))
            Log.d("KycState", "Targets saved: ${selectedTargetPackages.size} packages")
        } catch (e: Exception) {
            Log.e("KycState", "Failed to save targets: ${e.message}")
        }
    }

    // --- Activation toggle ---
    fun toggleActive() {
        isActive = !isActive
        saveConfig()
    }

    // --- Image/video selection ---
    fun setSelectedImage(path: String?) {
        selectedImagePath = path
    }

    fun setSelectedVideo(path: String?) {
        selectedVideoPath = path
    }

    fun setMode(mode: String) {
        injectionMode = mode
    }

    // --- Parse helpers ---
    private fun parseConfigBool(config: String, key: String, default: Boolean): Boolean {
        val pattern = "\"$key\"\\s*:\\s*(true|false)"
        val match = Regex(pattern).find(config)
        return match?.groupValues?.get(1) == "true"
    }

    private fun parseConfigString(config: String, key: String, default: String?): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val match = Regex(pattern).find(config)
        return match?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: default
    }
}
