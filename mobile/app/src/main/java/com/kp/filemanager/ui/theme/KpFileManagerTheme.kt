/** mobile/app/src/main/java/com/kp/filemanager/ui/theme/KpFileManagerTheme.kt: Defines the adaptive Material 3 theme, spacing, shapes, and immediate theme preference; expanded for the UI refactor. */
package com.kp.filemanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemePreference(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class KpSpacing(
    val micro: Dp = 4.dp,
    val tight: Dp = 8.dp,
    val related: Dp = 12.dp,
    val standard: Dp = 16.dp,
    val section: Dp = 24.dp,
    val large: Dp = 32.dp,
    val major: Dp = 48.dp,
)

private val LocalKpSpacing = staticCompositionLocalOf { KpSpacing() }

val MaterialTheme.spacing: KpSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalKpSpacing.current

private val LightColors = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF006C4C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF8BF8C7),
    onSecondaryContainer = Color(0xFF002115),
    tertiary = Color(0xFF765A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDF91),
    onTertiaryContainer = Color(0xFF251A00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFF6CDBAC),
    onSecondary = Color(0xFF003826),
    secondaryContainer = Color(0xFF005138),
    onSecondaryContainer = Color(0xFF8BF8C7),
    tertiary = Color(0xFFEAC248),
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Color(0xFF594400),
    onTertiaryContainer = Color(0xFFFFDF91),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

@Composable
fun KpFileManagerTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalKpSpacing provides KpSpacing()) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography(),
            shapes = Shapes(),
            content = content,
        )
    }
}
