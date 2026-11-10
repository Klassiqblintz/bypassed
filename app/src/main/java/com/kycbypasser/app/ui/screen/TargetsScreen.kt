package com.kycbypasser.app.ui.screen

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.kycbypasser.app.KycState
import com.kycbypasser.app.ui.theme.CardBackground

/**
 * Targets screen - select which apps to inject into.
 * Uses shared KycState so selections persist across navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(
    state: KycState,
    packageManager: PackageManager = androidx.compose.ui.platform.LocalContext.current.packageManager
) {
    var searchQuery by remember { mutableStateOf("") }

    // Get all installed apps that have at least one activity
    val allApps = remember {
        packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            .filter { pkg -> pkg.activities?.isNotEmpty() == true }
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
                text = "SELECTED: ${state.selectedTargetPackages.size}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            if (state.selectedTargetPackages.isNotEmpty()) {
                TextButton(onClick = {
                    state.selectedTargetPackages = emptySet()
                    state.saveTargets()
                }) {
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
                          else "No apps found",
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
                        isSelected = state.selectedTargetPackages.contains(app.packageName),
                        onToggle = {
                            val current = state.selectedTargetPackages.toMutableSet()
                            if (current.contains(app.packageName)) {
                                current.remove(app.packageName)
                            } else {
                                current.add(app.packageName)
                            }
                            state.selectedTargetPackages = current
                        }
                    )
                }
            }
        }

        // Save button at bottom
        if (state.selectedTargetPackages.isNotEmpty()) {
            Button(
                onClick = {
                    state.saveTargets()
                    state.saveAllState()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Settings, null)
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
        // Display actual app icon
        if (app.icon != null) {
            val bitmap = remember(app.icon) {
                (app.icon as? android.graphics.drawable.BitmapDrawable)?.let { it.bitmap }
                    ?: run {
                        val drawable = app.icon
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            drawable.intrinsicWidth.takeIf { it > 0 } ?: 48,
                            drawable.intrinsicHeight.takeIf { it > 0 } ?: 48,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bitmap
                    }
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback if icon is null (very rare)
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
