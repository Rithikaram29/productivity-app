package com.intentcoach.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Palette v1.1 — "pastel-on-dark" (project doc: design-direction.md). Backgrounds stay
 * deep and warm; the pastels belong to the companion and highlights.
 *
 * The check-in screen keeps its own private copy (overlay/InterruptActivity.kt) so overlay/
 * does not depend on ui/. If you change a colour, change it in both places.
 *
 * The old names (Accent, Green) are kept so existing screens did not all need touching:
 * Accent is now Clay (firm / primary), Green is now Lime (grow / positive).
 */
internal val Ink = Color(0xFF100B07)
internal val Umber = Color(0xFF17100B)
internal val Field = Color(0xFF221A13)
internal val FieldBorder = Color(0xFF443A31)
internal val Cream = Color(0xFFF5ECDE)
internal val Muted = Color(0xFFAC9C8B)

internal val Clay = Color(0xFFD9734E)
internal val Ember = Color(0xFFEBAF5A)
internal val Lime = Color(0xFFC3E667)
internal val Mint = Color(0xFFA6E0C0)
internal val Blush = Color(0xFFF1B4C3)
internal val Peach = Color(0xFFF3D6AD)
internal val Lavender = Color(0xFFCDBEEB)
internal val Sky = Color(0xFFAAD2E8)

internal val Accent = Clay
internal val Green = Lime

// Time-of-day scene tints for the companion's window.
internal val MorningTint = Color(0xFFF3E7D6)
internal val AfternoonTint = Color(0xFFEFDFC4)
internal val EveningTint = Color(0xFF463228)
internal val NightTint = Color(0xFF0F0A06)
