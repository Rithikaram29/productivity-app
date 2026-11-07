package com.intentcoach.app.ui

import androidx.compose.ui.graphics.Color

/**
 * The interrupt screen's palette, shared by the app's own screens so the product looks
 * like one thing. These deliberately duplicate the private values in
 * InterruptActivity.kt rather than being imported from it: overlay/ is a separate
 * launched surface, and making it depend on ui/ would tangle two layers that are
 * otherwise independent. If you change a colour, change it in both places.
 */
internal val Ink = Color(0xFF12100E)
internal val Cream = Color(0xFFF5F2EC)
internal val Muted = Color(0xFF8A857D)
internal val Field = Color(0xFF1E1B17)
internal val FieldBorder = Color(0xFF38332C)
internal val Accent = Color(0xFFE1306C)
internal val Green = Color(0xFF1D9E75)
