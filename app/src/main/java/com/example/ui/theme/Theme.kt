package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val RainCubeDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = MidnightVoid,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = TextPrimary,
    secondary = SkyPulse,
    onSecondary = MidnightVoid,
    secondaryContainer = ElevatedSlate,
    onSecondaryContainer = TextPrimary,
    tertiary = VerifiedEmerald,
    onTertiary = MidnightVoid,
    tertiaryContainer = VerifiedEmeraldContainer,
    onTertiaryContainer = TextPrimary,
    background = MidnightVoid,
    onBackground = TextPrimary,
    surface = DeepNavy,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = TextSecondary,
    outline = OutlineNavy,
    error = ErrorCoral,
    onError = MidnightVoid,
    errorContainer = ErrorCoralContainer,
    onErrorContainer = TextPrimary
)

private val RainCubeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun RainCubeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RainCubeDarkColorScheme,
        typography = Typography,
        shapes = RainCubeShapes,
        content = content
    )
}
