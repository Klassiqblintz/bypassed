package com.kycbypasser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.kycbypasser.app.ui.screen.DashboardScreen
import com.kycbypasser.app.ui.screen.TargetsScreen
import com.kycbypasser.app.ui.screen.MediaScreen
import com.kycbypasser.app.ui.screen.SettingsScreen
import com.kycbypasser.app.ui.screen.DocumentGeneratorScreen
import com.kycbypasser.app.ui.theme.CardBackground
import com.kycbypasser.app.ui.theme.CyanAccent

/**
 * Main activity with bottom navigation between the main screens.
 * Creates shared KycState that all screens read/write.
 */
class MainActivity : ComponentActivity() {

    private enum class Screen {
        DASHBOARD,
        TARGETS,
        MEDIA,
        DOCUMENT_GENERATOR,  // NEW: Document generator from top KYC bypassers
        SETTINGS
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create shared state that persists across all screens
        val state = KycState(this)

        setContent {
            var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (currentScreen) {
                                        Screen.DASHBOARD -> "Dashboard"
                                        Screen.TARGETS -> "Target Apps"
                                        Screen.MEDIA -> "Media"
                                        Screen.DOCUMENT_GENERATOR -> "Documents"
                                        Screen.SETTINGS -> "Settings"
                                    }
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CardBackground,
                                titleContentColor = CyanAccent
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, "Dashboard") },
                                label = { Text("Dashboard") },
                                selected = currentScreen == Screen.DASHBOARD,
                                onClick = { currentScreen = Screen.DASHBOARD }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, "Targets") },
                                label = { Text("Targets") },
                                selected = currentScreen == Screen.TARGETS,
                                onClick = { currentScreen = Screen.TARGETS }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.PlayArrow, "Media") },
                                label = { Text("Media") },
                                selected = currentScreen == Screen.MEDIA,
                                onClick = { currentScreen = Screen.MEDIA }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Description, "Documents") },
                                label = { Text("Documents") },
                                selected = currentScreen == Screen.DOCUMENT_GENERATOR,
                                onClick = { currentScreen = Screen.DOCUMENT_GENERATOR }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, "Settings") },
                                label = { Text("Settings") },
                                selected = currentScreen == Screen.SETTINGS,
                                onClick = { currentScreen = Screen.SETTINGS }
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        when (currentScreen) {
                            Screen.DASHBOARD -> DashboardScreen(state)
                            Screen.TARGETS -> TargetsScreen(state)
                            Screen.MEDIA -> MediaScreen(state)
                            Screen.DOCUMENT_GENERATOR -> DocumentGeneratorScreen(state)
                            Screen.SETTINGS -> SettingsScreen(state)
                        }
                    }
                }
            }
        }
    }
}
