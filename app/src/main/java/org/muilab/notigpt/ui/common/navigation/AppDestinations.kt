package org.muilab.notigpt.ui.common.navigation

/**
 * A pushed destination within the home area (the region that used to be the New/Todos/Keep bottom
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

    /**
     * A filtered saved-item list (a smart filter, or the Todos/Keep collections). [focusItemId], when
     * set, opens that item's detail screen immediately on top of the list (e.g. jumping in from a
     * notification's linked-items sheet).
     */
    data class SavedList(val filter: SavedListFilter, val focusItemId: String? = null) : HomeDestination
}

/**
 * Entry points into a saved-item list from the home screen. Attention filters mix todos and keeps
 * unless their definition is type-specific; [Todos] and [Keep] open the full collections.
 */
enum class SavedListFilter {
    Suggested,
    Starred,
    DueSoon,
    RecentlyUpdated,
    AllItems,
    Todos,
    Keep,
}

/** Secondary app sections opened from the hamburger drawer. */
enum class AppMenuScreen {
    Reminders,
    Preferences,
    History,
    Settings,
}
