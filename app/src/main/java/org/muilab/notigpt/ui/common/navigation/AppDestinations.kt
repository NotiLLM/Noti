package org.muilab.notigpt.ui.common.navigation

/**
 * A pushed destination within the home area (the region that used to be the New/Tasks/Keep bottom
 * tabs). The home overview is the root; everything else is pushed onto a small back stack held by
 * AppScaffold. Kept as a lightweight sealed model rather than Navigation-Compose to match the app's
 * existing ad-hoc navigation style.
 */
sealed interface HomeDestination {
    /** The overview: review row, notification sections, saved-item smart filters. */
    data object Home : HomeDestination

    /** The Tinder-style review stack for new/updated generated items. */
    data object Review : HomeDestination

    /** A full-screen NotiCard list for one notification category ([category] = NotiCategory.*). */
    data class NotiList(val category: String) : HomeDestination

    /** A filtered saved-item list (a smart filter, or the Tasks/Keep collections). */
    data class SavedList(val filter: SavedListFilter) : HomeDestination
}

/**
 * Entry points into a saved-item list from the home screen. The first five are planned-date/star
 * smart filters (mixing tasks and keeps); [Tasks] and [Keep] open the type collections with their
 * own in-screen filter chips.
 */
enum class SavedListFilter {
    TodayEarlier,
    Upcoming,
    Someday,
    Undetermined,
    Starred,
    Tasks,
    Keep,
}

/** Secondary app sections opened from the hamburger drawer. */
enum class AppMenuScreen {
    Reminders,
    Preferences,
    History,
    Settings,
}
