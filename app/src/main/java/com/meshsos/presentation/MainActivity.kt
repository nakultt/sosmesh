package com.meshsos.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.meshsos.presentation.screens.MeshStatusScreen
import com.meshsos.presentation.screens.SettingsScreen
import com.meshsos.presentation.screens.SosScreen
import com.meshsos.presentation.theme.MeshSosTheme
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.ThemePreferences
import com.meshsos.presentation.viewmodels.MeshViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MeshViewModel by viewModels()

    @Inject
    lateinit var themePreferences: ThemePreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            Log.w(TAG, "Permissions denied: ${denied.joinToString()}")
        }
        viewModel.refreshService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsState(initial = false)

            // Update system bar appearance based on theme
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode
                isAppearanceLightNavigationBars = !isDarkMode
            }

            MeshSosTheme(darkTheme = isDarkMode) {
                var selectedTab by remember { mutableIntStateOf(0) }

                val navBarColor = MaterialTheme.colorScheme.surface
                val selectedColor = MeshTeal
                val unselectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                val indicatorColor = MeshTeal.copy(alpha = 0.12f)

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = navBarColor,
                            tonalElevation = androidx.compose.ui.unit.dp.times(0)
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "SOS"
                                    )
                                },
                                label = {
                                    Text(
                                        "SOS",
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = selectedColor,
                                    selectedTextColor = selectedColor,
                                    unselectedIconColor = unselectedColor,
                                    unselectedTextColor = unselectedColor,
                                    indicatorColor = indicatorColor
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Status"
                                    )
                                },
                                label = {
                                    Text(
                                        "Status",
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = selectedColor,
                                    selectedTextColor = selectedColor,
                                    unselectedIconColor = unselectedColor,
                                    unselectedTextColor = unselectedColor,
                                    indicatorColor = indicatorColor
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = {
                                    Text(
                                        "Settings",
                                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = selectedColor,
                                    selectedTextColor = selectedColor,
                                    unselectedIconColor = unselectedColor,
                                    unselectedTextColor = unselectedColor,
                                    indicatorColor = indicatorColor
                                )
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> SosScreen(viewModel = viewModel)
                            1 -> MeshStatusScreen(viewModel = viewModel)
                            2 -> SettingsScreen(
                                viewModel = viewModel,
                                themePreferences = themePreferences,
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = buildList {
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

        val missingPermissions = requiredPermissions.filterNot(::isPermissionGranted)
        if (missingPermissions.isEmpty()) {
            viewModel.refreshService()
            return
        }

        permissionLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
