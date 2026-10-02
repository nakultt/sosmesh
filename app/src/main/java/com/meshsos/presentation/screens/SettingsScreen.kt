package com.meshsos.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.BuildConfig
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.ThemePreferences
import com.meshsos.presentation.viewmodels.MeshViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MeshViewModel,
    themePreferences: ThemePreferences,
    isDarkMode: Boolean
) {
    val scope = rememberCoroutineScope()
    val autoRelayEnabled by viewModel.autoRelayEnabled.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Screen title ────────────────────────────────────────────────────
        Text(
            "Settings",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Configure your MeshSOS experience",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        // ── Appearance ──────────────────────────────────────────────────────
        SettingsSectionCard(title = "APPEARANCE") {
            SettingsToggleRow(
                label = "Dark Mode",
                description = "Switch to a dark interface theme",
                checked = isDarkMode,
                onCheckedChange = { enabled ->
                    scope.launch { themePreferences.setDarkMode(enabled) }
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Mesh Configuration ──────────────────────────────────────────────
        SettingsSectionCard(title = "MESH NETWORK") {
            SettingsToggleRow(
                label = "Auto Relay",
                description = "Automatically relay SOS packets from nearby devices (Good Samaritan mode)",
                checked = autoRelayEnabled,
                onCheckedChange = { viewModel.setAutoRelayEnabled(it) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Device Identity ─────────────────────────────────────────────────
        SettingsSectionCard(title = "DEVICE IDENTITY") {
            Text(
                text = viewModel.localDeviceName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = viewModel.localDeviceId,
                color = MeshTeal,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                "This unique ID identifies your device in the mesh network and appears in relay hop trails.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Service Control ─────────────────────────────────────────────────
        SettingsSectionCard(title = "SERVICE CONTROL") {
            Text(
                "The relay keeps discovering devices and forwarding SOS messages in the background.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            SettingsInfoRow(
                label = "Status",
                value = if (serviceRunning) "Running • $peerCount peer(s)" else "Stopped",
                valueColor = if (serviceRunning) SafeGreen else SosRed
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.startService() },
                    enabled = !serviceRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text(
                        "Start Service",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Button(
                    onClick = { viewModel.stopService() },
                    enabled = serviceRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed)
                ) {
                    Text(
                        "Stop Service",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── About ───────────────────────────────────────────────────────────
        SettingsSectionCard(title = "ABOUT") {
            SettingsInfoRow(label = "App Name", value = "MeshSOS")
            SettingsInfoRow(label = "Version", value = BuildConfig.VERSION_NAME)
            SettingsInfoRow(label = "Build", value = if (BuildConfig.DEBUG) "Debug" else "Release")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Reusable settings components ────────────────────────────────────────────

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                color = MeshTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MeshTeal,
                uncheckedThumbColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                uncheckedTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
