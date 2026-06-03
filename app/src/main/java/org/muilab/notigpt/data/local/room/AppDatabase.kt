package org.muilab.notigpt.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.PreferenceConflict
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderNotiRecordRef
import org.muilab.notigpt.model.features.ReminderSavedItemRef
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.model.features.UserContext
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.VisibleNotiRecord
import org.muilab.notigpt.model.features.ReminderExtractionSnapshot
import org.muilab.notigpt.data.local.room.dao.ExtractionPreferenceDao
import org.muilab.notigpt.data.local.room.dao.NotiActionDao
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.data.local.room.dao.PreferenceConflictDao
import org.muilab.notigpt.data.local.room.dao.ReminderDao
import org.muilab.notigpt.data.local.room.dao.SavedItemDao
import org.muilab.notigpt.data.local.room.dao.ReminderSnapshotDao
import org.muilab.notigpt.data.local.room.dao.SavedSubItemDao
import org.muilab.notigpt.data.local.room.dao.UserContextDao

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
        SavedItem::class,
        SavedSubItem::class,
        Reminder::class,
        ReminderSavedItemRef::class,
        ReminderNotiRecordRef::class,
        ReminderExtractionSnapshot::class,
        ExtractionPreference::class,
        PreferenceConflict::class,
        UserContext::class,
    ],
    views = [VisibleNotiRecord::class],
    version = 38,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao
    abstract fun reminderListDao(): SavedItemDao
    abstract fun subTaskDao(): SavedSubItemDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderSnapshotDao(): ReminderSnapshotDao
    abstract fun extractionPreferenceDao(): ExtractionPreferenceDao
    abstract fun preferenceConflictDao(): PreferenceConflictDao
    abstract fun userContextDao(): UserContextDao

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
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}