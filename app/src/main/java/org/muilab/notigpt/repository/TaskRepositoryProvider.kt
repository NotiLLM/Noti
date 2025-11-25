package org.muilab.notigpt.repository

import android.content.Context
import org.muilab.notigpt.database.room.AppDatabase

object TaskRepositoryProvider {
    fun provideTaskRepository(context: Context): TaskRepository {
        val appDatabase = AppDatabase.getInstance(context.applicationContext)
        return TaskRepository(appDatabase, appDatabase.taskListDao())
    }
}

