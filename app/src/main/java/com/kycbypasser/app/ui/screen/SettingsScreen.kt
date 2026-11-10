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
import com.kycbypasser.app.KycState
import com.kycbypasser.app.ui.theme.CardBackground

/**
 * Settings screen - configure injection behavior.
 * Uses KycState for all values so changes persist across screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: KycState
) {
    // All state values come from the shared KycState — no local remember variables
    var hideModule by remember { mutableStateOf(state.hideModule) }
    var autoLiveness by remember { mutableStateOf(state.autoLiveness) }
    var frameRate by remember { mutableStateOf(state.fps) }
    var livenessMode by remember { mutableStateOf(state.injectionMode) }

    // Reload from state each time screen is recomposed (handles navigation back)
    LaunchedEffect(Unit) {
        hideModule = state.hideModule
        autoLiveness = state.autoLiveness
        frameRate = state.fps
        livenessMode = state.injectionMode
    }

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

                // Hide module
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hide from root/checkers",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Switch(
                        checked = hideModule,
                        onCheckedChange = { hideModule = it }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Auto liveness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto liveness detection",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Switch(
                        checked = autoLiveness,
                        onCheckedChange = { autoLiveness = it }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Frame rate
                Text(
                    text = "Frame rate (FPS)",
                    fontSize = 14.sp,
                    color = Color.White
                )
                OutlinedTextField(
                    value = frameRate,
                    onValueChange = { frameRate = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Liveness mode
                Text(
                    text = "Liveness mode",
                    fontSize = 14.sp,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = livenessMode == "static",
                        onClick = { livenessMode = "static" },
                        label = { Text("Static Image") },
                        leadingIcon = {
                            if (livenessMode == "static") Icon(Icons.Default.Check, null)
                        }
                    )
                    FilterChip(
                        selected = livenessMode == "video",
                        onClick = { livenessMode = "video" },
                        label = { Text("Video Loop") },
                        leadingIcon = {
                            if (livenessMode == "video") Icon(Icons.Default.Check, null)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Save settings — actually saves everything to the shared state
                Button(
                    onClick = {
                        state.injectionMode = livenessMode
                        state.fps = frameRate
                        state.hideModule = hideModule
                        state.autoLiveness = autoLiveness
                        state.saveAllState()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SAVE SETTINGS")
                }
            }
        }

        // About section
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
                    text = "KYC Bypasser works with or without root.\n" +
                           "With root (Zygisk): deep camera hooking\n" +
                           "Without root: virtual camera service\n\n" +
                           "No data wipe required for root installation.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
