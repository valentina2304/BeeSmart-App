package com.example.beesmart.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.beesmart.ui.components.BeeSmartBackdrop

private val LightColorScheme = lightColorScheme(
    primary = YellowPrimary,
    onPrimary = Color(0xFF241400),
    primaryContainer = YellowLight,
    onPrimaryContainer = Color(0xFF2B1900),

    secondary = BrownPrimary,
    onSecondary = Color.White,
    secondaryContainer = BrownLight,
    onSecondaryContainer = Color(0xFF251306),

    tertiary = GreenSuccess,
    onTertiary = Color.White,
    tertiaryContainer = SageSoft,
    onTertiaryContainer = Color(0xFF062112),

    error = RedError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF3F0500),

    background = Color.Transparent,
    onBackground = Color(0xFF241A12),

    surface = CreamSurface,
    onSurface = Color(0xFF241A12),
    surfaceVariant = Color(0xFFECE0CB),
    onSurfaceVariant = Color(0xFF5E5142),
    surfaceContainerLowest = Color(0xFFFFFAF0),
    surfaceContainerLow = Color(0xFFFFF8E8),
    surfaceContainer = Color(0xFFFFF4DD),
    surfaceContainerHigh = Color(0xFFF7EAD2),

    outline = Color(0xFF8B7B68),
    outlineVariant = Color(0xFFD7C7AE)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF352000),
    primaryContainer = Color(0xFF5B3900),
    onPrimaryContainer = Color(0xFFFFE3A3),

    secondary = Color(0xFFDDBB8D),
    onSecondary = Color(0xFF3C230E),
    secondaryContainer = Color(0xFF563719),
    onSecondaryContainer = Color(0xFFF6DFC0),

    tertiary = Color(0xFF8CCF9D),
    onTertiary = Color(0xFF04351A),
    tertiaryContainer = Color(0xFF164B29),
    onTertiaryContainer = Color(0xFFDDEEDB),

    error = Color(0xFFFFB4AA),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD5),

    background = Color(0xFF19140D),
    onBackground = Color(0xFFF2E7D6),

    surface = Color(0xFF211A11),
    onSurface = Color(0xFFF2E7D6),
    surfaceVariant = Color(0xFF564A3A),
    onSurfaceVariant = Color(0xFFD9C8AF),

    outline = Color(0xFFA9977F),
    outlineVariant = Color(0xFF564A3A)
)

private val BeeSmartShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun BeeSmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = BeeSmartShapes
    ) {
        BeeSmartBackdrop {
            content()
        }
    }
}

