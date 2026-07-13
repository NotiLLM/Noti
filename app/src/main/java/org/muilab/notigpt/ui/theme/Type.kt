package org.muilab.notigpt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Full Material 3 type scale for NotiGPT.
 *
 * Uses the system default font family (best CJK coverage via Noto fallback for the app's mixed
 * English/Chinese content). The value here is a complete, deliberate weight/size hierarchy so titles,
 * body, and metadata read distinctly — previously only [Typography.bodyLarge] was defined.
 */
private val Default = FontFamily.Default

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)

/**
 * App-level named styles built on the M3 scale.
 *
 * These exist so screens stop applying ad-hoc `fontWeight = FontWeight.SemiBold/Bold` overrides: a
 * card title or a stat number is one named style, retunable in one place. Reference as
 * `NotiType.cardTitle` etc. inside composables.
 */
object NotiType {
    /** Card headline (NotiCard / ReminderCard title). Slightly heavier than titleMedium. */
    val cardTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** Big count in a stat box / smart-filter tile. */
    val statNumber: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)

    /** Metadata line (timestamp, counts) — pair with `onSurfaceVariant`. */
    val metadata: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium
}
