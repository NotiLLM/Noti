package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.muilab.notigpt.model.notifications.NotiUnit

@Database(entities = [NotiUnit::class], version = 1)
abstract class NotiDrawerDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao

    companion object {
        @Volatile
        private var INSTANCE: NotiDrawerDatabase? = null

        fun getInstance(context: Context): NotiDrawerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
            context.applicationContext, NotiDrawerDatabase::class.java, "noti_drawer")
            .build()
    }
}