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
import com.kycbypasser.app.KycState
import com.kycbypasser.app.ui.theme.CardBackground

/**
 * Media screen - select images and videos for injection.
 * Uses shared KycState so selections persist across navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    state: KycState
) {
    // Image picker launcher
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.path?.let {
            state.setSelectedImage(it)
            state.saveAllState()
        }
    }

    // Video picker launcher
    var currentMode by remember { mutableStateOf(if (state.injectionMode == "video") "video" else "static") }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.path?.let {
            state.setSelectedVideo(it)
            state.saveAllState()
        }
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
                    onClick = {
                        currentMode = "static"
                        state.setMode("static")
                        state.saveAllState()
                    },
                    label = { Text("Static Image") },
                    leadingIcon = {
                        if (currentMode == "static") Icon(Icons.Default.Check, null)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilterChip(
                    selected = currentMode == "video",
                    onClick = {
                        currentMode = "video"
                        state.setMode("video")
                        state.saveAllState()
                    },
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
                    if (state.selectedImagePath != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SELECTED",
                                fontSize = 11.sp,
                                color = Color(0xFF00C853)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            if (state.selectedImagePath != null) Color(0xFF2E7D32)
                            else CardBackground.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.selectedImagePath != null) {
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
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CHOOSE IMAGE")
                    }

                    if (state.selectedImagePath != null) {
                        OutlinedButton(
                            onClick = {
                                state.setSelectedImage(null)
                                state.saveAllState()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Clear, null)
                            Spacer(modifier = Modifier.width(4.dp))
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
                    if (state.selectedVideoPath != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SELECTED",
                                fontSize = 11.sp,
                                color = Color(0xFF00C853)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            if (state.selectedVideoPath != null) Color(0xFF1565C0)
                            else CardBackground.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.selectedVideoPath != null) {
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

                    if (state.selectedVideoPath != null) {
                        OutlinedButton(
                            onClick = {
                                state.setSelectedVideo(null)
                                state.saveAllState()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Clear, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CLEAR")
                        }
                    }
                }
            }
        }

        // Save button
        if (state.selectedImagePath != null || state.selectedVideoPath != null) {
            Button(
                onClick = {
                    state.saveAllState()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
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
