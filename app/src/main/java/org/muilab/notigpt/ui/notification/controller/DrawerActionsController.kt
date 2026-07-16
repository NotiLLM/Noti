package org.muilab.notigpt.ui.notification.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.data.repository.notification.NotiRepository

/**
 * Controller for applying user actions to individual notifications.
 *
 * This layer translates UI intents into repository writes and Android notification operations. Keep action
 * rules here so card components do not duplicate state-transition behavior.
 */
class DrawerActionsController(
    private val notiRepository: NotiRepository,
) {
    suspend fun actOnNoti(notiKey: String, action: NotiActionType) {
        withContext(Dispatchers.IO) {
            notiRepository.actOnNoti(notiKey, action.wireValue)
        }
    }

    suspend fun actOnNotiLegacy(notiKey: String, action: String) {
        withContext(Dispatchers.IO) {
            notiRepository.actOnNoti(notiKey, action)
        }
    }

}
