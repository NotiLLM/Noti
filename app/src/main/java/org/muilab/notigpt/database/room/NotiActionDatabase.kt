package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.muilab.notigpt.model.notifications.NotiAction

@Database(entities = [NotiAction::class], version = 1)
abstract class NotiActionDatabase : RoomDatabase() {

    abstract fun actionDao(): NotiActionDao

    companion object {
        @Volatile
        private var INSTANCE: NotiActionDatabase? = null

        fun getInstance(context: Context): NotiActionDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
            context.applicationContext, NotiActionDatabase::class.java, "noti_action")
            .build()
    }
}