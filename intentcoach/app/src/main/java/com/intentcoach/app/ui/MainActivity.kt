package com.intentcoach.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.detection.AppWatchService
import com.intentcoach.app.util.Permissions

/**
 * MVP onboarding. The two permissions we need are "special" — the user grants
 * them in system Settings, not via a dialog. So this screen's whole job is to
 * explain why, deep-link to the right Settings page, and start the service once
 * both are granted.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SetupScreen() } }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    // Re-check permission state each time the screen resumes (after user returns from Settings).
    var usageGranted by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(Permissions.hasOverlay(context)) }

    LaunchedEffect(Unit) {
        usageGranted = Permissions.hasUsageAccess(context)
        overlayGranted = Permissions.hasOverlay(context)
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Intent Coach", fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "When you open a distracting app, we'll ask one question: what did you pick up your phone to do? Nothing leaves your device.",
            fontSize = 15.sp, lineHeight = 22.sp
        )
        Spacer(Modifier.height(32.dp))

        PermissionRow(
            title = "Usage access",
            why = "Lets us notice when a distracting app opens.",
            granted = usageGranted
        ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        Spacer(Modifier.height(16.dp))

        PermissionRow(
            title = "Draw over other apps",
            why = "Lets us show the question on top of that app.",
            granted = overlayGranted
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { AppWatchService.start(context) },
            enabled = usageGranted && overlayGranted,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Start", fontSize = 16.sp, fontWeight = FontWeight.Medium) }

        if (!usageGranted || !overlayGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant both permissions above to start.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(why, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Text("Granted", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            } else {
                TextButton(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}
