package com.avinash.yatramitra.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens from the Stitch "Collaborative Trip Planner" design system (DESIGN.md front-matter),
// which matches the Tailwind config actually used in the exported screen mockups.

// Core M3 roles (light scheme)
val Primary = Color(0xFF9D4300)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFF97316)
val OnPrimaryContainer = Color(0xFF582200)
val InversePrimary = Color(0xFFFFB690)

val Secondary = Color(0xFF565E74)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFDAE2FD)
val OnSecondaryContainer = Color(0xFF5C647A)

val Tertiary = Color(0xFF006C49)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFF00B07A)
val OnTertiaryContainer = Color(0xFF003B26)

val ErrorColor = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)

val Background = Color(0xFFF7F9FB)
val OnBackground = Color(0xFF191C1E)

val Surface = Color(0xFFF7F9FB)
val OnSurface = Color(0xFF191C1E)
val SurfaceVariant = Color(0xFFE0E3E5)
val OnSurfaceVariant = Color(0xFF584237)
val SurfaceTint = Color(0xFF9D4300)

val SurfaceDim = Color(0xFFD8DADC)
val SurfaceBright = Color(0xFFF7F9FB)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF2F4F6)
val SurfaceContainer = Color(0xFFECEEF0)
val SurfaceContainerHigh = Color(0xFFE6E8EA)
val SurfaceContainerHighest = Color(0xFFE0E3E5)

val Outline = Color(0xFF8C7164)
val OutlineVariant = Color(0xFFE0C0B1)
val InverseSurface = Color(0xFF2D3133)
val InverseOnSurface = Color(0xFFEFF1F3)

// "Fixed" accent tokens — not routed through M3's ColorScheme (not part of the Material3
// version bundled with this project's Compose BOM); used directly by composables that need
// them verbatim, e.g. badge/pill/chip backgrounds and the round-trip toggle's glow label.
val PrimaryFixed = Color(0xFFFFDBCA)
val PrimaryFixedDim = Color(0xFFFFB690)
val OnPrimaryFixed = Color(0xFF341100)
val OnPrimaryFixedVariant = Color(0xFF783200)

val SecondaryFixed = Color(0xFFDAE2FD)
val SecondaryFixedDim = Color(0xFFBEC6E0)
val OnSecondaryFixed = Color(0xFF131B2E)
val OnSecondaryFixedVariant = Color(0xFF3F465C)

val TertiaryFixed = Color(0xFF6FFBBE)
val TertiaryFixedDim = Color(0xFF4EDEA3)
val OnTertiaryFixed = Color(0xFF002113)
val OnTertiaryFixedVariant = Color(0xFF005236)

// Dark-scheme surfaces (not specified by the design system — derived the same way the app's
// previous theme did: same accent hues, darker neutral surfaces).
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF121212)
val DarkOnSurface = Color(0xFFEFF1F3)
