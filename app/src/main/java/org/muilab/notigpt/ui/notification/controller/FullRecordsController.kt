package org.muilab.notigpt.ui.notification.controller

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.data.repository.notification.NotiRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope

/**
 * Controller for loading full notification-record timelines by notification key.
 *
 * Keep per-key record flows here so expanded cards can request history without owning DAO queries. If this grows
 * into general context loading, merge it with DrawerSearchController or a repository-level context loader.
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
