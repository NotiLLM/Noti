package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.muilab.notigpt.model.notifications.NotiRecord

@Database(entities = [NotiRecord::class], version = 1)
abstract class NotiRecordDatabase : RoomDatabase() {

    abstract fun recordDao(): NotiRecordDao

    companion object {
        @Volatile
        private var INSTANCE: NotiRecordDatabase? = null

        fun getInstance(context: Context): NotiRecordDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
            context.applicationContext, NotiRecordDatabase::class.java, "noti_record")
            .build()
    }
}