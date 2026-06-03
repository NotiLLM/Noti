package org.muilab.notigpt.ui.notification.controller

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.remote.n8n.enqueueNotificationAction
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Controller for applying user actions to individual notifications.
 *
 * This layer translates UI intents into repository writes and Android notification operations. Keep action
 * tracking here so card components do not duplicate state-transition rules.
 */
class DrawerActionsController(
    private val context: Context,
    private val notiRepository: NotiRepository,
) {

    private fun shouldTrackAction(action: NotiActionType): Boolean {
        return when (action) {
            NotiActionType.Pin -> SharedPreferencesManager.trackPin
            NotiActionType.Archive -> SharedPreferencesManager.autoArchive
            NotiActionType.DismissSwipe -> SharedPreferencesManager.autoDelete
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun actOnNoti(notiKey: String, action: NotiActionType) {
        withContext(Dispatchers.IO) {
            if (shouldTrackAction(action)) enqueueNotificationAction(context, notiKey, action.wireValue)
            notiRepository.actOnNoti(notiKey, action.wireValue)
        }
    }

    suspend fun actOnNotiLegacy(notiKey: String, action: String) {
        withContext(Dispatchers.IO) {
            notiRepository.actOnNoti(notiKey, action)
        }
    }

}
