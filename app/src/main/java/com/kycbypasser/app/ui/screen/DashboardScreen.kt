package com.kycbypasser.app.ui.screen

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
import com.kycbypasser.app.KYCApp
import com.kycbypasser.app.ui.theme.CyanAccent
import com.kycbypasser.app.ui.theme.CardBackground
import com.kycbypasser.app.ui.theme.SuccessGreen
import com.kycbypasser.app.ui.theme.WarningOrange

/**
 * Dashboard screen - shows overall status and quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    var isActive by remember { mutableStateOf(false) }
    var targetCount by remember { mutableStateOf(0) }
    var hasImage by remember { mutableStateOf(false) }
    var hasVideo by remember { mutableStateOf(false) }

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
                                if (isActive) SuccessGreen else WarningOrange,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "ACTIVE" else "INACTIVE",
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (isActive) SuccessGreen else WarningOrange
                    )
                }

                // Toggle button
                Button(
                    onClick = {
                        isActive = !isActive
                        updateConfig("active", isActive)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) Color(0xFF1B5E20) else Color(0xFFE65100)
                    )
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isActive) "DEACTIVATE" else "ACTIVATE")
                }
            }
        }

        // Stats cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                icon = Icons.Default.Devices,
                title = "Targets",
                value = targetCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Image,
                title = "Image",
                value = if (hasImage) "SET" else "NONE",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                icon = Icons.Default.VideoLibrary,
                title = "Video",
                value = if (hasVideo) "SET" else "NONE",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Shield,
                title = "Mode",
                value = "STATIC",
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
                    onClick = { /* Navigate to targets */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Devices, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MANAGE TARGETS")
                }

                OutlinedButton(
                    onClick = { /* Navigate to media */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, null)
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
                           "3. Activate injection\n" +
                           "4. Open the target app - camera shows your media",
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

/**
 * Update the module config file.
 */
fun updateConfig(key: String, value: Any) {
    android.util.Log.d("Dashboard", "Update config: $key = $value")
}
