package org.muilab.notigpt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muilab.notigpt.domain.esm.EsmStatuses
import org.muilab.notigpt.domain.esm.IRBShortSurveyV2
import org.muilab.notigpt.repository.EsmRepository
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.model.esm.EsmInstance

class EsmViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = EsmRepository(app.applicationContext)
    private val db = AppDatabase.getInstance(app.applicationContext)

    private val _available = MutableStateFlow<List<EsmInstance>>(emptyList())
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

    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _available.value = repo.getInstancesByStatuses(listOf(EsmStatuses.AVAILABLE))
                .filter { it.expiresAt > now }
                .sortedBy { it.availableAt }
        }
    }

    private fun loadActiveSnapshot(instance: EsmInstance) {
        viewModelScope.launch {
            _activeSnapshotJson.value = repo.getSnapshot(instance.snapshotId)?.payloadJson
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
        refresh()
    }

    fun submitAnswer(answerJson: String) {
        val inst = _activeInstance.value ?: return
        val qid = _currentQuestionId.value ?: return
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            db.esmDao().saveAnswerAndMaybeMarkAnswered(inst.instanceId, qid, answerJson, now)
            _answers.update { it + (qid to answerJson) }

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
}
