package org.muilab.notigpt.model.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.model.notifications.NotiRecord

class SavedItemAndReminderContractTest {

    @Test
    fun savedItemType_usesTodoAndKeepAsStableRoomValues() {
        assertEquals("todo", SavedItemType.Todo)
        assertEquals("keep", SavedItemType.Keep)
    }

    @Test
    fun reminderStatus_usesDueUnseenInsteadOfMissed() {
        assertEquals("scheduled", ReminderStatus.Scheduled)
        assertEquals("due_unseen", ReminderStatus.DueUnseen)
        assertEquals("seen", ReminderStatus.Seen)
        assertEquals("cancelled", ReminderStatus.Cancelled)
    }

    @Test
    fun reminderSourceType_keepsUnifiedReminderSourceValues() {
        assertEquals("saved_item", ReminderSourceType.SavedItem)
        assertEquals("noti_record_set", ReminderSourceType.NotiRecordSet)
    }

    @Test
    fun savedItemState_keepsInboxLifecycleValuesStable() {
        assertEquals("new", SavedItemState.New)
        assertEquals("updated", SavedItemState.Updated)
        assertEquals("saved", SavedItemState.Saved)
        assertEquals("completed", SavedItemState.Completed)
        assertEquals("archived", SavedItemState.Archived)
    }

    @Test
    fun savedItemState_helpersSeparateNewFromMainLists() {
        assertTrue(SavedItemState.isNewLike(SavedItemState.New))
        assertTrue(SavedItemState.isNewLike(SavedItemState.Updated))
        assertFalse(SavedItemState.isNewLike(SavedItemState.Saved))
        assertTrue(SavedItemState.isTodoListState(SavedItemState.Completed))
        assertTrue(SavedItemState.isKeepListState(SavedItemState.Archived))
    }

    @Test
    fun notiRecord_defaultsToActiveForInbox() {
        val record = NotiRecord(
            notiRecordId = "key_1",
            notiKey = "key",
            whenTime = 1L,
            postTime = 1L,
        )

        assertFalse(record.isDismissed)
    }
}
