package org.muilab.notigpt.ui.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muilab.notigpt.R
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.room.ExtractionPreferenceDao
import org.muilab.notigpt.database.room.PreferenceConflictDao
import org.muilab.notigpt.database.server.PreferenceChatClient
import org.muilab.notigpt.database.server.PreferenceQuickSyncClient
import org.muilab.notigpt.model.features.ChatFlowContext
import org.muilab.notigpt.model.features.ChatInteractRequest
import org.muilab.notigpt.model.features.ChatMessage
import org.muilab.notigpt.model.features.ConflictDto
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.PreferenceConflict
import org.muilab.notigpt.model.features.PreferenceEntryPoint
import org.muilab.notigpt.model.features.ProposedAction
import org.muilab.notigpt.model.features.ProposedActionType
import org.muilab.notigpt.model.features.QuickSyncRequest
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.features.UserSelections
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.Locale

/**
 * Central state manager for the HITL preference learning feature.
 *
 * Handles:
 * - Progressive disclosure BottomSheet state (Flows 1-3)
 * - Chat UI state (Flow 4)
 * - Direct network calls for quick-sync and chat-interact
 * - Room persistence of preferences
 */
class PreferenceViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "PreferenceViewModel"

    private val prefDao: ExtractionPreferenceDao =
        AppDatabase.getInstance(application).extractionPreferenceDao()

    private val conflictDao: PreferenceConflictDao =
        AppDatabase.getInstance(application).preferenceConflictDao()

    // ══════════════════════════════════════════════════════════════════
    //  Active preferences (from Room)
    // ══════════════════════════════════════════════════════════════════

    val activePreferences: StateFlow<List<ExtractionPreference>> =
        prefDao.getAllPreferencesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ══════════════════════════════════════════════════════════════════
    //  Unresolved conflicts (from Room)
    // ══════════════════════════════════════════════════════════════════

    val unresolvedConflicts: StateFlow<List<PreferenceConflict>> =
        conflictDao.getAllConflictsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Persist conflicts returned by the backend into Room. */
    private suspend fun persistConflicts(dtos: List<ConflictDto>, source: String) {
        if (dtos.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = dtos.map { dto ->
            PreferenceConflict(
                conflictId = dto.conflictId,
                description = dto.description,
                involvedPreferenceIds = dto.involvedPreferenceIds.joinToString(","),
                source = source,
                createdAt = now,
            )
        }
        conflictDao.insertAll(entities)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Snackbar prompt (Delete / Manual Extract)
    //  If ignored → implicit "just this one". If action clicked → open BottomSheet.
    // ══════════════════════════════════════════════════════════════════

    data class SnackbarEvent(
        val entryPoint: PreferenceEntryPoint,
        val reminder: ReminderUnit?,
        val reminderBefore: ReminderUnit?,
        val contextData: Map<String, Any?>,
    )

    private val _snackbarEvent = MutableStateFlow<SnackbarEvent?>(null)
    val snackbarEvent: StateFlow<SnackbarEvent?> = _snackbarEvent

    /** Called when the Snackbar auto-dismisses or user swipes it away. */
    fun dismissSnackbar() {
        _snackbarEvent.value = null
    }

    /** Called when user clicks the action button on the Snackbar. */
    fun promoteSnackbarToFlow() {
        val event = _snackbarEvent.value ?: return
        _snackbarEvent.value = null
        _currentReminder = event.reminder
        _currentReminderBefore = event.reminderBefore
        _bottomSheetStep.value = BottomSheetStep.Scope(event.entryPoint, event.contextData)
    }

    /** Channel-based overload: event is passed directly, StateFlow already cleared. */
    fun promoteSnackbarToFlow(event: SnackbarEvent) {
        _currentReminder = event.reminder
        _currentReminderBefore = event.reminderBefore
        _bottomSheetStep.value = BottomSheetStep.Scope(event.entryPoint, event.contextData)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Bottom-sheet progressive disclosure state (Flows 1-3)
    // ══════════════════════════════════════════════════════════════════

    sealed interface BottomSheetStep {
        object Hidden : BottomSheetStep
        data class Scope(val entryPoint: PreferenceEntryPoint, val contextData: Map<String, Any?>) : BottomSheetStep
        data class Reason(val entryPoint: PreferenceEntryPoint, val contextData: Map<String, Any?>, val scope: Int) : BottomSheetStep
        data class SubReason(val entryPoint: PreferenceEntryPoint, val contextData: Map<String, Any?>, val scope: Int, val reason: Int, val subOptions: List<Int>) : BottomSheetStep
        /** Syncing in progress after user completed selections. */
        data class Syncing(val entryPoint: PreferenceEntryPoint) : BottomSheetStep
    }

    private val _bottomSheetStep = MutableStateFlow<BottomSheetStep>(BottomSheetStep.Hidden)
    val bottomSheetStep: StateFlow<BottomSheetStep> = _bottomSheetStep

    // context for the current reminder being operated on
    private var _currentReminder: ReminderUnit? = null
    private var _currentReminderBefore: ReminderUnit? = null

    /**
     * Build rich contextData from a [ReminderUnit] so that n8n always receives
     * semantic content (title, description, flags) — not just metadata.
     *
     * Callers may pass extra entries via [extraContext]; they are merged in but
     * never override the reminder-derived keys.
     */
    private fun buildContextData(
        reminder: ReminderUnit?,
        reminderBefore: ReminderUnit? = null,
        extraContext: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val ctx = extraContext.toMutableMap()
        reminder?.let { r ->
            ctx["reminder"] = mapOf(
                "reminderId" to r.reminderId,
                "title" to r.reminderTitle,
                "content" to r.reminderContent,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "isCompleted" to r.isCompleted,
                "origin" to r.origin,
                "associatedNotiRecords" to r.associatedNotiRecords.toList(),
            )
        }
        reminderBefore?.let { r ->
            ctx["reminderBefore"] = mapOf(
                "reminderId" to r.reminderId,
                "title" to r.reminderTitle,
                "content" to r.reminderContent,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "isCompleted" to r.isCompleted,
            )
        }
        return ctx
    }

    /**
     * Entry point for preference learning.
     * - EDIT: opens BottomSheet immediately (user is already in a detail view).
     * - DELETE / MANUAL_EXTRACT: shows a lightweight Snackbar. Only if the user
     *   clicks the action button do we open the full BottomSheet.
     *
     * Rich contextData (reminder title/content, before/after snapshots) is
     * auto-built from [reminder] and [reminderBefore]; callers may pass additional
     * metadata (e.g. notiKey, appName) via [contextData] which is merged in.
     */
    fun startFlow(
        entryPoint: PreferenceEntryPoint,
        reminder: ReminderUnit?,
        reminderBefore: ReminderUnit? = null,
        contextData: Map<String, Any?> = emptyMap(),
    ) {
        val enriched = buildContextData(reminder, reminderBefore, contextData)

        when (entryPoint) {
            PreferenceEntryPoint.EDIT -> {
                // Opens BottomSheet immediately (user is in detail screen)
                _currentReminder = reminder
                _currentReminderBefore = reminderBefore
                _bottomSheetStep.value = BottomSheetStep.Scope(entryPoint, enriched)
            }
            PreferenceEntryPoint.DELETE,
            PreferenceEntryPoint.MANUAL_EXTRACT -> {
                // Show a lightweight Snackbar; only open BottomSheet if user clicks action
                _snackbarEvent.value = SnackbarEvent(
                    entryPoint = entryPoint,
                    reminder = reminder,
                    reminderBefore = reminderBefore,
                    contextData = enriched,
                )
            }
        }
    }

    fun dismissBottomSheet() {
        _bottomSheetStep.value = BottomSheetStep.Hidden
        _currentReminder = null
        _currentReminderBefore = null
    }

    fun selectScope(scopeResId: Int) {
        val current = _bottomSheetStep.value
        if (current !is BottomSheetStep.Scope) return

        when (scopeResId) {
            R.string.pref_scope_just_this_one, R.string.pref_scope_not_now -> {
                // Terminal choice — no learning needed
                dismissBottomSheet()
            }
            else -> {
                _bottomSheetStep.value = BottomSheetStep.Reason(
                    entryPoint = current.entryPoint,
                    contextData = current.contextData,
                    scope = scopeResId,
                )
            }
        }
    }

    fun selectReason(reasonResId: Int) {
        val current = _bottomSheetStep.value
        if (current !is BottomSheetStep.Reason) return

        if (reasonResId == R.string.pref_reason_other) {
            openChatFromFlow(current.entryPoint, current.contextData)
            return
        }

        val subOptions = getSubOptions(current.entryPoint, reasonResId)
        if (subOptions.isNotEmpty()) {
            _bottomSheetStep.value = BottomSheetStep.SubReason(
                entryPoint = current.entryPoint,
                contextData = current.contextData,
                scope = current.scope,
                reason = reasonResId,
                subOptions = subOptions,
            )
        } else {
            // No sub-options: fire quick-sync now
            val ctx = getApplication<Application>()
            fireQuickSync(
                current.entryPoint, current.contextData,
                ctx.getString(current.scope), ctx.getString(reasonResId), null,
            )
        }
    }

    fun selectSubReason(subReasonResId: Int) {
        val current = _bottomSheetStep.value
        if (current !is BottomSheetStep.SubReason) return

        if (subReasonResId == R.string.pref_sub_let_me_explain || subReasonResId == R.string.pref_reason_other) {
            openChatFromFlow(current.entryPoint, current.contextData)
            return
        }

        val ctx = getApplication<Application>()
        fireQuickSync(
            current.entryPoint, current.contextData,
            ctx.getString(current.scope), ctx.getString(current.reason), ctx.getString(subReasonResId),
        )
    }

    private fun getSubOptions(entryPoint: PreferenceEntryPoint, reasonResId: Int): List<Int> {
        return when (entryPoint) {
            PreferenceEntryPoint.DELETE -> when (reasonResId) {
                R.string.pref_reason_delete_handled -> listOf(
                    R.string.pref_sub_no_task_next_time,
                    R.string.pref_sub_only_if_not_handled,
                    R.string.pref_sub_save_as_memo,
                    R.string.pref_sub_ask_when_unsure,
                )
                R.string.pref_reason_delete_not_mine -> listOf(
                    R.string.pref_sub_only_directly_addressed,
                    R.string.pref_sub_only_clearly_for_me,
                    R.string.pref_sub_ask_ambiguous_group,
                )
                R.string.pref_reason_delete_informational -> listOf(
                    R.string.pref_sub_save_as_memo,
                    R.string.pref_sub_do_not_extract,
                    R.string.pref_sub_only_action_request,
                )
                R.string.pref_reason_delete_incorrect -> listOf(
                    R.string.pref_sub_wrong_wording,
                    R.string.pref_sub_wrong_person,
                    R.string.pref_sub_missing_context,
                    R.string.pref_sub_should_update,
                    R.string.pref_reason_other,
                )
                else -> emptyList()
            }
            PreferenceEntryPoint.EDIT -> listOf(
                R.string.pref_sub_same_sender,
                R.string.pref_sub_same_topic,
                R.string.pref_sub_similar_wording,
                R.string.pref_sub_same_thread,
                R.string.pref_sub_let_me_explain,
            )
            PreferenceEntryPoint.MANUAL_EXTRACT -> listOf(
                R.string.pref_sub_same_sender,
                R.string.pref_sub_same_topic,
                R.string.pref_sub_similar_wording,
                R.string.pref_sub_similar_timing,
                R.string.pref_sub_similar_update_type,
                R.string.pref_sub_let_me_explain,
            )
        }
    }

    private fun fireQuickSync(
        entryPoint: PreferenceEntryPoint,
        contextData: Map<String, Any?>,
        scope: String,
        reason: String,
        subReason: String?,
    ) {
        _bottomSheetStep.value = BottomSheetStep.Syncing(entryPoint)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = prefDao.getAllPreferences()
                val prefsPayload = prefs.map { p ->
                    mapOf("id" to p.id, "statement" to p.statement, "type" to p.preferenceType)
                }

                val request = QuickSyncRequest(
                    userId = SharedPreferencesManager.userId,
                    language =  Locale.getDefault().toLanguageTag(),
                    entryPoint = entryPoint.wire,
                    contextData = contextData,
                    userSelections = UserSelections(scope = scope, reason = reason, subReason = subReason),
                    currentPreferences = prefsPayload,
                )

                val result = PreferenceQuickSyncClient.sync(request)
                if (result != null) {
                    val now = System.currentTimeMillis()
                    val newPrefs = result.updatedPreferences.map { p ->
                        ExtractionPreference(
                            id = p.id,
                            statement = p.statement,
                            preferenceType = p.type,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }
                    prefDao.replacePreferences(newPrefs)

                    // Persist any conflicts detected by the backend
                    persistConflicts(result.conflicts, "QUICK_SYNC")

                    val toast = result.toastMessage
                    if (!toast.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), toast, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), R.string.pref_sync_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Quick-sync error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), R.string.pref_sync_error, Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _bottomSheetStep.value = BottomSheetStep.Hidden
                    _currentReminder = null
                    _currentReminderBefore = null
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Chat (Flow 4)
    // ══════════════════════════════════════════════════════════════════

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _pendingActions = MutableStateFlow<List<ProposedAction>>(emptyList())
    val pendingActions: StateFlow<List<ProposedAction>> = _pendingActions

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading

    private var _chatContextData: Map<String, Any?>? = null

    /** Contextual info shown as a card at the top of the chat screen. */
    private val _chatFlowContext = MutableStateFlow<ChatFlowContext?>(null)
    val chatFlowContext: StateFlow<ChatFlowContext?> = _chatFlowContext

    /**
     * Opens the Chat screen pre-seeded from a progressive-disclosure redirect.
     * Clears any previous conversation first so the new context is unambiguous.
     *
     * [contextData] is already enriched with reminder/reminderBefore maps
     * by [buildContextData] in [startFlow], so we only add flowEntryPoint here.
     */
    private fun openChatFromFlow(entryPoint: PreferenceEntryPoint, contextData: Map<String, Any?>) {
        // Clear previous conversation
        _chatMessages.value = emptyList()
        _pendingActions.value = emptyList()

        // Build a ChatFlowContext from the current reminder state
        val flowCtx = ChatFlowContext(
            entryPoint = entryPoint,
            reminderTitle = _currentReminder?.reminderTitle,
            reminderContent = _currentReminder?.reminderContent,
            reminderBeforeTitle = _currentReminderBefore?.reminderTitle,
            reminderBeforeContent = _currentReminderBefore?.reminderContent,
            notiKey = contextData["notiKey"] as? String,
        )
        _chatFlowContext.value = flowCtx

        // Merge in flowEntryPoint; reminder/reminderBefore are already present.
        val enrichedContext = contextData.toMutableMap().apply {
            put("flowEntryPoint", entryPoint.wire)
        }
        _chatContextData = enrichedContext

        _bottomSheetStep.value = BottomSheetStep.Hidden
        // The navigation to the Chat tab is triggered via _navigateToChat
        _navigateToChat.value = true
    }

    /** One-shot navigation flag consumed by the UI. */
    private val _navigateToChat = MutableStateFlow(false)
    val navigateToChat: StateFlow<Boolean> = _navigateToChat

    fun onChatNavigated() {
        _navigateToChat.value = false
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(role = "user", content = text)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = prefDao.getAllPreferences()
                val prefsPayload = prefs.map { p ->
                    mapOf("id" to p.id, "statement" to p.statement, "type" to p.preferenceType)
                }

                val request = ChatInteractRequest(
                    userId = SharedPreferencesManager.userId,
                    language = Locale.getDefault().toLanguageTag(),
                    chatHistory = _chatMessages.value,
                    contextData = _chatContextData,
                    currentPreferences = prefsPayload,
                )

                val result = PreferenceChatClient.interact(request)

                // Persist any conflicts on IO thread before switching to Main
                if (result != null) {
                    persistConflicts(result.conflicts, "CHAT_INTERACT")
                }

                withContext(Dispatchers.Main) {
                    if (result != null) {
                        val assistantMsg = ChatMessage(role = "assistant", content = result.assistantMessage)
                        _chatMessages.value = _chatMessages.value + assistantMsg

                        if (result.proposedActions.isNotEmpty()) {
                            val actions = result.proposedActions.map { dto ->
                                ProposedAction(
                                    actionId = dto.actionId,
                                    type = try {
                                        ProposedActionType.valueOf(dto.type)
                                    } catch (_: Exception) {
                                        ProposedActionType.ADD
                                    },
                                    targetPreferenceId = dto.targetPreferenceId,
                                    newStatement = dto.newStatement,
                                    newPreferenceType = dto.newPreferenceType,
                                )
                            }
                            _pendingActions.value = _pendingActions.value + actions
                        }
                    } else {
                        val errorMsg = ChatMessage(
                            role = "assistant",
                            content = "Sorry, I couldn't reach the server. Please check your network connection and try again."
                        )
                        _chatMessages.value = _chatMessages.value + errorMsg
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat interact error", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = ChatMessage(
                        role = "assistant",
                        content = "An error occurred. Please try again."
                    )
                    _chatMessages.value = _chatMessages.value + errorMsg
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isChatLoading.value = false
                }
            }
        }
    }

    fun confirmProposedAction(actionId: String) {
        val action = _pendingActions.value.find { it.actionId == actionId } ?: return
        if (action.confirmed || action.dismissed) return

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            when (action.type) {
                ProposedActionType.ADD -> {
                    val pref = ExtractionPreference(
                        id = action.actionId,
                        statement = action.newStatement ?: "",
                        preferenceType = action.newPreferenceType ?: "",
                        createdAt = now,
                        updatedAt = now,
                    )
                    prefDao.upsertPreference(pref)
                }
                ProposedActionType.MODIFY -> {
                    val targetId = action.targetPreferenceId ?: action.actionId
                    val pref = ExtractionPreference(
                        id = targetId,
                        statement = action.newStatement ?: "",
                        preferenceType = action.newPreferenceType ?: "",
                        createdAt = now,
                        updatedAt = now,
                    )
                    prefDao.upsertPreference(pref)
                }
                ProposedActionType.DELETE -> {
                    val targetId = action.targetPreferenceId ?: return@launch
                    prefDao.deletePreference(targetId)
                }
            }

            withContext(Dispatchers.Main) {
                val updatedActions = _pendingActions.value.map {
                    if (it.actionId == actionId) it.copy(confirmed = true) else it
                }
                _pendingActions.value = updatedActions

                // Auto-clear: if all pending actions are now confirmed/dismissed
                // and no user message came after the last assistant response, clear
                // the chat so it's fresh for the next interaction.
                maybeAutoClearChat(updatedActions)
            }
        }
    }

    fun dismissProposedAction(actionId: String) {
        val updatedActions = _pendingActions.value.map {
            if (it.actionId == actionId) it.copy(dismissed = true) else it
        }
        _pendingActions.value = updatedActions
        maybeAutoClearChat(updatedActions)
    }

    /**
     * If every pending action has been confirmed or dismissed and the user
     * did not send any further chat messages after the last assistant reply,
     * automatically clear the chat for a clean slate.
     */
    private fun maybeAutoClearChat(actions: List<ProposedAction>) {
        if (actions.isEmpty()) return
        val allResolved = actions.all { it.confirmed || it.dismissed }
        if (!allResolved) return

        // Check whether the user typed anything after the last assistant message
        val msgs = _chatMessages.value
        val lastAssistantIdx = msgs.indexOfLast { it.role == "assistant" }
        val hasUserMsgAfter = msgs.drop(lastAssistantIdx + 1).any { it.role == "user" }
        if (hasUserMsgAfter) return

        // All resolved, no further user input → clear
        clearChatHistory()
    }

    fun clearChatHistory() {
        _chatMessages.value = emptyList()
        _pendingActions.value = emptyList()
        _chatContextData = null
        _chatFlowContext.value = null
    }

    /** Delete a single active preference (from the active rules UI). */
    fun deleteActivePreference(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.deletePreference(id)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Conflict management
    // ══════════════════════════════════════════════════════════════════

    /** Dismiss / ignore a conflict — removes it from the local DB. */
    fun dismissConflict(conflictId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            conflictDao.deleteConflict(conflictId)
        }
    }

    /**
     * Resolve a conflict via Chat.
     * Deletes the conflict from Room, pre-seeds the chat with the conflict
     * description as context, and navigates to the chat screen.
     */
    fun resolveConflictInChat(conflict: PreferenceConflict) {
        viewModelScope.launch(Dispatchers.IO) {
            conflictDao.deleteConflict(conflict.conflictId)
        }

        // Clear previous conversation
        _chatMessages.value = emptyList()
        _pendingActions.value = emptyList()
        _chatFlowContext.value = null

        _chatContextData = mapOf("conflictId" to conflict.conflictId)

        // Send the conflict description as a user message to start the conversation.
        // sendChatMessage adds the user message to _chatMessages internally.
        sendChatMessage("I'd like to resolve this conflict in my preferences: ${conflict.description}")

        // Navigate to the chat tab
        _navigateToChat.value = true
    }
}



















