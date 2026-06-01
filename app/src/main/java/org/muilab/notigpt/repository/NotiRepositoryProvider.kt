package org.muilab.notigpt.repository

import android.content.Context
import org.muilab.notigpt.database.room.AppDatabase

/**
 * Small provider for constructing NotiRepository from the Room database singleton.
 *
 * Use this at Android boundaries that cannot receive injected dependencies. Prefer passing an existing repository
 * through constructors when testability matters.
 */
object NotiRepositoryProvider {
    fun provideNotiRepository(context: Context): NotiRepository {
        val appDatabase = AppDatabase.getInstance(context.applicationContext)
        return NotiRepository(
            context.applicationContext,
            // Pass the whole database instance
            appDatabase.drawerDao(),
            appDatabase.actionDao(),
            appDatabase.recordDao(),
            appDatabase.groupDao()
        )
    }
}