package com.example.rememberme.caregiver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val InkColor = Color(0xFF080B12)
val PanelColor = Color(0xFF111827)
val BorderColor = Color(0xFF2E333F)
val SosRed = Color(0xFFDC2626)
val ActiveBlue = Color(0xFF3B82F6)
val MintColor = Color(0xFF63E6BE)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)

private val DarkColorScheme = darkColorScheme(
    primary = ActiveBlue,
    secondary = MintColor,
    background = InkColor,
    surface = PanelColor,
    error = SosRed,
    onPrimary = Color.White,
    onSecondary = InkColor,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = Color.White
)

@Composable
fun RememberMeCaregiverTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
