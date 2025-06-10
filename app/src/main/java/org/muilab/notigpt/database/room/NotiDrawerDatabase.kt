package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_UNKNOWN

@Database(entities = [NotiUnit::class], version = 2)
abstract class NotiDrawerDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao

    companion object {
        @Volatile
        private var INSTANCE: NotiDrawerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE noti_drawer ADD COLUMN appCategory TEXT NOT NULL DEFAULT '$APP_CATEGORY_UNKNOWN'")
            }
        }

        fun getInstance(context: Context): NotiDrawerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
            context.applicationContext, NotiDrawerDatabase::class.java, "noti_drawer")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}