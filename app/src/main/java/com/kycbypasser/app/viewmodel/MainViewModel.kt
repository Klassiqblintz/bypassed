package com.kycbypasser.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kycbypasser.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main ViewModel managing all app state.
 * Handles config file I/O, target app list, media selection,
 * and liveness mode settings.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── State ──
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _selectedTargets = MutableStateFlow<List<String>>(emptyList())
    val selectedTargets: StateFlow<List<String>> = _selectedTargets.asStateFlow()

    private val _injectedMediaPath = MutableStateFlow<String?>(null)
    val injectedMediaPath: StateFlow<String?> = _injectedMediaPath.asStateFlow()

    private val _livenessMode = MutableStateFlow(LivenessMode.STATIC)
    val livenessMode: StateFlow<LivenessMode> = _livenessMode.asStateFlow()

    private val _hideModule = MutableStateFlow(true)
    val hideModule: StateFlow<Boolean> = _hideModule.asStateFlow()

    private val _autoLiveness = MutableStateFlow(true)
    val autoLiveness: StateFlow<Boolean> = _autoLiveness.asStateFlow()

    private val _frameRate = MutableStateFlow(30)
    val frameRate: StateFlow<Int> = _frameRate.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // All available packages on the device
    private val _allPackages = MutableStateFlow<List<String>>(emptyList())
    val allPackages: StateFlow<List<String>> = _allPackages.asStateFlow()

    // ── Config File Paths ──
    private val moduleDir = File("/data/adb/modules/kyc_bypasser")
    private val configFile = File(moduleDir, "config.json")
    private val targetsFile = File(moduleDir, "targets.txt")

    // ── Liveness Mode Enum ──
    enum class LivenessMode {
        STATIC, VIDEO, REALTIME;

        fun toConfigString(): String = when (this) {
            STATIC -> "static"
            VIDEO -> "video"
            REALTIME -> "realtime"
        }

        companion object {
            fun fromConfigString(s: String): LivenessMode = when (s) {
                "static" -> STATIC
                "video" -> VIDEO
                "realtime" -> REALTIME
                else -> STATIC
            }
        }
    }

    init {
        loadAllState()
        scanAvailablePackages()
    }

    // ── Load All State from Config Files ──
    private fun loadAllState() {
        viewModelScope.launch {
            loadConfig()
            loadTargets()
            _isActive.value = configFile.exists() && isConfigActive()
        }
    }

    private fun isConfigActive(): Boolean {
        val text = configFile.readText().trim()
        return text.contains("\"active\": true")
    }

    private fun loadConfig() {
        if (!configFile.exists()) return
        val text = configFile.readText()

        if (text.contains("\"active\": true")) _isActive.value = true
        else _isActive.value = false

        // Parse mode
        val modeMatch = """\"mode\"\s*:\s*\"([^\"]+)""".toRegex().find(text)
        if (modeMatch != null) {
            _livenessMode.value = LivenessMode.fromConfigString(modeMatch.groupValues[1])
        }

        // Parse image_path
        val imgMatch = """\"image_path\"\s*:\s*\"([^\"]+)""".toRegex().find(text)
        if (imgMatch != null) _injectedMediaPath.value = imgMatch.groupValues[1]

        // Parse fps
        val fpsMatch = """\"fps\"\s*:\s*(\d+)""".toRegex().find(text)
        if (fpsMatch != null) _frameRate.value = fpsMatch.groupValues[1].toInt()
    }

    private fun loadTargets() {
        if (!targetsFile.exists()) return
        val targets = targetsFile.readText()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        _selectedTargets.value = targets
    }

    // ── Scan Available Packages ──
    private fun scanAvailablePackages() {
        viewModelScope.launch {
            val pm = getApplication<Context>().packageManager
            val packages = pm.getInstalledPackages(0)
                .map { it.packageName }
                .sorted()
            _allPackages.value = packages
        }
    }

    // ── Toggle Active Status ──
    fun toggleActive() {
        viewModelScope.launch {
            _isActive.value = !_isActive.value
            updateConfigFile("active", _isActive.value)
        }
    }

    // ── Target App Management ──
    fun addTarget(packageName: String) {
        viewModelScope.launch {
            val targets = _selectedTargets.value.toMutableList()
            if (!targets.contains(packageName)) {
                targets.add(packageName)
                _selectedTargets.value = targets
                saveTargetsToFile(targets)
            }
        }
    }

    fun removeTarget(packageName: String) {
        viewModelScope.launch {
            val targets = _selectedTargets.value.toMutableList()
            targets.remove(packageName)
            _selectedTargets.value = targets
            saveTargetsToFile(targets)
        }
    }

    fun isTarget(packageName: String): Boolean {
        return _selectedTargets.value.contains(packageName)
    }

    private fun saveTargetsToFile(targets: List<String>) {
        viewModelScope.launch {
            if (!moduleDir.exists()) moduleDir.mkdirs()
            targetsFile.writeText(targets.joinToString("\n"))
        }
    }

    // ── Media Selection ──
    fun selectMedia(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Context>()
            // Copy file to internal storage for reliability
            val outFile = File(ctx.cacheDir, "injected_media")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            _injectedMediaPath.value = outFile.absolutePath
            updateConfigFile("image_path", outFile.absolutePath)
        }
    }

    // ── Liveness Mode ──
    fun setLivenessMode(mode: LivenessMode) {
        viewModelScope.launch {
            _livenessMode.value = mode
            updateConfigFile("mode", mode.toConfigString())
        }
    }

    // ── Hide Module ──
    fun setHideModule(value: Boolean) {
        viewModelScope.launch {
            _hideModule.value = value
            updateConfigFile("hide_module", value)
        }
    }

    // ── Auto Liveness ──
    fun setAutoLiveness(value: Boolean) {
        viewModelScope.launch {
            _autoLiveness.value = value
            updateConfigFile("auto_liveness", value)
        }
    }

    // ── Frame Rate ──
    fun setFrameRate(fps: Int) {
        viewModelScope.launch {
            val clamped = fps.coerceIn(1, 60)
            _frameRate.value = clamped
            updateConfigFile("fps", clamped)
        }
    }

    // ── Search ──
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredPackages(): List<String> {
        val query = _searchQuery.value.lowercase()
        return _allPackages.value.filter { pkg ->
            pkg.lowercase().contains(query)
        }
    }

    // ── Update Config File ──
    private fun updateConfigFile(key: String, value: Any) {
        viewModelScope.launch {
            if (!moduleDir.exists()) moduleDir.mkdirs()

            val existingText = if (configFile.exists()) configFile.readText() else "{}"
            val updated = existingText.replace(
                """\"$key\"\s*:\s*"[^"]*"|\"$key\"\s*:\s*\d+|\"$key\"\s*:\s*true|\"$key\"\s*:\s*false""".toRegex(),
                "\"$key\": ${
                    when (value) {
                        is String -> "\"$value\""
                        is Boolean -> value.toString()
                        is Int -> value.toString()
                        else -> "\"$value\""
                    }
                }"
            )
            configFile.writeText(updated)
        }
    }
}
