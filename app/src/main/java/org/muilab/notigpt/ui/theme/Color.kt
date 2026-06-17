package org.muilab.notigpt.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens for the NotiGPT custom theme.
 *
 * The palette is intentionally neutral so that the *semantic* colors (section accents and deadline urgency,
 * see [NotiSemanticColors]) carry the meaning. A single calm indigo serves as the primary/selected-state hue.
 * Keep raw hex values here; map them into [androidx.compose.material3.ColorScheme] roles in Theme.kt.
 */

// ── Light scheme — neutral chrome + indigo primary ───────────────────────────
val LightPrimary = Color(0xFF3F5C9A)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD8E2FF)
val LightOnPrimaryContainer = Color(0xFF001A41)
val LightSecondary = Color(0xFF565E71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDAE2F9)
val LightOnSecondaryContainer = Color(0xFF131C2B)
val LightTertiary = Color(0xFF6F5575)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF9D8FD)
val LightOnTertiaryContainer = Color(0xFF28132F)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFFBFBFC)
val LightOnBackground = Color(0xFF1A1C1E)
val LightSurface = Color(0xFFFBFBFC)
val LightOnSurface = Color(0xFF1A1C1E)
val LightSurfaceVariant = Color(0xFFE1E2EC)
val LightOnSurfaceVariant = Color(0xFF44474E)
val LightOutline = Color(0xFF74777F)
val LightOutlineVariant = Color(0xFFC4C6CF)
val LightSurfaceDim = Color(0xFFDADAE0)
val LightSurfaceBright = Color(0xFFFBFBFC)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF4F3F8)
val LightSurfaceContainer = Color(0xFFEEEDF2)
val LightSurfaceContainerHigh = Color(0xFFE8E7EC)
val LightSurfaceContainerHighest = Color(0xFFE2E1E7)
val LightInverseSurface = Color(0xFF2F3033)
val LightInverseOnSurface = Color(0xFFF1F0F4)
val LightInversePrimary = Color(0xFFAEC6FF)
val LightScrim = Color(0xFF000000)

// ── Dark scheme — neutral chrome + indigo primary ────────────────────────────
val DarkPrimary = Color(0xFFAEC6FF)
val DarkOnPrimary = Color(0xFF002C6E)
val DarkPrimaryContainer = Color(0xFF234381)
val DarkOnPrimaryContainer = Color(0xFFD8E2FF)
val DarkSecondary = Color(0xFFBEC6DC)
val DarkOnSecondary = Color(0xFF283041)
val DarkSecondaryContainer = Color(0xFF3E4759)
val DarkOnSecondaryContainer = Color(0xFFDAE2F9)
val DarkTertiary = Color(0xFFDCBCE1)
val DarkOnTertiary = Color(0xFF3E2845)
val DarkTertiaryContainer = Color(0xFF563E5C)
val DarkOnTertiaryContainer = Color(0xFFF9D8FD)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF121316)
val DarkOnBackground = Color(0xFFE3E2E6)
val DarkSurface = Color(0xFF121316)
val DarkOnSurface = Color(0xFFE4E2E6)
val DarkSurfaceVariant = Color(0xFF44474E)
val DarkOnSurfaceVariant = Color(0xFFC4C7CF)
val DarkOutline = Color(0xFF8E9199)
val DarkOutlineVariant = Color(0xFF44474E)
val DarkSurfaceDim = Color(0xFF121316)
val DarkSurfaceBright = Color(0xFF38393E)
val DarkSurfaceContainerLowest = Color(0xFF0D0E11)
val DarkSurfaceContainerLow = Color(0xFF1A1B1F)
val DarkSurfaceContainer = Color(0xFF1E1F23)
val DarkSurfaceContainerHigh = Color(0xFF282A2E)
val DarkSurfaceContainerHighest = Color(0xFF333539)
val DarkInverseSurface = Color(0xFFE4E2E6)
val DarkInverseOnSurface = Color(0xFF2F3033)
val DarkInversePrimary = Color(0xFF3F5C9A)
val DarkScrim = Color(0xFF000000)

// ── Semantic accents — fixed meaning, independent of the neutral scheme ───────
// Sourced from the shared Amber / Teal / Red ramps. Section identity (Tasks=amber, Keep=teal,
// Notifications=neutral) and deadline urgency (overdue=red, soon=amber) read consistently in both modes.

// Light
val LightTaskAccent = Color(0xFFB26A00)
val LightTaskContainer = Color(0xFFFFDEA8)
val LightOnTaskContainer = Color(0xFF5B3A00)
val LightKeepAccent = Color(0xFF0F6E56)
val LightKeepContainer = Color(0xFF9FE1CB)
val LightOnKeepContainer = Color(0xFF04342C)
val LightOverdue = Color(0xFFA32D2D)
val LightOverdueContainer = Color(0xFFF7C1C1)
val LightOnOverdueContainer = Color(0xFF501313)
val LightDueSoon = Color(0xFF854F0B)
val LightDueSoonContainer = Color(0xFFFAC775)
val LightOnDueSoonContainer = Color(0xFF412402)

// Dark
val DarkTaskAccent = Color(0xFFFFC36B)
val DarkTaskContainer = Color(0xFF5B3A00)
val DarkOnTaskContainer = Color(0xFFFFDEA8)
val DarkKeepAccent = Color(0xFF5DCAA5)
val DarkKeepContainer = Color(0xFF085041)
val DarkOnKeepContainer = Color(0xFF9FE1CB)
val DarkOverdue = Color(0xFFF09595)
val DarkOverdueContainer = Color(0xFF791F1F)
val DarkOnOverdueContainer = Color(0xFFF7C1C1)
val DarkDueSoon = Color(0xFFEF9F27)
val DarkDueSoonContainer = Color(0xFF633806)
val DarkOnDueSoonContainer = Color(0xFFFAC775)
