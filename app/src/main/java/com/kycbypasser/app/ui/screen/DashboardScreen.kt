package com.kycbypasser.app.ui.screen

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.kycbypasser.app.KycState
import com.kycbypasser.app.service.VirtualCameraService
import com.kycbypasser.app.ui.theme.CyanAccent
import com.kycbypasser.app.ui.theme.CardBackground
import com.kycbypasser.app.ui.theme.SuccessGreen
import com.kycbypasser.app.ui.theme.WarningOrange

/**
 * Dashboard screen - shows overall status and quick actions.
 * Reads/writes shared KycState so selections persist across screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: KycState
) {
    var showPermissionDialog by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
        Settings.canDrawOverlays(state.appContext)
    ) }

    // Re-check permission when dialog is dismissed
    if (showPermissionDialog) {
        LaunchedEffect(Unit) {
            hasOverlayPermission = Settings.canDrawOverlays(state.appContext)
            if (hasOverlayPermission) {
                showPermissionDialog = false
            } else {
                // Stay open until granted
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "INJECTION STATUS",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (state.isActive) SuccessGreen else WarningOrange,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isActive) "ACTIVE" else "INACTIVE",
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (state.isActive) SuccessGreen else WarningOrange
                    )
                }

                // Overlay permission status
                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Overlay permission REQUIRED",
                            fontSize = 11.sp,
                            color = WarningOrange,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }

                // Toggle button
                Button(
                    onClick = {
                        if (!hasOverlayPermission) {
                            // Launch overlay permission settings
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${state.appContext.packageName}")
                            )
                            state.appContext.startActivity(intent)
                            showPermissionDialog = true
                        } else {
                            state.toggleActive()
                            val intent = android.content.Intent(state.appContext, VirtualCameraService::class.java)
                            if (state.isActive) {
                                intent.action = VirtualCameraService.CMD_START
                            } else {
                                intent.action = VirtualCameraService.CMD_STOP
                            }
                            state.startService(intent)
                            android.util.Log.d("Dashboard", "Virtual camera ${if (state.isActive) "started" else "stopped"}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isActive) Color(0xFF1B5E20) else Color(0xFFE65100)
                    )
                ) {
                    Icon(
                        imageVector = if (state.isActive) Icons.Default.CheckCircle else Icons.Default.Clear,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isActive) "DEACTIVATE" else "ACTIVATE")
                }
            }
        }

        // Stats cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                icon = Icons.Default.PlayArrow,
                title = "Targets",
                value = state.selectedTargetPackages.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.CheckCircle,
                title = "Image",
                value = if (state.selectedImagePath != null) "SET" else "NONE",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                icon = Icons.Default.PlayArrow,
                title = "Video",
                value = if (state.selectedVideoPath != null) "SET" else "NONE",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Settings,
                title = "Mode",
                value = if (state.injectionMode == "video") "VIDEO LOOP" else "STATIC",
                modifier = Modifier.weight(1f)
            )
        }

        // Quick actions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "QUICK ACTIONS",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { /* Navigation handled by parent */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MANAGE TARGETS")
                }

                OutlinedButton(
                    onClick = { /* Navigation handled by parent */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MANAGE MEDIA")
                }
            }
        }

        // Info about how it works
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HOW IT WORKS",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Select target app(s) that use camera for KYC\n" +
                           "2. Choose an image or video to inject\n" +
                           "3. Tap ACTIVATE — grants overlay permission if needed\n" +
                           "4. Open the target app — your media replaces the camera preview\n" +
                           "5. The target app's camera captures your overlay (screen)\n\n" +
                           "WORKS WITH: Front camera KYC apps (most common)\n" +
                           "REQUIRES: SYSTEM_ALERT_WINDOW (overlay) permission",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Tip card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TIPS",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "- Use a BRIGHT, CLEAR image for best results\n" +
                           "- For video mode, the first frame is used\n" +
                           "- Hold the phone at a normal distance from your face\n" +
                           "- Make sure your image fills the screen\n" +
                           "- Works best with front camera KYC apps",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
