package org.muilab.notigpt.domain.saveditem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType

class SavedItemMergePolicyTest {
    @Test
    fun `rejects task and keep merge`() {
        assertNull(
            SavedItemMergePolicy.preservedUserState(
                listOf(item("task", SavedItemType.Task), item("keep", SavedItemType.Keep)),
            )
        )
    }

    @Test
    fun `rejects conflicting user-set when values`() {
        assertNull(
            SavedItemMergePolicy.preservedUserState(
                listOf(item("a", whenAtMs = 100L), item("b", whenAtMs = 200L)),
            )
        )
    }

    @Test
    fun `preserves sole when star and manual edit provenance`() {
        val result = SavedItemMergePolicy.preservedUserState(
            listOf(
                item("a", whenAtMs = 0L, isStarred = true),
                item("b", whenAtMs = 200L, userEdited = true),
            )
        )!!

        assertEquals(200L, result.whenAtMs)
        assertTrue(result.isStarred)
        assertTrue(result.userEdited)
    }

    private fun item(
        id: String,
        type: String = SavedItemType.Task,
        whenAtMs: Long = 0L,
        isStarred: Boolean = false,
        userEdited: Boolean = false,
    ) = SavedItem(
        savedItemId = id,
        title = id,
        itemType = type,
        lastUpdateTimestamp = 1L,
        deadlineAtMs = 0L,
        origin = "manual",
        humanEditCount = 0,
        userEdited = userEdited,
        isStarred = isStarred,
        whenAtMs = whenAtMs,
    )
}
