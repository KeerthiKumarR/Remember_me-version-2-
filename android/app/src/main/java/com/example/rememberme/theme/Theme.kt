package com.example.rememberme.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Semantic app color definitions
data class AppColors(
    val ink: Color,
    val panel: Color,
    val border: Color,
    val mint: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val inputBackground: Color,
    val cardBorder: Color,
    val cardContainer: Color,
    val topBarTitle: Color,
    val topBarNavigationText: Color,
    val isDark: Boolean
)

val DarkAppColors = AppColors(
    ink = Color(0xFF080B12),
    panel = Color(0xFF111827),
    border = Color(0xFF2E333F),
    mint = Color(0xFF63E6BE),
    textPrimary = Color.White,
    textSecondary = Color.LightGray,
    textTertiary = Color.Gray,
    inputBackground = Color.Black.copy(alpha = 0.25f),
    cardBorder = Color.White.copy(alpha = 0.10f),
    cardContainer = Color(0xFF111827).copy(alpha = 0.7f),
    topBarTitle = Color.White,
    topBarNavigationText = Color.LightGray,
    isDark = true
)

val LightAppColors = AppColors(
    ink = Color(0xFFF3F4F6),
    panel = Color(0xFFFFFFFF),
    border = Color(0xFFE5E7EB),
    mint = Color(0xFF0D9488),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF4B5563),
    textTertiary = Color(0xFF6B7280),
    inputBackground = Color(0xFFE5E7EB).copy(alpha = 0.5f),
    cardBorder = Color.Black.copy(alpha = 0.08f),
    cardContainer = Color.White,
    topBarTitle = Color(0xFF111827),
    topBarNavigationText = Color(0xFF4B5563),
    isDark = false
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

@Composable
fun RememberMeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set dynamic color default to false to allow custom color system to take absolute precedence
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val appColors = if (darkTheme) DarkAppColors else LightAppColors
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  CompositionLocalProvider(LocalAppColors provides appColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
