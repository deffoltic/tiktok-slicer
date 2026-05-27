package com.example.tiktokslicer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.tiktokslicer.theme.TikTokCyan
import com.example.tiktokslicer.theme.TikTokRed
import com.example.tiktokslicer.theme.TikTokSlicerTheme
import com.example.tiktokslicer.ui.GalleryScreen
import com.example.tiktokslicer.ui.HomeScreen

enum class Tab { Slice, Gallery }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TikTokSlicerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun AppScreen() {
    var currentTab by remember { mutableStateOf(Tab.Slice) }

    // Request permissions on startup
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle result (e.g. show warning if denied)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = currentTab == Tab.Slice,
                    onClick = { currentTab = Tab.Slice },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Slice") },
                    label = { Text("Slice") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TikTokRed,
                        selectedTextColor = TikTokRed,
                        indicatorColor = TikTokRed.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Gallery,
                    onClick = { currentTab = Tab.Gallery },
                    icon = { Icon(Icons.Default.List, contentDescription = "Gallery") },
                    label = { Text("Gallery") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TikTokCyan,
                        selectedTextColor = TikTokCyan,
                        indicatorColor = TikTokCyan.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            Tab.Slice -> HomeScreen(modifier = Modifier.padding(innerPadding))
            Tab.Gallery -> GalleryScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
