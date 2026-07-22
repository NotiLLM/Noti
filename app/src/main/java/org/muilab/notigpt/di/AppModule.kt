package org.muilab.notigpt.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.muilab.notigpt.data.export.MediaStoreNotiLogExporter
import org.muilab.notigpt.data.export.NotiLogExporter
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.data.repository.personalization.PersonalizationRepository
import org.muilab.notigpt.data.repository.personalization.RoomPersonalizationRepository
import org.muilab.notigpt.ui.common.clipboard.AndroidClipboardController
import org.muilab.notigpt.ui.common.clipboard.ClipboardController
import org.muilab.notigpt.ui.common.feedback.SnackbarUserToaster
import org.muilab.notigpt.ui.common.feedback.UserToaster

/**
 * Application-scoped object graph shared by Android entry points.
 *
 * `@Singleton` here means one instance per app process, not permanent storage. Room remains the
 * durable source of truth; Hilt only owns construction and lifetime of the database/repository.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideNotiRepository(
        @ApplicationContext context: Context,
        database: AppDatabase,
    ): NotiRepository = NotiRepository(
        appContext = context,
        notiDrawerDao = database.drawerDao(),
        notiActionDao = database.actionDao(),
        notiRecordDao = database.recordDao(),
        notiLlmStateDao = database.notiLlmStateDao(),
    )

    /** One application-scoped owner for confirmed three-store personalization. */
    @Provides
    @Singleton
    fun providePersonalizationRepository(database: AppDatabase): PersonalizationRepository =
        RoomPersonalizationRepository(
            database = database,
            generalPreferenceDao = database.generalPreferenceDao(),
            extractionPreferenceDao = database.extractionPreferenceDao(),
            userKnowledgeDao = database.userKnowledgeDao(),
            firestoreOutboxDao = database.firestoreOutboxDao(),
        )

    @Provides
    @Singleton
    fun provideClipboardController(@ApplicationContext context: Context): ClipboardController =
        AndroidClipboardController(context)

    @Provides
    @Singleton
    fun provideUserToaster(): UserToaster = SnackbarUserToaster()

    @Provides
    @Singleton
    fun provideNotiLogExporter(@ApplicationContext context: Context): NotiLogExporter =
        MediaStoreNotiLogExporter(context)
}
