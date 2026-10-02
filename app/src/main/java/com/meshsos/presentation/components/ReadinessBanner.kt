package com.meshsos.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.WarnAmber
import com.meshsos.presentation.viewmodels.MeshReadiness

/** Callbacks into the Activity for fixing readiness problems. */
data class ReadinessActions(
    val requestPermissions: () -> Unit = {},
    val enableBluetooth: () -> Unit = {},
    val openLocationSettings: () -> Unit = {},
    val openAppSettings: () -> Unit = {},
    /** True when the user permanently denied a permission, so only App settings can fix it. */
    val permissionsPermanentlyDenied: Boolean = false
)

private data class ReadinessIssue(
    val title: String,
    val detail: String,
    val actionLabel: String,
    val critical: Boolean,
    val action: () -> Unit
)

/**
 * Explains exactly why the mesh cannot find devices and offers a one-tap fix for each issue.
 * Renders nothing when everything is ready.
 */
@Composable
fun ReadinessBanner(
    readiness: MeshReadiness,
    actions: ReadinessActions,
    serviceRunning: Boolean,
    onStartService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val issues = buildList {
        if (!readiness.bluetoothSupported) {
            add(ReadinessIssue("Bluetooth not supported", "This device cannot join the mesh.", "", true) {})
        }
        if (readiness.missingPermissions.isNotEmpty()) {
            val names = readiness.missingPermissions.joinToString { it.substringAfterLast('.').replace('_', ' ').lowercase() }
            add(
                ReadinessIssue(
                    title = "Permissions needed",
                    detail = "Nearby device discovery needs: $names",
                    actionLabel = if (actions.permissionsPermanentlyDenied) "Open settings" else "Grant",
                    critical = true,
                    action = if (actions.permissionsPermanentlyDenied) actions.openAppSettings else actions.requestPermissions
                )
            )
        }
        if (readiness.bluetoothSupported && !readiness.bluetoothEnabled) {
            add(ReadinessIssue("Bluetooth is off", "Turn on Bluetooth to find and relay to nearby devices.", "Turn on", true, actions.enableBluetooth))
        }
        if (readiness.locationServicesRequired && !readiness.locationServicesEnabled) {
            add(ReadinessIssue("Location is off", "This Android version needs Location turned on for Bluetooth discovery.", "Turn on", true, actions.openLocationSettings))
        }
        if (readiness.missingPermissions.isEmpty() && !readiness.locationPermissionGranted) {
            add(ReadinessIssue("Location permission off", "Your SOS will be sent without your position.", "Grant", false, actions.requestPermissions))
        }
        if (!readiness.notificationsAllowed) {
            add(ReadinessIssue("Notifications off", "You won't be alerted when someone nearby needs help.", "Enable", false, actions.openAppSettings))
        }
        if (readiness.meshReady && !serviceRunning) {
            add(ReadinessIssue("Mesh relay stopped", "Start the relay to send and receive SOS messages.", "Start", true, onStartService))
        }
    }
    if (issues.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        issues.forEach { issue -> IssueCard(issue) }
    }
}

@Composable
private fun IssueCard(issue: ReadinessIssue) {
    val accent = if (issue.critical) SosRed else WarnAmber
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(issue.title, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    issue.detail,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            if (issue.actionLabel.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = issue.action,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                ) {
                    Text(issue.actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
