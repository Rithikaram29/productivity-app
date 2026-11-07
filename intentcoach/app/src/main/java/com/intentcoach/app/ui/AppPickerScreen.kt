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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intentcoach.app.AppContainer
import com.intentcoach.app.data.AppCategory
import com.intentcoach.app.data.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppPickerViewModel(private val c: AppContainer) : ViewModel() {
    private val _installed = MutableStateFlow<List<InstalledApp>?>(null)
    /** null while loading. */
    val installed: StateFlow<List<InstalledApp>?> = _installed.asStateFlow()

    val selected: StateFlow<Set<String>> = c.apps.observeAll()
        .map { list -> list.filter { it.isEnabled }.map { it.packageName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch { _installed.value = c.apps.launchableApps() }
    }

    fun toggle(app: InstalledApp, on: Boolean) {
        // appScope: the write must land even if the user leaves the screen straight away.
        c.appScope.launch { c.apps.setMonitored(app, on) }
    }
}

/** Standalone screen (from Settings / Home). */
@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp, vertical = 16.dp)) {
        ScreenTitle(
            "Which apps should ask first?",
            "We'll only check in for the ones you pick. Everything else opens as normal."
        )
        Spacer(Modifier.height(12.dp))
        AppPickerContent(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Done", onClick = onBack)
    }
}

/**
 * The list itself, also embedded in onboarding. Lists launchable apps only, grouped by a
 * suggested category. Nothing is selected unless the user selects it.
 */
@Composable
fun AppPickerContent(modifier: Modifier = Modifier) {
    val vm = appViewModel { AppPickerViewModel(it) }
    val installed by vm.installed.collectAsState()
    val selected by vm.selected.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps", color = Muted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Field, unfocusedContainerColor = Field,
                focusedTextColor = Cream, unfocusedTextColor = Cream,
                focusedBorderColor = Lime, unfocusedBorderColor = FieldBorder
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (selected.size == 1) "1 app selected" else "${selected.size} apps selected",
            color = Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))

        val list = installed
        when {
            list == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lime)
            }
            list.isEmpty() -> Text("No apps found.", color = Muted, fontSize = 15.sp)
            else -> {
                val filtered = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                val groups = AppCategory.entries.mapNotNull { cat ->
                    filtered.filter { it.category == cat }.takeIf { it.isNotEmpty() }?.let { cat to it }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { (cat, apps) ->
                        item(key = "cat-${cat.name}") { SectionLabel(cat.label) }
                        items(apps, key = { it.packageName }) { app ->
                            AppRow(app, checked = app.packageName in selected, onToggle = { vm.toggle(app, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Icons are decoded off the main thread, once per package: produceState starts null
    // (draw a placeholder) and fills in when ready, so scrolling never stalls on decoding.
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) { loadIcon(context.packageManager, app.packageName) }
    }

    Surface(color = Field, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = icon
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
            } else {
                Box(Modifier.size(36.dp).background(FieldBorder, RoundedCornerShape(8.dp)))
            }
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = Cream, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink,
                    checkedTrackColor = Lime,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = FieldBorder,
                    uncheckedBorderColor = FieldBorder
                )
            )
        }
    }
}

/** Can throw if the app is uninstalled mid-scroll — rare, but then we just show no icon. */
private fun loadIcon(pm: PackageManager, packageName: String): ImageBitmap? = try {
    // Adaptive icons have no meaningful intrinsic size, so ask for explicit pixels.
    pm.getApplicationIcon(packageName).toBitmap(width = 144, height = 144).asImageBitmap()
} catch (e: PackageManager.NameNotFoundException) {
    null
}
