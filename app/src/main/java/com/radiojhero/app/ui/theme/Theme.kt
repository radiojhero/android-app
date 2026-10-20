package com.radiojhero.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.TextUnit
import com.radiojhero.app.R

// Body text keeps the font's natural line height, like the TextViews this UI replaced.
private val Typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(lineHeight = TextUnit.Unspecified),
        bodyMedium = bodyMedium.copy(lineHeight = TextUnit.Unspecified),
        bodySmall = bodySmall.copy(lineHeight = TextUnit.Unspecified),
    )
}

/**
 * App theme. Colors come from `values/colors.xml` and `values-night/colors.xml`, so the night
 * mode set through AppCompatDelegate (see `switchTheme`) picks the right palette.
 */
@Composable
fun RadioJHeroTheme(content: @Composable () -> Unit) {
    val primary = colorResource(R.color.md_theme_primary)
    val onPrimary = colorResource(R.color.md_theme_onPrimary)
    val primaryContainer = colorResource(R.color.md_theme_primaryContainer)
    val onPrimaryContainer = colorResource(R.color.md_theme_onPrimaryContainer)
    val secondary = colorResource(R.color.md_theme_secondary)
    val onSecondary = colorResource(R.color.md_theme_onSecondary)
    val secondaryContainer = colorResource(R.color.md_theme_secondaryContainer)
    val onSecondaryContainer = colorResource(R.color.md_theme_onSecondaryContainer)
    val tertiary = colorResource(R.color.md_theme_tertiary)
    val onTertiary = colorResource(R.color.md_theme_onTertiary)
    val tertiaryContainer = colorResource(R.color.md_theme_tertiaryContainer)
    val onTertiaryContainer = colorResource(R.color.md_theme_onTertiaryContainer)
    val error = colorResource(R.color.md_theme_error)
    val onError = colorResource(R.color.md_theme_onError)
    val errorContainer = colorResource(R.color.md_theme_errorContainer)
    val onErrorContainer = colorResource(R.color.md_theme_onErrorContainer)
    val background = colorResource(R.color.md_theme_background)
    val onBackground = colorResource(R.color.md_theme_onBackground)
    val surface = colorResource(R.color.md_theme_surface)
    val onSurface = colorResource(R.color.md_theme_onSurface)
    val surfaceVariant = colorResource(R.color.md_theme_surfaceVariant)
    val onSurfaceVariant = colorResource(R.color.md_theme_onSurfaceVariant)
    val outline = colorResource(R.color.md_theme_outline)
    val outlineVariant = colorResource(R.color.md_theme_outlineVariant)
    val scrim = colorResource(R.color.md_theme_scrim)
    val inverseSurface = colorResource(R.color.md_theme_inverseSurface)
    val inverseOnSurface = colorResource(R.color.md_theme_inverseOnSurface)
    val inversePrimary = colorResource(R.color.md_theme_inversePrimary)
    val surfaceDim = colorResource(R.color.md_theme_surfaceDim)
    val surfaceBright = colorResource(R.color.md_theme_surfaceBright)
    val surfaceContainerLowest = colorResource(R.color.md_theme_surfaceContainerLowest)
    val surfaceContainerLow = colorResource(R.color.md_theme_surfaceContainerLow)
    val surfaceContainer = colorResource(R.color.md_theme_surfaceContainer)
    val surfaceContainerHigh = colorResource(R.color.md_theme_surfaceContainerHigh)
    val surfaceContainerHighest = colorResource(R.color.md_theme_surfaceContainerHighest)

    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceDim = surfaceDim,
    ) else lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceDim = surfaceDim,
    )

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
