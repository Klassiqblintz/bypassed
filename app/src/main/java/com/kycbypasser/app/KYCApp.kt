package com.kycbypasser.app

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Main application class.
 * Initializes shared state and manages the module config directory.
 */
class KYCApp : Application() {

    companion object {
        // Path where the Zygisk module stores its config files
        const val MODULE_DIR = "/data/adb/modules/kyc_bypasser"
        const val CONFIG_FILE = "$MODULE_DIR/config.json"
        const val TARGETS_FILE = "$MODULE_DIR/targets.txt"

        // Singleton for context
        lateinit var instance: KYCApp

        fun configDir(): String = MODULE_DIR
        fun configPath(): String = CONFIG_FILE
        fun targetsPath(): String = TARGETS_FILE
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Ensure module config directory exists
        ensureModuleDir()
    }

    /**
     * Create the module directory and default config files if they don't exist.
     * This runs when the app first launches.
     */
    private fun ensureModuleDir() {
        val dir = File(MODULE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Create default config if it doesn't exist
        val configFile = File(CONFIG_FILE)
        if (!configFile.exists()) {
            val defaultConfig = """
                {
                    "active": false,
                    "mode": "static",
                    "image_path": "",
                    "video_path": "",
                    "hide_module": true,
                    "auto_liveness": true,
                    "fps": 30
                }
            """.trimIndent()
            configFile.writeText(defaultConfig)
        }

        // Create default targets file if it doesn't exist
        val targetsFile = File(TARGETS_FILE)
        if (!targetsFile.exists()) {
            targetsFile.writeText("")
        }
    }
}
