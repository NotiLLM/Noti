package org.muilab.notigpt.ui.notification.controller

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.repository.notification.NotiRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Controller for batching read/seen-state updates from drawer rendering.
 *
 * This prevents Compose visibility events from writing to Room one row at a time. Keep it focused on read-state
 * persistence; filtering and counts belong in adjacent drawer controllers.
 */
class DrawerReadStateController(
    private val notiRepository: NotiRepository,
) {
    private val seenNotiKeys = ConcurrentHashMap.newKeySet<String>()

    fun markSeenIfUnread(notiKey: String, isAlreadyRead: Boolean) {
        if (isAlreadyRead) return
        seenNotiKeys.add(notiKey)
    }

    fun hasPending(): Boolean = seenNotiKeys.isNotEmpty()

    suspend fun persistSeen() {
        if (seenNotiKeys.isEmpty()) return

        val snapshot = seenNotiKeys.toSet()
        seenNotiKeys.clear()

        withContext(Dispatchers.IO) {
            Log.d("ViewModelReadState", "Persisting seenNotis: ${snapshot.size}")
            notiRepository.updateSeenNotifications(snapshot)
        }
    }
}

