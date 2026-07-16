package org.muilab.notigpt.data.remote.firestore

/**
 * Central Firestore collection/document path builder for synced app data.
 *
 * Keep path construction here so repositories and sync adapters do not duplicate collection names or user-scope
 * conventions.
 */
internal object FirestorePaths {
    const val COLLECTION_USERS = "users"

    const val COLLECTION_REMINDERS_ROOT = "reminders"
    const val COLLECTION_GENERATED_PROPOSALS_ROOT = "generatedProposals"

    const val SUBCOLLECTION_REMINDERS = "reminders"
    const val SUBCOLLECTION_PROPOSALS = "proposals"

    /** Legacy raw-notification subcollection name, retained only for the separately gated cleanup job. */
    const val SUBCOLLECTION_NOTIS = "notis"
}
