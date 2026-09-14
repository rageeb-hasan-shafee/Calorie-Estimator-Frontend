package com.example.capstone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Design tokens lifted 1:1 from style.css `:root`. The web app is a fixed
 * dark design (no light-mode variant), so this theme is not dynamic.
 */
object MealScan {
    val bg = Color(0xFF14161A)
    val panel = Color(0xFF1C1F26)
    val panelRaised = Color(0xFF22262F)
    val line = Color(0xFF2C313C)
    val stageBg = Color(0xFF0F1114) // .scan-stage background

    val text = Color(0xFFEDEDEE)
    val textDim = Color(0xFF9198A6)
    val textFaint = Color(0xFF5C6474)

    val teal = Color(0xFF3DD6C4)
    val tealDim = Color(0xFF204A46)
    val amber = Color(0xFFF2A93B)
    val amberDim = Color(0xFF4A3A1C)
    val red = Color(0xFFE15554)
    val redDim = Color(0xFF4A2020)

    // Segment color cycle — used for mask fills before classification resolves.
    val segmentColors = listOf(
        Color(0xFF3DD6C4),
        Color(0xFFF2A93B),
        Color(0xFF8E7CE0),
        Color(0xFFE88AB0),
        Color(0xFF6FBF6A),
        Color(0xFF4FA3D1),
        Color(0xFFE07A5F),
        Color(0xFFB5CC5C),
    )

    val mono = FontFamily.Monospace
}

private val MealScanColorScheme = darkColorScheme(
    background = MealScan.bg,
    surface = MealScan.panel,
    surfaceVariant = MealScan.panelRaised,
    onBackground = MealScan.text,
    onSurface = MealScan.text,
    primary = MealScan.teal,
    onPrimary = MealScan.bg,
    secondary = MealScan.amber,
    error = MealScan.red,
    outline = MealScan.line,
)

@Composable
fun MealScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MealScanColorScheme,
        content = content,
    )
}
