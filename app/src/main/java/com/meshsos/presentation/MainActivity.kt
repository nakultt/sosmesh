package com.meshsos.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.meshsos.data.transport.TransportCapabilityChecker
import com.meshsos.presentation.components.ReadinessActions
import com.meshsos.presentation.screens.DebugConsoleScreen
import com.meshsos.presentation.screens.MeshStatusScreen
import com.meshsos.presentation.screens.RelayLogScreen
import com.meshsos.presentation.screens.SettingsScreen
import com.meshsos.presentation.screens.SosScreen
import com.meshsos.presentation.theme.MeshSosTheme
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.ThemePreferences
import com.meshsos.presentation.viewmodels.MeshViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private data class NavTab(val label: String, val icon: ImageVector)

private val navTabs = listOf(
    NavTab("SOS", Icons.Default.Warning),
    NavTab("Status", Icons.Default.Info),
    NavTab("Logs", Icons.Default.List),
    NavTab("Debug", Icons.Default.Build),
    NavTab("Settings", Icons.Default.Settings)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MeshViewModel by viewModels()

    @Inject
    lateinit var themePreferences: ThemePreferences

    private var permissionsPermanentlyDenied by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            Log.w(TAG, "Permissions denied: ${denied.joinToString()}")
            // Without a rationale the system will no longer show the dialog: only Settings can fix it.
            permissionsPermanentlyDenied = denied
                .filter { it != Manifest.permission.POST_NOTIFICATIONS }
                .any { !shouldShowRequestPermissionRationale(it) }
        } else {
            permissionsPermanentlyDenied = false
        }
        viewModel.refreshService()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshService()
    }

    private val readinessActions by lazy {
        ReadinessActions(
            requestPermissions = ::requestRequiredPermissions,
            enableBluetooth = ::enableBluetooth,
            openLocationSettings = ::openLocationSettings,
            openAppSettings = ::openAppSettings
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestRequiredPermissions()

        setContent {
            val darkModePreference by themePreferences.isDarkMode.collectAsState(initial = null)
            val isDarkMode = darkModePreference ?: isSystemInDarkTheme()

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkMode
                    isAppearanceLightNavigationBars = !isDarkMode
                }
            }

            MeshSosTheme(darkTheme = isDarkMode) {
                var selectedTab by rememberSaveable { mutableStateOf(0) }
                val actions = readinessActions.copy(permissionsPermanentlyDenied = permissionsPermanentlyDenied)

                val selectedColor = MeshTeal
                val unselectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                val indicatorColor = MeshTeal.copy(alpha = 0.12f)

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            navTabs.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = {
                                        Text(
                                            tab.label,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
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
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> SosScreen(viewModel = viewModel, readinessActions = actions)
                            1 -> MeshStatusScreen(viewModel = viewModel, readinessActions = actions)
                            2 -> RelayLogScreen(viewModel = viewModel)
                            3 -> DebugConsoleScreen(viewModel = viewModel)
                            4 -> SettingsScreen(
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

    override fun onResume() {
        super.onResume()
        // The user may have changed permissions, Bluetooth or Location while we were away.
        viewModel.refreshService()
    }

    // ── Readiness actions ─────────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val missing = TransportCapabilityChecker.requestedPermissions().filterNot(::isPermissionGranted)
        if (missing.isEmpty()) {
            permissionsPermanentlyDenied = false
            viewModel.refreshService()
            return
        }
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun enableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            requestRequiredPermissions()
            return
        }
        runCatching {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
    }

    private fun openLocationSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
