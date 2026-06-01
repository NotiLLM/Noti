package org.muilab.notigpt.domain.reminder

/**
 * Canonical status strings for reminder snapshot and extraction state.
 *
 * Keep backend/persistence-facing status values centralized here. UI labels should translate these values rather
 * than introducing alternate strings.
 */
object ReminderSnapshotStatuses {
    const val STAGED = "STAGED"
    const val KEPT = "KEPT"
    const val DISCARDED = "DISCARDED"
}
