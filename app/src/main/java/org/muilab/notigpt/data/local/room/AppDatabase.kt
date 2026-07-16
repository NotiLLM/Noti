package org.muilab.notigpt.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalSummary
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.PreferenceConflict
import org.muilab.notigpt.model.features.NotiLlmState
import org.muilab.notigpt.model.features.NotiSavedItemLink
import org.muilab.notigpt.model.features.PendingOp
import org.muilab.notigpt.model.features.RejectedMerge
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderNotiRecordRef
import org.muilab.notigpt.model.features.ReminderSavedItemRef
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.model.features.UserContext
import org.muilab.notigpt.model.features.FirestoreOutboxOp
import org.muilab.notigpt.model.features.GeneratedProposal
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.local.room.dao.ExtractionJournalDao
import org.muilab.notigpt.data.local.room.dao.ExtractionPreferenceDao
import org.muilab.notigpt.data.local.room.dao.SavedItemChangeLogDao
import org.muilab.notigpt.data.local.room.dao.NotiActionDao
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiLlmStateDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.data.local.room.dao.NotiSavedItemLinkDao
import org.muilab.notigpt.data.local.room.dao.PendingOpDao
import org.muilab.notigpt.data.local.room.dao.PreferenceConflictDao
import org.muilab.notigpt.data.local.room.dao.RejectedMergeDao
import org.muilab.notigpt.data.local.room.dao.ReminderDao
import org.muilab.notigpt.data.local.room.dao.SavedItemDao
import org.muilab.notigpt.data.local.room.dao.SavedSubItemDao
import org.muilab.notigpt.data.local.room.dao.UserContextDao
import org.muilab.notigpt.data.local.room.dao.FirestoreOutboxDao
import org.muilab.notigpt.data.local.room.dao.GeneratedProposalDao

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
        SavedSubItem::class,
        NotiSavedItemLink::class,
        SavedItemChangeLog::class,
        PendingOp::class,
        RejectedMerge::class,
        ExtractionJournalEntry::class,
        ExtractionJournalSummary::class,
        Reminder::class,
        ReminderSavedItemRef::class,
        ReminderNotiRecordRef::class,
        ExtractionPreference::class,
        PreferenceConflict::class,
        UserContext::class,
        FirestoreOutboxOp::class,
        GeneratedProposal::class,
    ],
    version = 47,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao
    abstract fun notiLlmStateDao(): NotiLlmStateDao
    abstract fun reminderListDao(): SavedItemDao
    abstract fun subTaskDao(): SavedSubItemDao
    abstract fun notiSavedItemLinkDao(): NotiSavedItemLinkDao
    abstract fun savedItemChangeLogDao(): SavedItemChangeLogDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun rejectedMergeDao(): RejectedMergeDao
    abstract fun extractionJournalDao(): ExtractionJournalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun extractionPreferenceDao(): ExtractionPreferenceDao
    abstract fun preferenceConflictDao(): PreferenceConflictDao
    abstract fun userContextDao(): UserContextDao
    abstract fun firestoreOutboxDao(): FirestoreOutboxDao
    abstract fun generatedProposalDao(): GeneratedProposalDao

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
                .addMigrations(AppDatabaseMigrations.MIGRATION_1_2)
                .addMigrations(AppDatabaseMigrations.MIGRATION_2_3)
                .addMigrations(AppDatabaseMigrations.MIGRATION_3_4)
                .addMigrations(AppDatabaseMigrations.MIGRATION_4_5)
                .addMigrations(AppDatabaseMigrations.MIGRATION_5_6)
                .addMigrations(AppDatabaseMigrations.MIGRATION_6_7)
                .addMigrations(AppDatabaseMigrations.MIGRATION_7_8)
                .addMigrations(AppDatabaseMigrations.MIGRATION_8_9)
                .addMigrations(AppDatabaseMigrations.MIGRATION_9_10)
                .addMigrations(AppDatabaseMigrations.MIGRATION_10_11)
                .addMigrations(AppDatabaseMigrations.MIGRATION_11_12)
                .addMigrations(AppDatabaseMigrations.MIGRATION_12_13)
                .addMigrations(AppDatabaseMigrations.MIGRATION_13_14)
                .addMigrations(AppDatabaseMigrations.MIGRATION_14_15)
                .addMigrations(AppDatabaseMigrations.MIGRATION_15_16)
                .addMigrations(AppDatabaseMigrations.MIGRATION_16_17)
                .addMigrations(AppDatabaseMigrations.MIGRATION_17_18)
                .addMigrations(AppDatabaseMigrations.MIGRATION_18_19)
                .addMigrations(AppDatabaseMigrations.MIGRATION_19_20)
                .addMigrations(AppDatabaseMigrations.MIGRATION_20_21)
                .addMigrations(AppDatabaseMigrations.MIGRATION_21_22)
                .addMigrations(AppDatabaseMigrations.MIGRATION_22_23)
                .addMigrations(AppDatabaseMigrations.MIGRATION_23_24)
                .addMigrations(AppDatabaseMigrations.MIGRATION_24_25)
                .addMigrations(AppDatabaseMigrations.MIGRATION_25_26)
                .addMigrations(AppDatabaseMigrations.MIGRATION_26_27)
                .addMigrations(AppDatabaseMigrations.MIGRATION_27_28)
                .addMigrations(AppDatabaseMigrations.MIGRATION_28_29)
                .addMigrations(AppDatabaseMigrations.MIGRATION_29_30)
                .addMigrations(AppDatabaseMigrations.MIGRATION_30_31)
                .addMigrations(AppDatabaseMigrations.MIGRATION_31_32)
                .addMigrations(AppDatabaseMigrations.MIGRATION_32_33)
                .addMigrations(AppDatabaseMigrations.MIGRATION_33_34)
                .addMigrations(AppDatabaseMigrations.MIGRATION_34_35)
                .addMigrations(AppDatabaseMigrations.MIGRATION_35_36)
                .addMigrations(AppDatabaseMigrations.MIGRATION_36_37)
                .addMigrations(AppDatabaseMigrations.MIGRATION_37_38)
                .addMigrations(AppDatabaseMigrations.MIGRATION_38_39)
                .addMigrations(AppDatabaseMigrations.MIGRATION_39_40)
                .addMigrations(AppDatabaseMigrations.MIGRATION_40_41)
                .addMigrations(AppDatabaseMigrations.MIGRATION_41_42)
                .addMigrations(AppDatabaseMigrations.MIGRATION_42_43)
                .addMigrations(AppDatabaseMigrations.MIGRATION_43_44)
                .addMigrations(AppDatabaseMigrations.MIGRATION_44_45)
                .addMigrations(AppDatabaseMigrations.MIGRATION_45_46)
                .addMigrations(AppDatabaseMigrations.MIGRATION_46_47)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}
