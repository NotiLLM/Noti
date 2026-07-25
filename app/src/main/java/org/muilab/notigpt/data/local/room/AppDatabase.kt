package org.muilab.notigpt.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalSummary
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.GeneralPreference
import org.muilab.notigpt.model.features.NotiLlmState
import org.muilab.notigpt.model.features.NotiSavedItemLink
import org.muilab.notigpt.model.features.PendingProposedOp
import org.muilab.notigpt.model.features.PendingReviewDraft
import org.muilab.notigpt.model.features.RejectedMerge
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderNotiRecordRef
import org.muilab.notigpt.model.features.ReminderSavedItemRef
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.model.features.UserKnowledge
import org.muilab.notigpt.model.features.FirestoreOutboxOp
import org.muilab.notigpt.model.features.ProposedOpRecord
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.local.room.dao.ExtractionJournalDao
import org.muilab.notigpt.data.local.room.dao.ExtractionPreferenceDao
import org.muilab.notigpt.data.local.room.dao.GeneralPreferenceDao
import org.muilab.notigpt.data.local.room.dao.SavedItemChangeLogDao
import org.muilab.notigpt.data.local.room.dao.NotiActionDao
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiLlmStateDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.data.local.room.dao.NotiSavedItemLinkDao
import org.muilab.notigpt.data.local.room.dao.PendingProposedOpDao
import org.muilab.notigpt.data.local.room.dao.PendingReviewDraftDao
import org.muilab.notigpt.data.local.room.dao.RejectedMergeDao
import org.muilab.notigpt.data.local.room.dao.ReminderDao
import org.muilab.notigpt.data.local.room.dao.SavedItemDao
import org.muilab.notigpt.data.local.room.dao.TodoStepDao
import org.muilab.notigpt.data.local.room.dao.UserKnowledgeDao
import org.muilab.notigpt.data.local.room.dao.FirestoreOutboxDao
import org.muilab.notigpt.data.local.room.dao.ProposedOpRecordDao

/**
 * Room database entry point for local app state.
 *
 * Entities listed here are the durable local source of truth. External sync, export, and
 * analysis adapters should depend on this database instead of changing its ownership model.
 */
@Database(
    entities = [
        NotiUnit::class,
        NotiRecord::class,
        NotiAction::class,
        NotiLlmState::class,
        SavedItem::class,
        TodoStep::class,
        NotiSavedItemLink::class,
        SavedItemChangeLog::class,
        PendingProposedOp::class,
        RejectedMerge::class,
        ExtractionJournalEntry::class,
        ExtractionJournalSummary::class,
        Reminder::class,
        ReminderSavedItemRef::class,
        ReminderNotiRecordRef::class,
        GeneralPreference::class,
        ExtractionPreference::class,
        UserKnowledge::class,
        FirestoreOutboxOp::class,
        ProposedOpRecord::class,
        PendingReviewDraft::class,
    ],
    version = 56,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao
    abstract fun notiLlmStateDao(): NotiLlmStateDao
    abstract fun savedItemDao(): SavedItemDao
    abstract fun todoStepDao(): TodoStepDao
    abstract fun notiSavedItemLinkDao(): NotiSavedItemLinkDao
    abstract fun savedItemChangeLogDao(): SavedItemChangeLogDao
    abstract fun pendingProposedOpDao(): PendingProposedOpDao
    abstract fun rejectedMergeDao(): RejectedMergeDao
    abstract fun extractionJournalDao(): ExtractionJournalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun generalPreferenceDao(): GeneralPreferenceDao
    abstract fun extractionPreferenceDao(): ExtractionPreferenceDao
    abstract fun userKnowledgeDao(): UserKnowledgeDao
    abstract fun firestoreOutboxDao(): FirestoreOutboxDao
    abstract fun proposedOpRecordDao(): ProposedOpRecordDao
    abstract fun pendingReviewDraftDao(): PendingReviewDraftDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "notigpt_app.db"

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(*AppDatabaseMigrations.ALL)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}
