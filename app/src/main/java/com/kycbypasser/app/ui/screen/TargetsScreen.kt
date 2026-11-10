package com.kycbypasser.app.ui.screen

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * Targets screen - select which apps to inject into.
 * Lists all installed apps with checkboxes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(
    packageManager: PackageManager = androidx.compose.ui.platform.LocalContext.current.packageManager
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Get all installed apps that have camera permission
    val allApps = remember {
        packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { pkg ->
                val hasCamera = pkg.requestedPermissions?.any { it == "android.permission.CAMERA" } == true
                val hasActivity = pkg.activities?.isNotEmpty() == true
                hasCamera && hasActivity
            }
            .map { pkg ->
                AppInfo(
                    packageName = pkg.packageName,
                    appName = pkg.applicationInfo?.loadLabel(packageManager)?.toString() ?: pkg.packageName,
                    icon = pkg.applicationInfo?.loadIcon(packageManager)
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    val filteredApps = allApps.filter { app ->
        app.appName.lowercase().contains(searchQuery.lowercase()) ||
        app.packageName.lowercase().contains(searchQuery.lowercase())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SELECTED: ${selectedPackages.size}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            if (selectedPackages.isNotEmpty()) {
                TextButton(onClick = { selectedPackages = emptySet() }) {
                    Text("CLEAR ALL")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // App list
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No apps match your search"
                          else "No camera apps found",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredApps) { app ->
                    AppItem(
                        app = app,
                        isSelected = selectedPackages.contains(app.packageName),
                        onToggle = {
                            if (selectedPackages.contains(app.packageName)) {
                                selectedPackages = selectedPackages - app.packageName
                            } else {
                                selectedPackages = selectedPackages + app.packageName
                            }
                        }
                    )
                }
            }
        }

        // Save button at bottom
        if (selectedPackages.isNotEmpty()) {
            Button(
                onClick = {
                    saveTargets(selectedPackages)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE TARGETS")
            }
        }
    }
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?
)

@Composable
fun AppItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon placeholder (would use actual icon in real impl)
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isSelected) Color(0xFF1B5E20) else CardBackground,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

            Switch(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

/**
 * Save selected target packages to the module's targets.txt file.
 */
fun saveTargets(packages: Set<String>) {
    val targetsPath = com.kycbypasser.app.KYCApp.targetsPath()
    val content = packages.joinToString("\n")
    android.util.Log.d("Targets", "Saving targets: $content")
    // In real impl: write to file via File API or IPC to companion process
}
