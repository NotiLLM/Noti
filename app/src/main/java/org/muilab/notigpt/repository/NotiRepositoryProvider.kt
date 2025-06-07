package org.muilab.notigpt.repository

import android.content.Context
import org.muilab.notigpt.database.room.NotiActionDatabase
import org.muilab.notigpt.database.room.NotiCategoryDatabase
import org.muilab.notigpt.database.room.NotiDrawerDatabase
import org.muilab.notigpt.database.room.NotiRecordDatabase

object NotiRepositoryProvider {
    fun provideNotiRepository(context: Context): NotiRepository {
        return NotiRepository(
            NotiDrawerDatabase.getInstance(context.applicationContext).drawerDao(),
            NotiActionDatabase.getInstance(context.applicationContext).actionDao(),
            NotiRecordDatabase.getInstance(context.applicationContext).recordDao(),
            NotiCategoryDatabase.getInstance(context.applicationContext).categoryDao()
        )
    }
}