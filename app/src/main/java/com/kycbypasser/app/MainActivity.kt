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
import com.kycbypasser.app.ui.screen.DashboardScreen
import com.kycbypasser.app.ui.screen.TargetsScreen
import com.kycbypasser.app.ui.screen.MediaScreen
import com.kycbypasser.app.ui.screen.SettingsScreen

/**
 * Main activity with bottom navigation between the four main screens.
 */
class MainActivity : ComponentActivity() {

    private enum class Screen {
        DASHBOARD,
        TARGETS,
        MEDIA,
        SETTINGS
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

        setContent {
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
                                        Screen.SETTINGS -> "Settings"
                                    }
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                                icon = { Icon(Icons.Default.Devices, "Targets") },
                                label = { Text("Targets") },
                                selected = currentScreen == Screen.TARGETS,
                                onClick = { currentScreen = Screen.TARGETS }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Image, "Media") },
                                label = { Text("Media") },
                                selected = currentScreen == Screen.MEDIA,
                                onClick = { currentScreen = Screen.MEDIA }
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
                            Screen.DASHBOARD -> DashboardScreen()
                            Screen.TARGETS -> TargetsScreen()
                            Screen.MEDIA -> MediaScreen()
                            Screen.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
