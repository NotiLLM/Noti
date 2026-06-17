package org.muilab.notigpt.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ReminderAssociationMerger.merge], the pure link-derivation logic used by the
 * extraction/regeneration handlers after the snapshot mechanism was replaced by the
 * [org.muilab.notigpt.model.features.NotiSavedItemLink] table.
 *
 * (The JSON-reading [ReminderAssociationMerger.associationIdsFrom] relies on android's org.json, which is a
 * non-functional stub under plain JVM unit tests, so it is exercised by instrumented tests instead.)
 */
class ReminderAssociationMergerTest {

    @Test
    fun `merge unions existing links, request, and response ids and drops blanks`() {
        val merged = ReminderAssociationMerger.merge(
            existingRecordIds = setOf("a_1"),
            responseAssociationIds = setOf("c_3", ""),
            requestRecordIds = setOf("b_2", " "),
        )
        assertEquals(setOf("a_1", "b_2", "c_3"), merged)
    }

    @Test
    fun `merge with only existing links preserves them`() {
        val merged = ReminderAssociationMerger.merge(
            existingRecordIds = setOf("a_1", "a_2"),
            responseAssociationIds = emptySet(),
        )
        assertEquals(setOf("a_1", "a_2"), merged)
    }

    @Test
    fun `merge with only response ids returns them`() {
        val merged = ReminderAssociationMerger.merge(
            existingRecordIds = emptySet(),
            responseAssociationIds = setOf("c_3"),
            requestRecordIds = emptySet(),
        )
        assertEquals(setOf("c_3"), merged)
    }

    @Test
    fun `merge returns empty when all inputs empty`() {
        val merged = ReminderAssociationMerger.merge(
            existingRecordIds = emptySet(),
            responseAssociationIds = emptySet(),
        )
        assertEquals(emptySet<String>(), merged)
    }
}
