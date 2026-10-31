package com.ira.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF100E16)
val Surface = Color(0xFF1C1924)
val SurfaceRaised = Color(0xFF24202D)
val Outline = Color(0xFF37313F)
val Rose = Color(0xFFE9B5C7)
val RoseLight = Color(0xFFFFDCE4)
val Lavender = Color(0xFFB6A0D8)
val Cream = Color(0xFFF8EFEF)
val Muted = Color(0xFFAAA2B1)
val Mint = Color(0xFFACD6C2)

private val IraColors = darkColorScheme(
    primary = Rose,
    onPrimary = Color(0xFF2A1420),
    secondary = Lavender,
    background = Background,
    onBackground = Cream,
    surface = Surface,
    onSurface = Cream,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Muted,
    outline = Outline
)

@Composable
fun IraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IraColors, content = content)
}
