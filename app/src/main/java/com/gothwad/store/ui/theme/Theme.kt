package com.gothwad.store.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.gothwad.store.data.settings.ThemeMode

private val LightScheme = lightColorScheme(
    primary = PlayColors.Green,
    onPrimary = PlayColors.OnGreen,
    primaryContainer = PlayColors.GreenLight,
    onPrimaryContainer = PlayColors.GreenInk,
    secondary = PlayColors.Blue,
    onSecondary = Color.White,
    secondaryContainer = PlayColors.BlueLight,
    onSecondaryContainer = PlayColors.Blue,
    tertiary = PlayColors.Purple,
    onTertiary = Color.White,
    background = PlayColors.White,
    onBackground = PlayColors.TextPrimary,
    surface = PlayColors.Surface,
    onSurface = PlayColors.TextPrimary,
    surfaceVariant = PlayColors.SearchBar,
    onSurfaceVariant = PlayColors.TextSecondary,
    surfaceContainerLow = PlayColors.SurfaceMuted,
    outline = PlayColors.ChipBorder,
    outlineVariant = PlayColors.Divider,
    error = PlayColors.Error,
    onError = Color.White,
    errorContainer = PlayColors.ErrorContainer,
    onErrorContainer = PlayColors.Error,
    scrim = PlayColors.Scrim,
)

private val DarkScheme = darkColorScheme(
    primary = PlayColorsDark.Green,
    onPrimary = Color(0xFF00331F),
    primaryContainer = PlayColorsDark.GreenContainer,
    onPrimaryContainer = PlayColorsDark.GreenInk,
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF0B2B52),
    secondaryContainer = Color(0xFF1D3A5F),
    onSecondaryContainer = Color(0xFFD3E3FD),
    tertiary = Color(0xFFD0BCFF),
    onTertiary = Color(0xFF2B1B52),
    background = Color(0xFF121212),
    onBackground = PlayColorsDark.TextPrimary,
    surface = PlayColorsDark.Surface,
    onSurface = PlayColorsDark.TextPrimary,
    surfaceVariant = PlayColorsDark.SearchBar,
    onSurfaceVariant = PlayColorsDark.TextSecondary,
    surfaceContainerLow = PlayColorsDark.SurfaceMuted,
    outline = PlayColorsDark.ChipBorder,
    outlineVariant = PlayColorsDark.Divider,
    error = PlayColorsDark.Error,
    onError = Color(0xFF3A0B06),
    errorContainer = PlayColorsDark.ErrorContainer,
    onErrorContainer = PlayColorsDark.Error,
    scrim = Color(0x99000000),
)

/**
 * Extra semantic colours that Material3's scheme does not model (star yellow,
 * warning banners, banner gradient …). Keeps screens free of hard-coded hex.
 */
data class GothwadExtraColors(
    val star: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val searchBar: Color,
    val chipBackground: Color,
    val chipBorder: Color,
    val bannerStart: Color,
    val bannerEnd: Color,
    val greenInk: Color,
)

val LocalExtraColors: ProvidableCompositionLocal<GothwadExtraColors> =
    staticCompositionLocalOf { LightExtraColors }

private val LightExtraColors = GothwadExtraColors(
    star = PlayColors.Star,
    success = PlayColors.Success,
    successContainer = PlayColors.SuccessContainer,
    warning = PlayColors.Warning,
    warningContainer = PlayColors.WarningContainer,
    divider = PlayColors.Divider,
    textPrimary = PlayColors.TextPrimary,
    textSecondary = PlayColors.TextSecondary,
    textTertiary = PlayColors.TextTertiary,
    searchBar = PlayColors.SearchBar,
    chipBackground = PlayColors.SurfaceMuted,
    chipBorder = PlayColors.ChipBorder,
    bannerStart = PlayColors.BannerStart,
    bannerEnd = PlayColors.BannerEnd,
    greenInk = PlayColors.GreenInk,
)

private val DarkExtraColors = GothwadExtraColors(
    star = PlayColorsDark.Star,
    success = PlayColorsDark.Success,
    successContainer = PlayColorsDark.SuccessContainer,
    warning = PlayColorsDark.Warning,
    warningContainer = PlayColorsDark.WarningContainer,
    divider = PlayColorsDark.Divider,
    textPrimary = PlayColorsDark.TextPrimary,
    textSecondary = PlayColorsDark.TextSecondary,
    textTertiary = PlayColorsDark.TextTertiary,
    searchBar = PlayColorsDark.SearchBar,
    chipBackground = PlayColorsDark.SurfaceMuted,
    chipBorder = PlayColorsDark.ChipBorder,
    bannerStart = PlayColorsDark.BannerStart,
    bannerEnd = PlayColorsDark.BannerEnd,
    greenInk = PlayColorsDark.GreenInk,
)

object GothwadTheme {
    val extra: GothwadExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}

@Composable
fun KiwiStoreTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val extra = if (dark) DarkExtraColors else LightExtraColors
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GothwadTypography,
            shapes = GothwadShapes,
            content = content,
        )
    }
}
