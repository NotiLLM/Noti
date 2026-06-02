package org.muilab.notigpt.ui.notification.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.repository.notification.NotiRepository

/**
 * Controller for drawer search and contextual record loading around a notification key.
 *
 * Keep search-result state and gap loading here so notification cards do not query Room directly. If reminder
 * context loading starts sharing this logic, extract a repository-level record-context service.
 */
class DrawerSearchController(
    private val notiRepository: NotiRepository,
) {

    private val _searchResults = MutableStateFlow<Map<String, List<NotiRecord>>>(emptyMap())
    val searchResults: StateFlow<Map<String, List<NotiRecord>>> = _searchResults

    private val _searchUnits = MutableStateFlow<Map<String, NotiUnit>>(emptyMap())
    val searchUnits: StateFlow<Map<String, NotiUnit>> = _searchUnits

    suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyMap()
            return
        }

        val results = withContext(Dispatchers.IO) {
            notiRepository.searchNotifications(query)
        }

        val unitMap = mutableMapOf<String, NotiUnit>()
        val keysToFetch = results.keys.filter { !_searchUnits.value.containsKey(it) }
        if (keysToFetch.isNotEmpty()) {
            val units = withContext(Dispatchers.IO) { notiRepository.getNotiUnitByKeys(keysToFetch) }
            units.forEach { unitMap[it.notiKey] = it }
        }
        _searchUnits.value += unitMap

        _searchResults.value = results.mapValues { (_, v) -> v.sortedBy { it.time } }
    }

    suspend fun loadSearchContext(notiKey: String, isOlder: Boolean) {
        val currentList = _searchResults.value[notiKey] ?: return
        if (currentList.isEmpty()) return

        val pivotTime = if (isOlder) currentList.first().time else currentList.last().time
        val newRecords = withContext(Dispatchers.IO) {
            notiRepository.getContextRecords(notiKey, pivotTime, isOlder)
        }

        if (newRecords.isNotEmpty()) {
            val updatedList = if (isOlder) newRecords + currentList else currentList + newRecords
            val currentMap = _searchResults.value.toMutableMap()
            currentMap[notiKey] = updatedList
            _searchResults.value = currentMap
        }
    }

    suspend fun checkGapHasRecords(notiKey: String, startTime: Long, endTime: Long): Boolean {
        return withContext(Dispatchers.IO) {
            notiRepository.hasRecordsInGap(notiKey, startTime, endTime)
        }
    }

    suspend fun loadGapRecords(notiKey: String, startTime: Long, endTime: Long, fromStart: Boolean) {
        val currentList = _searchResults.value[notiKey] ?: return

        val gapRecords = withContext(Dispatchers.IO) {
            notiRepository.getGapRecords(notiKey, startTime, endTime, 10, fromStart)
        }

        if (gapRecords.isNotEmpty()) {
            val combined = (currentList + gapRecords)
                .distinctBy { it.notiRecordId }
                .sortedBy { it.time }

            val currentMap = _searchResults.value.toMutableMap()
            currentMap[notiKey] = combined
            _searchResults.value = currentMap
        }
    }
}
