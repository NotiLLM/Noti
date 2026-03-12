package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.PreferenceConflict
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiGroup
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.VisibleNotiRecord
import org.muilab.notigpt.model.esm.EsmAnswerEvent
import org.muilab.notigpt.model.esm.EsmInstance
import org.muilab.notigpt.model.features.ReminderExtractionSnapshot

@Database(
    entities = [
        NotiUnit::class,
        NotiRecord::class,
        NotiAction::class,
        ReminderUnit::class,
        NotiGroup::class,
        EsmInstance::class,
        EsmAnswerEvent::class,
        ReminderExtractionSnapshot::class,
        ExtractionPreference::class,
        PreferenceConflict::class,
    ],
    views = [VisibleNotiRecord::class],
    version = 31,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao
    abstract fun reminderListDao(): ReminderListDao
    abstract fun groupDao(): NotiGroupDao
    abstract fun esmDao(): EsmDao
    abstract fun reminderSnapshotDao(): ReminderSnapshotDao
    abstract fun extractionPreferenceDao(): ExtractionPreferenceDao
    abstract fun preferenceConflictDao(): PreferenceConflictDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "notigpt_app.db"

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_list ADD COLUMN isVisible INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_list ADD COLUMN origin TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE reminder_list ADD COLUMN humanEditCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminder_list ADD COLUMN deletedAtMs INTEGER")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS extraction_preferences (
                        id TEXT NOT NULL PRIMARY KEY,
                        statement TEXT NOT NULL,
                        preferenceType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS preference_conflicts (
                        conflictId TEXT NOT NULL PRIMARY KEY,
                        description TEXT NOT NULL,
                        involvedPreferenceIds TEXT NOT NULL,
                        source TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder_list ADD COLUMN isEvent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminder_list ADD COLUMN startTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminder_list ADD COLUMN endTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE noti_drawer ADD COLUMN hasEvent INTEGER NOT NULL DEFAULT 0")
            }
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
                .addMigrations(MIGRATION_26_27)
                .addMigrations(MIGRATION_27_28)
                .addMigrations(MIGRATION_28_29)
                .addMigrations(MIGRATION_29_30)
                .addMigrations(MIGRATION_30_31)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}