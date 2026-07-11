package org.muilab.notigpt.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ReminderAssociationMerger.filterValidEvidence], the pure evidence-guard logic
 * used by the extraction handler when applying contract-v2 ops.
 *
 * (The JSON-reading [ReminderAssociationMerger.evidenceIdsFrom] relies on android's org.json, which is a
 * non-functional stub under plain JVM unit tests, so it is exercised by instrumented tests instead.)
 */
class ReminderAssociationMergerTest {

    @Test
    fun `filterValidEvidence keeps only ids present in the request`() {
        val valid = ReminderAssociationMerger.filterValidEvidence(
            citedIds = listOf("a_1", "hallucinated_9", "b_2"),
            validRequestIds = setOf("a_1", "b_2", "c_3"),
        )
        assertEquals(setOf("a_1", "b_2"), valid)
    }

    @Test
    fun `filterValidEvidence drops blanks and trims whitespace`() {
        val valid = ReminderAssociationMerger.filterValidEvidence(
            citedIds = listOf(" a_1 ", "", "  "),
            validRequestIds = setOf("a_1"),
        )
        assertEquals(setOf("a_1"), valid)
    }

    @Test
    fun `filterValidEvidence returns empty when nothing cited is real`() {
        val valid = ReminderAssociationMerger.filterValidEvidence(
            citedIds = listOf("x_1", "y_2"),
            validRequestIds = setOf("a_1"),
        )
        assertEquals(emptySet<String>(), valid)
    }

    @Test
    fun `filterValidEvidence returns empty for empty citation list`() {
        val valid = ReminderAssociationMerger.filterValidEvidence(
            citedIds = emptyList(),
            validRequestIds = setOf("a_1"),
        )
        assertEquals(emptySet<String>(), valid)
    }
}
