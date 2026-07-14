package org.muilab.notigpt.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens for the NotiGPT custom theme — modeled on Apple's iOS system-color and grouped-list
 * conventions rather than Material baseline colors, so every screen (already fully tokenized through
 * these values + [NotiSemanticColors]) shifts to this look with no per-screen edits.
 *
 * Surface-role policy: canvas is a soft grey in light mode (`surface`) with white grouped cards
 * (`surfaceContainerLow`/`surfaceContainer`), mirroring iOS Settings-style grouped lists. Dark mode goes
 * true black canvas with dark-grey cards (the iOS OLED convention). Section/urgency meaning is carried by
 * colored icons/discs on these neutral bodies, not by tinted container fills — reserve those for at most
 * one hero element per screen.
 *
 * Hue assignments are chosen by actual hue-wheel angle, not just by borrowing distinct Apple system-color
 * *names* — two named colors can still sit close enough on the wheel to read as "the same family." Current
 * angles: overdue red ~3°, due-soon orange ~34°, starred gold ~50°, keep green ~135°, today blue ~211°,
 * task indigo ~241°, upcoming magenta ~300°. Task and Upcoming used to both live in the blue-violet family
 * (indigo ~241° vs. Apple's raw systemPurple ~278°, only 37° apart) and read as near-duplicates on a dark
 * card; Upcoming was moved to magenta (~300°, the middle of the otherwise-empty 211°→360° span) so it's
 * ~60° clear of both indigo and red instead of sitting inside indigo's own family.
 */

// ── Light scheme — iOS grouped-list canvas + systemBlue primary ─────────────
val LightPrimary = Color(0xFF007AFF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE1F0FF)
val LightOnPrimaryContainer = Color(0xFF003A75)
val LightSecondary = Color(0xFF6C6C70)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE4E4E8)
val LightOnSecondaryContainer = Color(0xFF3C3C43)
val LightTertiary = Color(0xFF8E3FBF)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF1E1FB)
val LightOnTertiaryContainer = Color(0xFF4A1766)
val LightError = Color(0xFFD9342B)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD8)
val LightOnErrorContainer = Color(0xFF5C0A08)
val LightBackground = Color(0xFFF2F2F4)
val LightOnBackground = Color(0xFF1C1C1E)
val LightSurface = Color(0xFFF2F2F4)
val LightOnSurface = Color(0xFF1C1C1E)
val LightSurfaceVariant = Color(0xFFE4E4E8)
val LightOnSurfaceVariant = Color(0xFF6C6C70)
val LightOutline = Color(0xFFC6C6C8)
val LightOutlineVariant = Color(0xFFE4E4E8)
val LightSurfaceDim = Color(0xFFE4E4E8)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFFFFFFF)
val LightSurfaceContainerHigh = Color(0xFFEBEBF0)
val LightSurfaceContainerHighest = Color(0xFFE3E3E8)
val LightInverseSurface = Color(0xFF2C2C2E)
val LightInverseOnSurface = Color(0xFFF2F2F7)
val LightInversePrimary = Color(0xFF0A84FF)
val LightScrim = Color(0xFF000000)

// ── Dark scheme — true-black OLED canvas + systemBlue primary ───────────────
val DarkPrimary = Color(0xFF0A84FF)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF003A75)
val DarkOnPrimaryContainer = Color(0xFFCFE4FF)
val DarkSecondary = Color(0xFF98989D)
val DarkOnSecondary = Color(0xFF1C1C1E)
val DarkSecondaryContainer = Color(0xFF2C2C2E)
val DarkOnSecondaryContainer = Color(0xFFD1D1D6)
val DarkTertiary = Color(0xFFBF5AF2)
val DarkOnTertiary = Color(0xFF3B0A57)
val DarkTertiaryContainer = Color(0xFF4A1766)
val DarkOnTertiaryContainer = Color(0xFFF1E1FB)
val DarkError = Color(0xFFFF453A)
val DarkOnError = Color(0xFF2D0000)
val DarkErrorContainer = Color(0xFF5C0A08)
val DarkOnErrorContainer = Color(0xFFFFDAD8)
val DarkBackground = Color(0xFF000000)
val DarkOnBackground = Color(0xFFF2F2F7)
val DarkSurface = Color(0xFF000000)
val DarkOnSurface = Color(0xFFF2F2F7)
val DarkSurfaceVariant = Color(0xFF3A3A3C)
val DarkOnSurfaceVariant = Color(0xFF98989D)
val DarkOutline = Color(0xFF545458)
val DarkOutlineVariant = Color(0xFF3A3A3C)
val DarkSurfaceDim = Color(0xFF000000)
val DarkSurfaceBright = Color(0xFF242426)
val DarkSurfaceContainerLowest = Color(0xFF000000)
val DarkSurfaceContainerLow = Color(0xFF1C1C1E)
val DarkSurfaceContainer = Color(0xFF1C1C1E)
val DarkSurfaceContainerHigh = Color(0xFF2C2C2E)
val DarkSurfaceContainerHighest = Color(0xFF3A3A3C)
val DarkInverseSurface = Color(0xFFF2F2F7)
val DarkInverseOnSurface = Color(0xFF1C1C1E)
val DarkInversePrimary = Color(0xFF007AFF)
val DarkScrim = Color(0xFF000000)

// ── Semantic accents — fixed meaning, independent of the neutral scheme ───────
// Each concept gets its own hue, spaced by wheel angle (see file header), so nothing reads as ambiguous:
// task=indigo, keep=green, today=blue, upcoming=magenta, overdue=red, due-soon=orange, starred=gold.

// Light
val LightTaskAccent = Color(0xFF5856D6)
val LightTaskContainer = Color(0xFFE4E3FB)
val LightOnTaskContainer = Color(0xFF2A2875)
val LightKeepAccent = Color(0xFF248A3D)
val LightKeepContainer = Color(0xFFD4F5DE)
val LightOnKeepContainer = Color(0xFF0F3D1B)
val LightTodayAccent = Color(0xFF007AFF)
val LightTodayContainer = Color(0xFFE1F0FF)
val LightOnTodayContainer = Color(0xFF003A75)
// Magenta/orchid, hue ~304° — moved off the indigo-adjacent ~278° purple so it no longer shares
// Task's blue-violet family (see file header). Verified ~6.2:1 on white.
val LightUpcomingAccent = Color(0xFFAA1EA0)
val LightUpcomingContainer = Color(0xFFFBE0F7)
val LightOnUpcomingContainer = Color(0xFF66175E)
val LightOverdue = Color(0xFFD9342B)
val LightOverdueContainer = Color(0xFFFFDAD8)
val LightOnOverdueContainer = Color(0xFF5C0A08)
val LightDueSoon = Color(0xFFA15C00)
val LightDueSoonContainer = Color(0xFFFFE8CC)
val LightOnDueSoonContainer = Color(0xFF4A2800)
// Gold/yellow, hue ~50° — clearly apart from due-soon's ~34° amber so the two urgency-adjacent
// concepts (favorited vs. about-to-be-due) don't visually collide.
val LightStarred = Color(0xFF8C6D00)
val LightStarredContainer = Color(0xFFFFF3C4)
val LightOnStarredContainer = Color(0xFF4A3900)

// Dark
// Brightened past Apple's raw systemIndigo (0x5E5CE6) — at labelMedium size, that value only reaches
// ~4:1 against surfaceContainerHigh, which reads as unreadable on real displays. 0xFFA3A1FF holds
// ~6-7:1 against every dark surface tier used behind it.
val DarkTaskAccent = Color(0xFFA3A1FF)
val DarkTaskContainer = Color(0xFF2E2A6B)
val DarkOnTaskContainer = Color(0xFFE4E3FB)
// Apple's light-mode green (0x34C759) reused here instead of the neon dark-mode value (0x30D158) —
// same contrast headroom, noticeably less "glowing" on a black canvas.
val DarkKeepAccent = Color(0xFF34C759)
val DarkKeepContainer = Color(0xFF123820)
val DarkOnKeepContainer = Color(0xFFB6F0C4)
val DarkTodayAccent = Color(0xFF0A84FF)
val DarkTodayContainer = Color(0xFF003A75)
val DarkOnTodayContainer = Color(0xFFCFE4FF)
// Magenta/orchid, hue ~300° — shifted off Apple's raw dark systemPurple (0xBF5AF2, hue ~280°, which
// sat only 39° from Task's indigo at ~241° and read as "darker blue-violet," not a distinct concept).
// Also lightened past that raw value since fully saturated blue-violets look muddier than their measured
// contrast suggests (Helmholtz–Kohlrausch effect). Verified ~10.4:1 on black, ~7.0:1 on
// DarkSurfaceContainerHigh.
val DarkUpcomingAccent = Color(0xFFFF8CFF)
val DarkUpcomingContainer = Color(0xFF8A2E8A)
val DarkOnUpcomingContainer = Color(0xFFFBE0F7)
val DarkOverdue = Color(0xFFFF6961)
val DarkOverdueContainer = Color(0xFF5C0A08)
val DarkOnOverdueContainer = Color(0xFFFFDAD8)
val DarkDueSoon = Color(0xFFFF9F0A)
val DarkDueSoonContainer = Color(0xFF4A2800)
val DarkOnDueSoonContainer = Color(0xFFFFE8CC)
val DarkStarred = Color(0xFFFFD60A)
val DarkStarredContainer = Color(0xFF4A3900)
val DarkOnStarredContainer = Color(0xFFFFE8A3)
