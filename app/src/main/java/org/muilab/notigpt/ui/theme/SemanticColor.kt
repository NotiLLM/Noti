package org.muilab.notigpt.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-specific semantic colors that Material's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * These encode *meaning*, not chrome: section identity (Tasks / Keep / Notifications) and deadline urgency.
 * They stay fixed regardless of dynamic color so the same color always means the same thing. Access via
 * [LocalNotiColors] or the [org.muilab.notigpt.ui.theme.NotiTheme] object accessor.
 */
@Immutable
data class NotiSemanticColors(
    // Section identity. Notifications are intentionally neutral (no accent) — use onSurfaceVariant.
    val taskAccent: Color,
    val taskContainer: Color,
    val onTaskContainer: Color,
    val keepAccent: Color,
    val keepContainer: Color,
    val onKeepContainer: Color,
    // Deadline urgency.
    val overdue: Color,
    val overdueContainer: Color,
    val onOverdueContainer: Color,
    val dueSoon: Color,
    val dueSoonContainer: Color,
    val onDueSoonContainer: Color,
)

val LightNotiColors = NotiSemanticColors(
    taskAccent = LightTaskAccent,
    taskContainer = LightTaskContainer,
    onTaskContainer = LightOnTaskContainer,
    keepAccent = LightKeepAccent,
    keepContainer = LightKeepContainer,
    onKeepContainer = LightOnKeepContainer,
    overdue = LightOverdue,
    overdueContainer = LightOverdueContainer,
    onOverdueContainer = LightOnOverdueContainer,
    dueSoon = LightDueSoon,
    dueSoonContainer = LightDueSoonContainer,
    onDueSoonContainer = LightOnDueSoonContainer,
)

val DarkNotiColors = NotiSemanticColors(
    taskAccent = DarkTaskAccent,
    taskContainer = DarkTaskContainer,
    onTaskContainer = DarkOnTaskContainer,
    keepAccent = DarkKeepAccent,
    keepContainer = DarkKeepContainer,
    onKeepContainer = DarkOnKeepContainer,
    overdue = DarkOverdue,
    overdueContainer = DarkOverdueContainer,
    onOverdueContainer = DarkOnOverdueContainer,
    dueSoon = DarkDueSoon,
    dueSoonContainer = DarkDueSoonContainer,
    onDueSoonContainer = DarkOnDueSoonContainer,
)

/** Provides [NotiSemanticColors] down the tree. Defaults to dark; [NotiTheme] overrides per light/dark. */
val LocalNotiColors = staticCompositionLocalOf { DarkNotiColors }
