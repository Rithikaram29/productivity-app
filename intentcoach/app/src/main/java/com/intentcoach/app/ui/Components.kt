package com.intentcoach.app.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.intentcoach.app.AppContainer
import com.intentcoach.app.container

/** Small building blocks shared by every screen, so they look like one product. */

@Composable
fun IntentCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Clay,
            onPrimary = Cream,
            secondary = Lime,
            onSecondary = Ink,
            background = Ink,
            onBackground = Cream,
            surface = Field,
            onSurface = Cream,
            surfaceVariant = Field,
            onSurfaceVariant = Muted,
            outline = FieldBorder
        ),
        content = content
    )
}

/**
 * Builds a ViewModel from the app container. The factory only runs the first time; after
 * that (rotation, recomposition) the same ViewModel instance is handed back — that is what
 * lets screen state survive configuration changes.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContext.current.container
    return viewModel(factory = viewModelFactory { initializer { create(c) } })
}

/** Same as [appViewModel], but distinct ViewModels per [key] (e.g. one per app history). */
@Composable
inline fun <reified VM : ViewModel> appViewModelKeyed(key: String, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column {
        Text(text, color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, fontSize = 13.sp, modifier = modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
fun CoachCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    if (onClick != null) {
        Surface(onClick = onClick, color = Field, shape = shape, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Surface(color = Field, shape = shape, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Clay, contentColor = Cream,
            disabledContainerColor = Field, disabledContentColor = Muted
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, FieldBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream, disabledContentColor = Muted),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontSize = 15.sp) }
}

/** A selectable pill. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = if (selected) Lime.copy(alpha = 0.16f) else Field,
        border = BorderStroke(1.dp, if (selected) Lime else FieldBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Text(label, color = Cream, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

/** Wraps pills onto as many lines as needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Cream) {
    Surface(color = Field, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(label, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).background(Ember, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** Horizontal bar: [fraction] of the width filled. The "chart library" of this app. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).background(FieldBorder, RoundedCornerShape(3.dp))) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
    }
}

/** A real label if the app is still installed, else the name we stored. */
fun appLabel(pm: PackageManager, pkg: String, fallback: String): String = try {
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: PackageManager.NameNotFoundException) {
    if (fallback != pkg) fallback else pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
