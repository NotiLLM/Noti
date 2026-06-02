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

    const val SUBCOLLECTION_REMINDERS = "reminders"

    const val SUBCOLLECTION_NOTIS = "notis"
}

