package org.muilab.notigpt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muilab.notigpt.domain.esm.EsmStatuses
import org.muilab.notigpt.domain.esm.IRBShortSurveyV2
import org.muilab.notigpt.repository.EsmRepository
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.model.esm.EsmInstance
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.repository.firestore.FirestoreSyncRepository
import org.muilab.notigpt.util.postEsmIndicatorNotification

class EsmViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = EsmRepository(app.applicationContext)
    private val db = AppDatabase.getInstance(app.applicationContext)

    // Replace manual refresh-based state with a Room Flow-backed StateFlow.
    private val _available: StateFlow<List<EsmInstance>> =
        db.esmDao()
            .getUnexpiredAvailableFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val available: StateFlow<List<EsmInstance>> = _available

    private val _activeInstance = MutableStateFlow<EsmInstance?>(null)
    val activeInstance: StateFlow<EsmInstance?> = _activeInstance

    private val _currentQuestionId = MutableStateFlow<String?>(null)
    val currentQuestionId: StateFlow<String?> = _currentQuestionId

    private val _currentAnswerJson = MutableStateFlow<String>("")
    val currentAnswerJson: StateFlow<String> = _currentAnswerJson

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history

    /** questionId -> answerJson */
    private val _answers = MutableStateFlow<Map<String, String>>(emptyMap())
    val answers: StateFlow<Map<String, String>> = _answers

    /**
     * Stack of question IDs in the order the user actually visited them.
     * This enables q2-1/q2-2 -> q1 back navigation.
     */
    private val _questionTrail = MutableStateFlow<List<String>>(emptyList())
    val questionTrail: StateFlow<List<String>> = _questionTrail

    private val _activeSnapshotJson = MutableStateFlow<String?>(null)
    val activeSnapshotJson: StateFlow<String?> = _activeSnapshotJson

    private val _activeReminder = MutableStateFlow<org.muilab.notigpt.model.features.ReminderUnit?>(null)
    val activeReminder: StateFlow<org.muilab.notigpt.model.features.ReminderUnit?> = _activeReminder

    private val _activeNotiPreviews = MutableStateFlow<List<NotiDisplayUnit>>(emptyList())
    val activeNotiPreviews: StateFlow<List<NotiDisplayUnit>> = _activeNotiPreviews

    fun refresh() {
        // No-op: available is Flow-backed now, so UI updates automatically.
        // Kept for compatibility with existing call sites.
    }

    private fun loadActiveSnapshot(instance: EsmInstance) {
        viewModelScope.launch {
            val snap = repo.getSnapshot(instance.snapshotId)?.payloadJson
            val rem = repo.getReminder(instance.reminderId)
            _activeSnapshotJson.value = snap
            _activeReminder.value = rem

            // Reconstruct related notification previews for v2 snapshots.
            _activeNotiPreviews.value = emptyList()
            if (snap.isNullOrBlank()) return@launch

            val grouping = org.muilab.notigpt.domain.esm.EsmUserSnapshot.parseRecordIdGrouping(snap)
            if (grouping == null) return@launch

            val notiKeyToRecordIds = grouping.notiKeyToRecordIds

            val notiKeys: List<String> = when {
                notiKeyToRecordIds.isNotEmpty() -> notiKeyToRecordIds.keys.toList()
                rem != null && rem.associatedNotis.isNotEmpty() -> rem.associatedNotis.toList()
                else -> emptyList()
            }

            if (notiKeys.isEmpty()) return@launch

            val recordIdsToLoad: List<String> = when {
                notiKeyToRecordIds.isNotEmpty() -> notiKeyToRecordIds.values.flatten().distinct()
                else -> grouping.recordIds
            }

            if (recordIdsToLoad.isEmpty()) return@launch

            // Load rows from DB off main thread.
            val previews = withContext(Dispatchers.IO) {
                val records = db.recordDao().getRecordsByIds(recordIdsToLoad)
                val recordsByKey = records.groupBy { it.notiKey }
                val units = db.drawerDao().getByNotiKeys(notiKeys).associateBy { it.notiKey }

                val result = mutableListOf<NotiDisplayUnit>()
                for (key in notiKeys) {
                    val unit = units[key] ?: continue
                    val wantedIds = notiKeyToRecordIds[key]?.toHashSet()
                    val recs = recordsByKey[key].orEmpty()
                        .let { rs ->
                            if (wantedIds == null) rs else rs.filter { it.notiRecordId in wantedIds }
                        }
                        .sortedBy { it.whenTime }

                    if (recs.isEmpty()) continue
                    result.add(NotiDisplayUnit(unit, recs))
                }
                result
            }

            _activeNotiPreviews.value = previews
        }
    }

    fun openInstance(instance: EsmInstance) {
        val now = System.currentTimeMillis()
        if (now > instance.expiresAt) {
            // Make sure it can't be answered and doesn't crowd the list.
            viewModelScope.launch {
                db.esmDao().setInstanceStatus(instance.instanceId, EsmStatuses.EXPIRED)
                refresh()
            }
            return
        }

        _activeInstance.value = instance
        _currentQuestionId.value = IRBShortSurveyV2.firstQuestionId()
        _currentAnswerJson.value = ""

        // Always kick off snapshot load ASAP.
        loadActiveSnapshot(instance)

        viewModelScope.launch {
            val events = db.esmDao().getAnswerEvents(instance.instanceId)
            _answers.value = events.associate { it.questionId to it.answerJson }
            _history.value = emptyList()
            _questionTrail.value = listOfNotNull(_currentQuestionId.value)
        }
    }

    fun refreshActiveSnapshot() {
        val inst = _activeInstance.value ?: return
        loadActiveSnapshot(inst)
    }

    fun currentSavedAnswer(): String? {
        val qid = _currentQuestionId.value ?: return null
        return _answers.value[qid]
    }

    fun goBackQuestion() {
        val trail = _questionTrail.value
        if (trail.size <= 1) return

        // Remove current
        val newTrail = trail.dropLast(1)
        val prev = newTrail.last()

        _questionTrail.value = newTrail
        _currentQuestionId.value = prev
        _currentAnswerJson.value = _answers.value[prev] ?: ""
    }

    fun closeInstance() {
        _activeInstance.value = null
        _currentQuestionId.value = null
        _currentAnswerJson.value = ""
        _questionTrail.value = emptyList()
        _activeSnapshotJson.value = null
        _activeReminder.value = null
        _activeNotiPreviews.value = emptyList()
        refresh()

        // Best-effort: refresh the system indicator in case something expired/changed in the meantime.
        try { postEsmIndicatorNotification(getApplication<Application>().applicationContext) } catch (_: Exception) {}
    }

    fun submitAnswer(answerJson: String) {
        val inst = _activeInstance.value ?: return
        val qid = _currentQuestionId.value ?: return
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            db.esmDao().saveAnswerAndMaybeMarkAnswered(inst.instanceId, qid, answerJson, now)
            _answers.update { it + (qid to answerJson) }

            // Update/cancel drawer notification immediately after answer events change availability.
            try { postEsmIndicatorNotification(getApplication<Application>().applicationContext) } catch (_: Exception) {}

            // Firestore analytics upload (Option A: upload on every answer)
            try {
                FirestoreSyncRepository(getApplication<Application>().applicationContext).syncEsmAnswerEvent(inst.instanceId, qid)
            } catch (_: Throwable) {
                // best-effort
            }

            val next = IRBShortSurveyV2.nextQuestionId(qid, answerJson)
            if (next == null) {
                val isLate = now > inst.expiresAt
                db.esmDao().markAnswered(inst.instanceId, EsmStatuses.ANSWERED, now, isLate)
                closeInstance()
            } else {
                _history.update { it + qid }
                _currentQuestionId.value = next
                _currentAnswerJson.value = _answers.value[next] ?: ""
                _questionTrail.update { it + next }
            }
        }
    }

    suspend fun loadSnapshotJson(snapshotId: String): String? {
        return repo.getSnapshot(snapshotId)?.payloadJson
    }

    fun createDebugEsm() {
        viewModelScope.launch {
            repo.createDebugEsmNow()
            refresh()
        }
    }

    suspend fun loadReminderTitle(reminderId: String): String? {
        return repo.getReminder(reminderId)?.reminderTitle
    }
}
