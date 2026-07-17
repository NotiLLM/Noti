package org.muilab.notigpt.domain.saveditem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem
import java.util.Locale

class SavedItemNormalizationTest {
    @Test
    fun `task to keep merges deadline and subtasks while preserving when`() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val item = SavedItem(
                savedItemId = "item",
                title = "Title",
                content = "Body",
                itemType = SavedItemType.Task,
                lastUpdateTimestamp = 1,
                deadlineAtMs = 1_700_000_000_000,
                origin = "manual",
                humanEditCount = 0,
                userEdited = true,
                whenAtMs = 1_800_000_000_000,
            )
            val result = SavedItemNormalization.convertTaskToKeep(
                item,
                listOf(
                    SavedSubItem("a", "item", "Done", true, 0),
                    SavedSubItem("b", "item", "Next", false, 1),
                ),
            )

            assertEquals(SavedItemType.Keep, result.item.itemType)
            assertEquals(0L, result.item.deadlineAtMs)
            assertEquals(item.whenAtMs, result.item.whenAtMs)
            assertTrue(result.item.content.contains("Deadline:"))
            assertTrue(result.item.content.contains("☑ Done"))
            assertTrue(result.item.content.contains("☐ Next"))
            assertTrue(result.subItems.isEmpty())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `subtask text is one logical line but remains otherwise editable`() {
        val normalized = SavedSubItem.normalizeText("  first\nsecond\r\nthird  ")
        assertEquals("first second third", normalized)
        assertFalse(normalized.contains('\n'))
    }
}
