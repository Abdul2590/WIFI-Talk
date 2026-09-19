package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TacticalDarkColorScheme = darkColorScheme(
  primary = TacticalAmber,
  onPrimary = Color.Black,
  primaryContainer = TacticalAmberDark,
  onPrimaryContainer = TacticalAmberLight,
  secondary = RadioCyan,
  onSecondary = Color.Black,
  secondaryContainer = RadioCyanDark,
  onSecondaryContainer = Color.White,
  tertiary = SignalGreen,
  onTertiary = Color.Black,
  background = TacticalDarkBg,
  onBackground = TextPrimary,
  surface = TacticalSurface,
  onSurface = TextPrimary,
  surfaceVariant = TacticalSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  error = TransmitRed,
  onError = Color.White
)

private val TacticalLightColorScheme = lightColorScheme(
  primary = TacticalAmberDark,
  onPrimary = Color.White,
  primaryContainer = TacticalAmberLight,
  onPrimaryContainer = Color.Black,
  secondary = RadioCyanDark,
  onSecondary = Color.White,
  secondaryContainer = RadioCyan,
  onSecondaryContainer = Color.Black,
  tertiary = SignalGreen,
  onTertiary = Color.White,
  background = Color(0xFF0F172A),
  onBackground = TextPrimary,
  surface = TacticalSurface,
  onSurface = TextPrimary,
  surfaceVariant = TacticalSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  error = TransmitRed,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  // Walkie talkie hardware interface looks best in specialized tactical dark mode
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) TacticalDarkColorScheme else TacticalLightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
