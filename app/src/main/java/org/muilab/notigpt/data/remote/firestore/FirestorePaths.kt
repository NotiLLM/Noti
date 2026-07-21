package org.muilab.notigpt.data.remote.firestore

/**
 * Central Firestore collection/document path builder for synced app data.
 *
 * Keep path construction here so repositories and sync adapters do not duplicate collection names or user-scope
 * conventions.
 */
internal object FirestorePaths {
    const val COLLECTION_USERS = "users"

    const val SUBCOLLECTION_SAVED_ITEMS = "savedItems"
    const val SUBCOLLECTION_PROPOSED_OP_RECORDS = "proposedOpRecords"
}
