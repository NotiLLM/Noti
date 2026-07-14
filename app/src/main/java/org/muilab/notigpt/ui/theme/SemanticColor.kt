package org.muilab.notigpt.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-specific semantic colors that Material's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * These encode *meaning*, not chrome: section identity (Tasks / Keep / Notifications), smart-filter
 * identity (Today / Upcoming), and deadline urgency. They stay fixed regardless of dynamic color so the
 * same color always means the same thing. Access via [LocalNotiColors] or the
 * [org.muilab.notigpt.ui.theme.NotiTheme] object accessor.
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
    // Smart-filter identity (Home screen) — distinct from Task/Keep so the two groupings never collide.
    val todayAccent: Color,
    val todayContainer: Color,
    val onTodayContainer: Color,
    val upcomingAccent: Color,
    val upcomingContainer: Color,
    val onUpcomingContainer: Color,
    // Deadline urgency.
    val overdue: Color,
    val overdueContainer: Color,
    val onOverdueContainer: Color,
    val dueSoon: Color,
    val dueSoonContainer: Color,
    val onDueSoonContainer: Color,
    // Favoriting — its own hue, distinct from due-soon urgency even though both used to share orange.
    val starred: Color,
    val starredContainer: Color,
    val onStarredContainer: Color,
)

val LightNotiColors = NotiSemanticColors(
    taskAccent = LightTaskAccent,
    taskContainer = LightTaskContainer,
    onTaskContainer = LightOnTaskContainer,
    keepAccent = LightKeepAccent,
    keepContainer = LightKeepContainer,
    onKeepContainer = LightOnKeepContainer,
    todayAccent = LightTodayAccent,
    todayContainer = LightTodayContainer,
    onTodayContainer = LightOnTodayContainer,
    upcomingAccent = LightUpcomingAccent,
    upcomingContainer = LightUpcomingContainer,
    onUpcomingContainer = LightOnUpcomingContainer,
    overdue = LightOverdue,
    overdueContainer = LightOverdueContainer,
    onOverdueContainer = LightOnOverdueContainer,
    dueSoon = LightDueSoon,
    dueSoonContainer = LightDueSoonContainer,
    onDueSoonContainer = LightOnDueSoonContainer,
    starred = LightStarred,
    starredContainer = LightStarredContainer,
    onStarredContainer = LightOnStarredContainer,
)

val DarkNotiColors = NotiSemanticColors(
    taskAccent = DarkTaskAccent,
    taskContainer = DarkTaskContainer,
    onTaskContainer = DarkOnTaskContainer,
    keepAccent = DarkKeepAccent,
    keepContainer = DarkKeepContainer,
    onKeepContainer = DarkOnKeepContainer,
    todayAccent = DarkTodayAccent,
    todayContainer = DarkTodayContainer,
    onTodayContainer = DarkOnTodayContainer,
    upcomingAccent = DarkUpcomingAccent,
    upcomingContainer = DarkUpcomingContainer,
    onUpcomingContainer = DarkOnUpcomingContainer,
    overdue = DarkOverdue,
    overdueContainer = DarkOverdueContainer,
    onOverdueContainer = DarkOnOverdueContainer,
    dueSoon = DarkDueSoon,
    dueSoonContainer = DarkDueSoonContainer,
    onDueSoonContainer = DarkOnDueSoonContainer,
    starred = DarkStarred,
    starredContainer = DarkStarredContainer,
    onStarredContainer = DarkOnStarredContainer,
)

/** Provides [NotiSemanticColors] down the tree. Defaults to dark; [NotiTheme] overrides per light/dark. */
val LocalNotiColors = staticCompositionLocalOf { DarkNotiColors }
