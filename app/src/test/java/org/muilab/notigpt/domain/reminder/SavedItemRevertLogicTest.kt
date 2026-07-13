package org.muilab.notigpt.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure rollback logic used when a user rejects a pending LLM update.
 */
class SavedItemRevertLogicTest {

    private fun section(label: String, time: String, fragment: String) = "\n\n[$label — $time]\n$fragment"

    @Test
    fun stripUpdateSection_removesExactSection() {
        val base = "Original content"
        val frag = "Meeting moved to 4pm"
        val exact = section("Update", "2026-07-13 09:00", frag)
        val content = base + exact

        val result = SavedItemRevertLogic.stripUpdateSection(content, frag, exact)

        assertEquals(base, result)
    }

    @Test
    fun stripUpdateSection_stripsNewestFirstForStackedSections() {
        val base = "Original content"
        val first = section("Update", "2026-07-13 09:00", "First update")
        val second = section("Update", "2026-07-13 10:00", "Second update")
        val content = base + first + second

        // LIFO: strip the newest (last appended) section first.
        val afterSecond = SavedItemRevertLogic.stripUpdateSection(content, "Second update", second)
        assertEquals(base + first, afterSecond)

        val afterFirst = SavedItemRevertLogic.stripUpdateSection(afterSecond!!, "First update", first)
        assertEquals(base, afterFirst)
    }

    @Test
    fun stripUpdateSection_fallsBackWhenHeaderLabelChanged() {
        // Section was appended in Chinese, but the exact rebuild now uses the English label
        // (language preference changed since the append). Fallback still strips the section.
        val base = "Original content"
        val frag = "Deadline extended"
        val appended = section("更新", "2026-07-13 09:00", frag)
        val content = base + appended
        val exactWithWrongLabel = section("Update", "2026-07-13 09:00", frag)

        val result = SavedItemRevertLogic.stripUpdateSection(content, frag, exactWithWrongLabel)

        assertEquals(base, result)
    }

    @Test
    fun stripUpdateSection_returnsNullWhenFragmentGone() {
        val content = "The user rewrote everything and the fragment no longer appears"
        val result = SavedItemRevertLogic.stripUpdateSection(content, "some missing fragment", "\n\n[Update — x]\nsome missing fragment")
        assertNull(result)
    }

    @Test
    fun stripUpdateSection_blankFragmentLeavesContentUnchanged() {
        val content = "Unchanged"
        assertEquals(content, SavedItemRevertLogic.stripUpdateSection(content, "", null))
    }

    @Test
    fun revertField_restoresOldWhenCurrentMatchesNew() {
        assertEquals("old title", SavedItemRevertLogic.revertField(current = "new title", newValue = "new title", old = "old title"))
    }

    @Test
    fun revertField_keepsCurrentWhenUserEditedAfterLlm() {
        // User edited the title after the LLM set it; current no longer equals the LLM's new value.
        assertEquals("user's title", SavedItemRevertLogic.revertField(current = "user's title", newValue = "new title", old = "old title"))
    }

    @Test
    fun revertField_worksForTimestamps() {
        assertEquals(100L, SavedItemRevertLogic.revertField(current = 200L, newValue = 200L, old = 100L))
        assertEquals(999L, SavedItemRevertLogic.revertField(current = 999L, newValue = 200L, old = 100L))
    }
}
