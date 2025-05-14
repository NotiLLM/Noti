package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.model.notifications.NotiCategory
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_DELETED
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL

@Database(entities = [NotiCategory::class], version = 1, exportSchema = false)
abstract class CategoryDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: CategoryDatabase? = null

        fun getInstance(context: Context): CategoryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext, CategoryDatabase::class.java, "noti_category")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                database.categoryDao().insert(NotiCategory(NOTI_CATEGORY_GENERAL))
                                database.categoryDao().insert(NotiCategory(NOTI_CATEGORY_ARCHIVE))
                                database.categoryDao().insert(NotiCategory(NOTI_CATEGORY_DELETED))
                            }
                        }
                    }
                })
                .build()
    }
}