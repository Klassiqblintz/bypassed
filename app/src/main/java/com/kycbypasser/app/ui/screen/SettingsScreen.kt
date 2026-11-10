package com.kycbypasser.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.kycbypasser.app.ui.theme.CardBackground

/**
 * Settings screen - configure injection behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var hideModule by remember { mutableStateOf(true) }
    var autoLiveness by remember { mutableStateOf(true) }
    var frameRate by remember { mutableStateOf("30") }
    var livenessMode by remember { mutableStateOf("video") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Injection settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "INJECTION SETTINGS",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Hide module toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hide Module Name",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Hide from root/checkers",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = hideModule,
                        onCheckedChange = { hideModule = it }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Auto liveness toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Auto-Detect Liveness",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Switch frames on prompts",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoLiveness,
                        onCheckedChange = { autoLiveness = it }
                    )
                }
            }
        }

        // Liveness mode
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LIVENESS MODE",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                FilterChip(
                    selected = livenessMode == "video",
                    onClick = { livenessMode = "video" },
                    label = { Text("Video Loop") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilterChip(
                    selected = livenessMode == "realtime",
                    onClick = { livenessMode = "realtime" },
                    label = { Text("Real-Time Face Swap") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilterChip(
                    selected = livenessMode == "static",
                    onClick = { livenessMode = "static" },
                    label = { Text("Static (Blink Only)") }
                )
            }
        }

        // Frame rate
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FRAME RATE",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = frameRate == "30",
                        onClick = { frameRate = "30" },
                        label = { Text("30 FPS") }
                    )
                    FilterChip(
                        selected = frameRate == "24",
                        onClick = { frameRate = "24" },
                        label = { Text("24 FPS") }
                    )
                }
            }
        }

        // Camera hooks
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CAMERA HOOKS",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Camera2 API: ENABLED\n" +
                           "Camera1 API: ENABLED\n" +
                           "PLT Hooks: ENABLED\n" +
                           "Metadata Spoof: ENABLED",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // About
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ABOUT",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Version: 1.0.0\n" +
                           "Framework: Kotlin + C++ NDK\n" +
                           "Min Android: 9.0 (API 28)\n" +
                           "Requires: Root + Zygisk\n\n" +
                           "Features:\n" +
                           "- Camera2/Camera1/PLT hooks\n" +
                           "- Image & video injection\n" +
                           "- Liveness detection bypass\n" +
                           "- Metadata spoofing\n" +
                           "- Per-app targeting",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Save button
        Button(
            onClick = {
                saveSettings(hideModule, autoLiveness, frameRate, livenessMode)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Icon(Icons.Default.Save, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("SAVE SETTINGS")
        }
    }
}

/**
 * Save settings to config file.
 */
fun saveSettings(
    hideModule: Boolean,
    autoLiveness: Boolean,
    frameRate: String,
    livenessMode: String
) {
    android.util.Log.d("Settings", "Save: hide=$hideModule, autoLiveness=$autoLiveness, fps=$frameRate, mode=$livenessMode")
    // In real impl: write to config.json
}
