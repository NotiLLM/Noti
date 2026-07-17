package org.muilab.notigpt.data.remote.firestore

/**
 * Central Firestore collection/document path builder for synced app data.
 *
 * Keep path construction here so repositories and sync adapters do not duplicate collection names or user-scope
 * conventions.
 */
internal object FirestorePaths {
    const val COLLECTION_USERS = "users"

    const val COLLECTION_GENERATED_PROPOSALS_ROOT = "generatedProposals"

    const val SUBCOLLECTION_SAVED_ITEMS = "savedItems"
    const val SUBCOLLECTION_PROPOSALS = "proposals"

    /** Pre-v50 SavedItem location. It never contained scheduled push reminders. */
    const val LEGACY_COLLECTION_SAVED_ITEMS_ROOT = "reminders"
    const val LEGACY_SUBCOLLECTION_SAVED_ITEMS = "reminders"

    /** Legacy raw-notification subcollection name, retained only for the separately gated cleanup job. */
    const val SUBCOLLECTION_NOTIS = "notis"
}
