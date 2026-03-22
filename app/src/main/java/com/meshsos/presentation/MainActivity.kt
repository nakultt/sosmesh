package com.meshsos.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.meshsos.background.MeshForegroundService
import com.meshsos.presentation.screens.MeshStatusScreen
import com.meshsos.presentation.screens.RelayLogScreen
import com.meshsos.presentation.screens.SosScreen
import com.meshsos.presentation.theme.MeshSosTheme
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.SubtleGray
import com.meshsos.presentation.viewmodels.MeshViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MeshViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            MeshForegroundService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            MeshSosTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Warning, contentDescription = "SOS") },
                                label = { Text("SOS") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MeshTeal,
                                    selectedTextColor = MeshTeal,
                                    unselectedIconColor = SubtleGray,
                                    unselectedTextColor = SubtleGray,
                                    indicatorColor = Color(0xFF2A2A2A)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.List, contentDescription = "Log") },
                                label = { Text("Log") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MeshTeal,
                                    selectedTextColor = MeshTeal,
                                    unselectedIconColor = SubtleGray,
                                    unselectedTextColor = SubtleGray,
                                    indicatorColor = Color(0xFF2A2A2A)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Info, contentDescription = "Status") },
                                label = { Text("Status") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MeshTeal,
                                    selectedTextColor = MeshTeal,
                                    unselectedIconColor = SubtleGray,
                                    unselectedTextColor = SubtleGray,
                                    indicatorColor = Color(0xFF2A2A2A)
                                )
                            )
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        0 -> SosScreen(viewModel = viewModel)
                        1 -> RelayLogScreen(viewModel = viewModel)
                        2 -> MeshStatusScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
