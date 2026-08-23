package com.avinash.yatramitra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OffWhite,
    secondary = AmberAccent,
    onSecondary = Ink,
    background = OffWhite,
    surface = OffWhite,
    onBackground = Ink,
    onSurface = Ink,
    error = SoftRed
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    secondary = AmberAccent,
    onSecondary = Ink,
    background = Color4B(),
    surface = Color4B(),
    onBackground = OffWhite,
    onSurface = OffWhite,
    error = SoftRed
)

// Small helper kept private so the dark background reads as a single source of truth.
private fun Color4B() = androidx.compose.ui.graphics.Color(0xFF121212)

@Composable
fun YatraMitraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
