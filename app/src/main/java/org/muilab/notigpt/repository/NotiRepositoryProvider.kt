package org.muilab.notigpt.repository

import android.content.Context
import org.muilab.notigpt.database.room.AppDatabase

object NotiRepositoryProvider {
    fun provideNotiRepository(context: Context): NotiRepository {
        val appDatabase = AppDatabase.getInstance(context.applicationContext)
        return NotiRepository(
            context.applicationContext,
            appDatabase, // Pass the whole database instance
            appDatabase.drawerDao(),
            appDatabase.actionDao(),
            appDatabase.recordDao()
        )
    }
}