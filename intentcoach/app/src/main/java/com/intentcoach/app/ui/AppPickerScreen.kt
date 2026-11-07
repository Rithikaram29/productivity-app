package com.intentcoach.app.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.intentcoach.app.data.WatchedApps

/**
 * Lets the user choose which apps trigger the interrupt.
 *
 * Only shows apps from WatchedApps.CATALOG that are actually installed here — see
 * WatchedApps.installed() for why the list is curated rather than enumerated.
 */
@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Both of these touch the PackageManager / SharedPreferences, which is disk I/O.
    // remember(Unit) runs them once when the screen appears instead of on every
    // recomposition (and a recomposition happens on every single tap below).
    val apps = remember { WatchedApps.installed(context) }
    var selected by remember { mutableStateOf(WatchedApps.get(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Which apps should ask?", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "We'll only interrupt for the ones you pick. Everything else opens as normal.",
            color = Muted, fontSize = 14.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))

        if (apps.isEmpty()) {
            // Possible on a clean device or an emulator: none of the catalog is installed.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "None of the apps we know about are installed on this phone yet.",
                    color = Muted, fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        packageName = app.packageName,
                        fallbackName = app.displayName,
                        checked = app.packageName in selected,
                        onToggle = { isOn ->
                            // Rebuild the set rather than mutating it: Compose only
                            // recomposes when the state object itself changes identity.
                            selected = if (isOn) selected + app.packageName
                                       else selected - app.packageName
                            // Write through immediately — the service reads this from
                            // SharedPreferences on its next poll, so there is no
                            // "save" step to forget.
                            WatchedApps.set(context, selected)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Done", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun AppRow(
    packageName: String,
    fallbackName: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Icon and label come from the device so they match what the user sees on their
    // home screen. Keyed on packageName so the lookup is not repeated per recomposition.
    val details = remember(packageName) { loadAppDetails(context.packageManager, packageName, fallbackName) }

    Surface(
        color = Field,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = details.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null, // the label beside it already says the name
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Box(Modifier.size(36.dp).background(FieldBorder, RoundedCornerShape(8.dp)))
            }
            Spacer(Modifier.width(14.dp))
            Text(details.label, color = Cream, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Cream,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = FieldBorder,
                    uncheckedBorderColor = FieldBorder
                )
            )
        }
    }
}

private class AppDetails(val label: String, val icon: ImageBitmap?)

/**
 * Both lookups can throw NameNotFoundException if the app is uninstalled between the
 * installed() scan and this row being drawn — rare, but a crash if unhandled, so we fall
 * back to the catalog's display name.
 */
private fun loadAppDetails(pm: PackageManager, packageName: String, fallbackName: String): AppDetails = try {
    val info = pm.getApplicationInfo(packageName, 0)
    val label = pm.getApplicationLabel(info).toString()
    // Adaptive icons have no meaningful intrinsic size, so ask for explicit pixels
    // rather than letting toBitmap() guess.
    val icon = pm.getApplicationIcon(info).toBitmap(width = 144, height = 144).asImageBitmap()
    AppDetails(label, icon)
} catch (e: PackageManager.NameNotFoundException) {
    AppDetails(fallbackName, null)
}
