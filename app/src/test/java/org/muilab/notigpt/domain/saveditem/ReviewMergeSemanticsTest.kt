package org.muilab.notigpt.domain.saveditem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.model.features.TodoStep

class ReviewMergeSemanticsTest {
    @Test
    fun `target row and completion state win normalized duplicate`() {
        val rows = mutableListOf(sub("target", "  Book   venue ", completed = true))

        assertFalse(ReviewMergeSemantics.appendUnique(rows, sub("source", "book venue", completed = false)))

        assertEquals(listOf("target"), rows.map { it.todoStepId })
        assertTrue(rows.single().isCompleted)
    }

    @Test
    fun `sources then llm additions remain ordered and unique`() {
        val rows = mutableListOf(sub("target", "Confirm date"))

        assertTrue(ReviewMergeSemantics.appendUnique(rows, sub("source", "Book venue")))
        assertTrue(ReviewMergeSemantics.appendUnique(rows, sub("llm", "Send invitations")))
        assertFalse(ReviewMergeSemantics.appendUnique(rows, sub("duplicate", "BOOK VENUE")))

        assertEquals(listOf("target", "source", "llm"), rows.map { it.todoStepId })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
    }

    private fun sub(id: String, text: String, completed: Boolean = false) = TodoStep(
        todoStepId = id,
        parentSavedItemId = "target",
        text = text,
        isCompleted = completed,
    )
}
