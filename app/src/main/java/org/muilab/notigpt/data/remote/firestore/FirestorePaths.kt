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

    // Invitation-entitlement and usage-observability collections (see
    // plans/3-invitation-and-llm-usage.md) are deliberately top-level, not nested under
    // COLLECTION_USERS: the existing users/{uid} security rule grants the owner unrestricted
    // read/write over that whole subtree, which unrelated sync features already depend on. These
    // narrower collections each carry their own small, independently auditable rule instead of
    // widening or forking that broad rule.
    const val COLLECTION_ENTITLEMENTS = "entitlements"
    const val COLLECTION_INVITATION_CODES = "invitationCodes"
    const val COLLECTION_USAGE_LOGS = "usageLogs"
    const val COLLECTION_NOTIFICATION_USAGE = "notificationUsage"
}
