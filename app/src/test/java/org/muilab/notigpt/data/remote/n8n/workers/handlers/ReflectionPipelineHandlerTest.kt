package org.muilab.notigpt.data.remote.n8n.workers.handlers

import org.junit.Assert.assertEquals
import org.junit.Test
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository

class ReflectionPipelineHandlerTest {

    @Test
    fun normalizeGroups_coalescesOverlapsAndKeepsD2StrengthOrder() {
        val result = ReflectionPipelineHandler.normalizeGroups(
            groups = listOf(listOf("a", "b"), listOf("c", "d"), listOf("b", "c")),
            limit = 5,
        )

        assertEquals(listOf(listOf("a", "b", "c", "d")), result)
    }

    @Test
    fun normalizeGroups_capsIndependentGroups() {
        val result = ReflectionPipelineHandler.normalizeGroups(
            groups = listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f")),
            limit = 2,
        )

        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), result)
    }

    @Test
    fun normalizeGroups_overlapRetainsEarliestStrengthPosition() {
        val result = ReflectionPipelineHandler.normalizeGroups(
            groups = listOf(listOf("a", "b"), listOf("x", "y"), listOf("b", "c")),
            limit = 1,
        )

        assertEquals(listOf(listOf("a", "b", "c")), result)
    }

    @Test
    fun approvalReflectionIds_areActualCreatedItemsOrSurvivingTarget() {
        assertEquals(
            listOf("split-a", "split-b"),
            PendingProposedOpRepository.reflectionItemIds(
                createdItemIds = listOf("split-a", "split-b"),
                appliedItemId = "deleted-source",
            ),
        )
        assertEquals(
            listOf("merge-survivor"),
            PendingProposedOpRepository.reflectionItemIds(
                createdItemIds = emptyList(),
                appliedItemId = "merge-survivor",
            ),
        )
    }
}
