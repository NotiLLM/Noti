package org.muilab.notigpt.ui.viewmodel.drawer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.repository.NotiRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope

/**
 * Keeps a per-notification full-record stream/cache.
 *
 * Motivation: DrawerViewModel shouldn't manage job maps + caches.
 */
class FullRecordsController(
    private val scope: CoroutineScope,
    private val notiRepository: NotiRepository,
) {
    private val fullRecordsCache = ConcurrentHashMap<String, MutableStateFlow<List<NotiRecord>>>()
    private val fullRecordsJobs = ConcurrentHashMap<String, Job>()

    fun getFlow(notiKey: String): StateFlow<List<NotiRecord>> {
        return fullRecordsCache.getOrPut(notiKey) { MutableStateFlow(emptyList()) }
    }

    fun loadForKey(notiKey: String) {
        if (fullRecordsJobs.containsKey(notiKey)) return
        val stateFlow = fullRecordsCache.getOrPut(notiKey) { MutableStateFlow(emptyList()) }

        val job = scope.launch(Dispatchers.IO) {
            try {
                notiRepository.activeRecordsFlowForKey(notiKey)
                    .collect { recs: List<NotiRecord> ->
                        stateFlow.value = recs.sortedBy { it.time }
                    }
            } catch (e: Exception) {
                Log.e("FullRecordsController", "Error subscribing to full records for $notiKey", e)
            } finally {
                fullRecordsJobs.remove(notiKey)
            }
        }
        fullRecordsJobs[notiKey] = job
    }

    fun clearForKey(notiKey: String) {
        fullRecordsJobs.remove(notiKey)?.cancel()
        fullRecordsCache.remove(notiKey)
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            fullRecordsJobs.values.forEach { it.cancel() }
            fullRecordsJobs.clear()
            fullRecordsCache.clear()
        }
    }
}
