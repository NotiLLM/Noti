package org.muilab.notigpt.domain.saveditem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType

class SavedItemMergePolicyTest {
    @Test
    fun `rejects todo and keep merge`() {
        assertNull(
            SavedItemMergePolicy.preservedUserState(
                listOf(item("task", SavedItemType.Todo), item("keep", SavedItemType.Keep)),
            )
        )
    }

    @Test
    fun `preserves star and manual edit provenance`() {
        val result = SavedItemMergePolicy.preservedUserState(
            listOf(
                item("a", isStarred = true),
                item("b", userEdited = true),
            )
        )!!

        assertTrue(result.isStarred)
        assertTrue(result.userEdited)
    }

    private fun item(
        id: String,
        type: String = SavedItemType.Todo,
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
    )
}
