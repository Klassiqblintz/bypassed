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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kycbypasser.app.ui.theme.CardBackground

/**
 * Media screen - select images and videos for injection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)
@Composable
fun MediaScreen() {
    var selectedImagePath by remember { mutableStateOf<String?>(null) }
    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var currentMode by remember { mutableStateOf("static") } // "static" or "video"

    // Image picker launcher
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.path?.let { selectedImagePath = it }
    }

    // Video picker launcher
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.path?.let { selectedVideoPath = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Mode selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "INJECTION MODE",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                FilterChip(
                    selected = currentMode == "static",
                    onClick = { currentMode = "static" },
                    label = { Text("Static Image") },
                    leadingIcon = {
                        if (currentMode == "static") Icon(Icons.Default.Check, null)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilterChip(
                    selected = currentMode == "video",
                    onClick = { currentMode = "video" },
                    label = { Text("Video Loop") },
                    leadingIcon = {
                        if (currentMode == "video") Icon(Icons.Default.Check, null)
                    }
                )
            }
        }

        // Image section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INJECTED IMAGE",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    if (selectedImagePath != null) {
                        Text(
                            text = "SELECTED",
                            fontSize = 11.sp,
                            color = Color(0xFF00C853)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            if (selectedImagePath != null) Color(0xFF2E7D32)
                            else CardBackground.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImagePath != null) {
                        Text(
                            text = "IMAGE LOADED",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "NO IMAGE SELECTED",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { imageLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CHOOSE IMAGE")
                    }

                    if (selectedImagePath != null) {
                        OutlinedButton(
                            onClick = { selectedImagePath = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CLEAR")
                        }
                    }
                }
            }
        }

        // Video section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INJECTED VIDEO",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    if (selectedVideoPath != null) {
                        Text(
                            text = "SELECTED",
                            fontSize = 11.sp,
                            color = Color(0xFF00C853)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            if (selectedVideoPath != null) Color(0xFF1565C0)
                            else CardBackground.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedVideoPath != null) {
                        Text(
                            text = "VIDEO LOADED",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "NO VIDEO SELECTED",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { videoLauncher.launch("video/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CHOOSE VIDEO")
                    }

                    if (selectedVideoPath != null) {
                        OutlinedButton(
                            onClick = { selectedVideoPath = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CLEAR")
                        }
                    }
                }
            }
        }

        // Save button
        if (selectedImagePath != null || selectedVideoPath != null) {
            Button(
                onClick = {
                    saveMedia(selectedImagePath, selectedVideoPath)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE MEDIA")
            }
        }

        // Info about video format
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SUPPORTED FORMATS",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Images: JPEG, PNG, WebP\n" +
                           "Videos: MP4, MOV (H.264 encoded)\n" +
                           "Recommended: 720x1280, 30fps\n" +
                           "Max file size: 50MB",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Save selected media paths to the module config.
 */
fun saveMedia(imagePath: String?, videoPath: String?) {
    val configPath = com.kycbypasser.app.KYCApp.configPath()
    android.util.Log.d("Media", "Save media: image=$imagePath, video=$videoPath")
    // In real impl: write to config.json via IPC or direct file write
}
