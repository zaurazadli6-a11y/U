package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = ArenaCyan,
  onPrimary = Color.White,
  secondary = ArenaCoral,
  onSecondary = Color.White,
  tertiary = ArenaGold,
  onTertiary = Color(0xFF1E293B),
  background = PaperBackgroundDark,
  onBackground = PaperInkDark,
  surface = PaperSurfaceDark,
  onSurface = PaperInkDark,
  surfaceVariant = PaperSurfaceVariantDark,
  onSurfaceVariant = PaperMutedInkDark
)

private val LightColorScheme = lightColorScheme(
  primary = ArenaCyan,
  onPrimary = Color.White,
  secondary = ArenaCoral,
  onSecondary = Color.White,
  tertiary = ArenaGold,
  onTertiary = Color(0xFF0F172A),
  background = PaperBackgroundLight,
  onBackground = PaperInkLight,
  surface = PaperSurfaceLight,
  onSurface = PaperInkLight,
  surfaceVariant = PaperSurfaceVariantLight,
  onSurfaceVariant = PaperMutedInkLight
)

@Composable
fun PaperArenaTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    content = content
  )
}
