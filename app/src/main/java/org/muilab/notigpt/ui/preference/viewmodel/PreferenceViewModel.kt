package org.muilab.notigpt.ui.preference.viewmodel

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
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.dao.ExtractionPreferenceDao
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.PreferenceConflictDao
import org.muilab.notigpt.data.local.room.dao.SavedItemDao
import org.muilab.notigpt.data.local.room.dao.UserContextDao
import org.muilab.notigpt.data.remote.n8n.PreferenceChatClient
import org.muilab.notigpt.data.remote.n8n.PreferenceContextDiscoverClient
import org.muilab.notigpt.data.remote.n8n.PreferenceQuickSyncClient
import org.muilab.notigpt.ui.preference.model.ChatFlowContext
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractRequestDto
import org.muilab.notigpt.ui.preference.model.ChatMessage
import org.muilab.notigpt.data.remote.n8n.dto.N8nConflictDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nContextDiscoverRequestDto
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.PreferenceConflict
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.ui.preference.model.ProposedAction
import org.muilab.notigpt.ui.preference.model.ProposedActionType
import org.muilab.notigpt.data.remote.n8n.dto.N8nQuickSyncRequestDto
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.UserContext
import org.muilab.notigpt.data.remote.n8n.dto.N8nUserSelectionsDto
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

    private val userContextDao: UserContextDao =
        AppDatabase.getInstance(application).userContextDao()

    private val drawerDao: NotiDrawerDao =
        AppDatabase.getInstance(application).drawerDao()

    private val reminderListDao: SavedItemDao =
        AppDatabase.getInstance(application).reminderListDao()

    companion object {
        /** Max number of notification summaries sent to the context-discover endpoint. */
        const val CONTEXT_DISCOVER_NOTI_LIMIT = 80
    }

    // ══════════════════════════════════════════════════════════════════
    //  Active preferences (from Room)
    // ══════════════════════════════════════════════════════════════════

    val activePreferences: StateFlow<List<ExtractionPreference>> =
        prefDao.getAllPreferencesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ══════════════════════════════════════════════════════════════════
    //  Active user contexts (from Room)
    // ══════════════════════════════════════════════════════════════════

    val activeContexts: StateFlow<List<UserContext>> =
        userContextDao.getAllContextsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ══════════════════════════════════════════════════════════════════
    //  Unresolved conflicts (from Room)
    // ══════════════════════════════════════════════════════════════════

    val unresolvedConflicts: StateFlow<List<PreferenceConflict>> =
        conflictDao.getAllConflictsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Persist conflicts returned by the backend into Room. */
    private suspend fun persistConflicts(dtos: List<N8nConflictDto>, source: String) {
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
        val reminder: SavedItem?,
        val reminderBefore: SavedItem?,
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
        data class RuleSelection(val entryPoint: PreferenceEntryPoint, val contextData: Map<String, Any?>, val scope: Int, val ruleOptions: List<Int>) : BottomSheetStep
        /** Syncing in progress after user completed selections. */
        data class Syncing(val entryPoint: PreferenceEntryPoint) : BottomSheetStep
    }

    private val _bottomSheetStep = MutableStateFlow<BottomSheetStep>(BottomSheetStep.Hidden)
    val bottomSheetStep: StateFlow<BottomSheetStep> = _bottomSheetStep

    // context for the current reminder being operated on
    private var _currentReminder: SavedItem? = null
    private var _currentReminderBefore: SavedItem? = null

    /**
     * Build rich contextData from a [SavedItem] so that n8n always receives
     * semantic content (title, description, flags) — not just metadata.
     *
     * Callers may pass extra entries via [extraContext]; they are merged in but
     * never override the reminder-derived keys.
     */
    private fun buildContextData(
        reminder: SavedItem?,
        reminderBefore: SavedItem? = null,
        extraContext: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val ctx = extraContext.toMutableMap()
        reminder?.let { r ->
            ctx["reminder"] = mapOf(
                "savedItemId" to r.savedItemId,
                "title" to r.title,
                "content" to r.content,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "isCompleted" to r.isCompleted,
                "origin" to r.origin,
            )
        }
        reminderBefore?.let { r ->
            ctx["reminderBefore"] = mapOf(
                "savedItemId" to r.savedItemId,
                "title" to r.title,
                "content" to r.content,
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
        reminder: SavedItem?,
        reminderBefore: SavedItem? = null,
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
                val ruleOptions = getRuleOptions(current.entryPoint)
                _bottomSheetStep.value = BottomSheetStep.RuleSelection(
                    entryPoint = current.entryPoint,
                    contextData = current.contextData,
                    scope = scopeResId,
                    ruleOptions = ruleOptions,
                )
            }
        }
    }

    /**
     * Returns the concrete rule options for a given entry point.
     */
    private fun getRuleOptions(entryPoint: PreferenceEntryPoint): List<Int> {
        return when (entryPoint) {
            PreferenceEntryPoint.DELETE -> listOf(
                R.string.pref_rule_delete_sender,
                R.string.pref_rule_delete_group,
                R.string.pref_rule_delete_info,
                R.string.pref_rule_delete_topic,
                R.string.pref_rule_delete_app,
                R.string.pref_reason_other,
            )
            PreferenceEntryPoint.MANUAL_EXTRACT -> listOf(
                R.string.pref_rule_extract_sender,
                R.string.pref_rule_extract_thread,
                R.string.pref_rule_extract_wording,
                R.string.pref_rule_extract_situational,
                R.string.pref_reason_other,
            )
            PreferenceEntryPoint.EDIT -> listOf(
                R.string.pref_rule_edit_context,
                R.string.pref_rule_edit_urgency,
                R.string.pref_rule_edit_responsibility,
                R.string.pref_rule_edit_granularity,
                R.string.pref_reason_other,
            )
        }
    }

    /**
     * Called when user selects a concrete rule from the RuleSelection step.
     */
    fun selectRule(ruleResId: Int) {
        val current = _bottomSheetStep.value
        if (current !is BottomSheetStep.RuleSelection) return

        if (ruleResId == R.string.pref_reason_other) {
            openChatFromFlow(current.entryPoint, current.contextData)
            return
        }

        val ctx = getApplication<Application>()
        fireQuickSync(
            current.entryPoint, current.contextData,
            ctx.getString(current.scope), ctx.getString(ruleResId), null,
        )
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

                val contexts = userContextDao.getAllContexts()
                val contextsPayload = contexts.map { c ->
                    mapOf("id" to c.id, "statement" to c.statement, "category" to c.category)
                }

                val request = N8nQuickSyncRequestDto(
                    userId = SharedPreferencesManager.userId,
                    language =  Locale.getDefault().toLanguageTag(),
                    entryPoint = entryPoint.wire,
                    contextData = contextData,
                    userSelections = N8nUserSelectionsDto(scope = scope, reason = reason, subReason = subReason),
                    currentPreferences = prefsPayload,
                    userContexts = contextsPayload,
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

    /** "RULES" or "ABOUT_ME" — determines the LLM system prompt on n8n. */
    private val _chatMode = MutableStateFlow("RULES")
    val chatMode: StateFlow<String> = _chatMode

    fun switchChatMode(mode: String) {
        if (_chatMode.value == mode) return
        _chatMode.value = mode
        // Clear chat when switching modes to avoid cross-context confusion
        clearChatHistory()
    }

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
            title = _currentReminder?.title,
            content = _currentReminder?.content,
            reminderBeforeTitle = _currentReminderBefore?.title,
            reminderBeforeContent = _currentReminderBefore?.content,
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

                val contexts = userContextDao.getAllContexts()
                val contextsPayload = contexts.map { c ->
                    mapOf("id" to c.id, "statement" to c.statement, "category" to c.category)
                }

                val request = N8nChatInteractRequestDto(
                    userId = SharedPreferencesManager.userId,
                    language = Locale.getDefault().toLanguageTag(),
                    chatHistory = _chatMessages.value,
                    contextData = _chatContextData,
                    currentPreferences = prefsPayload,
                    userContexts = contextsPayload,
                    chatMode = _chatMode.value,
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
                                    targetType = dto.targetType,
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
            val isContext = action.targetType == "CONTEXT"

            when (action.type) {
                ProposedActionType.ADD -> {
                    if (isContext) {
                        val ctx = UserContext(
                            id = action.actionId,
                            statement = action.newStatement ?: "",
                            category = action.newPreferenceType ?: "general",
                            createdAt = now,
                            updatedAt = now,
                        )
                        userContextDao.upsertContext(ctx)
                    } else {
                        val pref = ExtractionPreference(
                            id = action.actionId,
                            statement = action.newStatement ?: "",
                            preferenceType = action.newPreferenceType ?: "",
                            createdAt = now,
                            updatedAt = now,
                        )
                        prefDao.upsertPreference(pref)
                    }
                }
                ProposedActionType.MODIFY -> {
                    val targetId = action.targetPreferenceId ?: action.actionId
                    if (isContext) {
                        val ctx = UserContext(
                            id = targetId,
                            statement = action.newStatement ?: "",
                            category = action.newPreferenceType ?: "general",
                            createdAt = now,
                            updatedAt = now,
                        )
                        userContextDao.upsertContext(ctx)
                    } else {
                        val pref = ExtractionPreference(
                            id = targetId,
                            statement = action.newStatement ?: "",
                            preferenceType = action.newPreferenceType ?: "",
                            createdAt = now,
                            updatedAt = now,
                        )
                        prefDao.upsertPreference(pref)
                    }
                }
                ProposedActionType.DELETE -> {
                    val targetId = action.targetPreferenceId ?: return@launch
                    if (isContext) {
                        userContextDao.deleteContext(targetId)
                    } else {
                        prefDao.deletePreference(targetId)
                    }
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

    /** Delete a single active user context (from the About Me UI). */
    fun deleteActiveContext(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userContextDao.deleteContext(id)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  "Learn About Me" — proactive context discovery
    // ══════════════════════════════════════════════════════════════════

    /**
     * Sends a curated summary of notifications and reminders to an n8n endpoint
     * that infers factual statements about the user.
     * Results are displayed as ProposedAction cards in the chat UI.
     * Chat remains active afterwards so the user can correct / discuss.
     */
    fun discoverUserContext() {
        if (_isChatLoading.value) return
        _isChatLoading.value = true

        val systemMsg = ChatMessage(
            role = "assistant",
            content = getApplication<Application>().getString(R.string.pref_chat_discover_analyzing),
        )
        _chatMessages.value = _chatMessages.value + systemMsg

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Curated notification summary (most recent active notis)
                val activeNotis = drawerDao.getAllActive()
                    .sortedByDescending { it.lastUpdateTime }
                    .take(CONTEXT_DISCOVER_NOTI_LIMIT)
                val notiSummary = activeNotis.map { noti ->
                    mapOf(
                        "appName" to noti.appName,
                        "summary" to noti.summary,
                    )
                }

                // Current visible reminders
                val reminders = reminderListDao.getAllVisible()
                val remindersPayload = reminders.map { r ->
                    mapOf(
                        "title" to r.title,
                        "content" to r.content,
                        "isTask" to r.isTask.toString(),
                    )
                }

                // Existing user contexts (to avoid duplicates)
                val contexts = userContextDao.getAllContexts()
                val contextsPayload = contexts.map { c ->
                    mapOf("id" to c.id, "statement" to c.statement, "category" to c.category)
                }

                val request = N8nContextDiscoverRequestDto(
                    userId = SharedPreferencesManager.userId,
                    language = Locale.getDefault().toLanguageTag(),
                    notificationSummary = notiSummary,
                    currentReminders = remindersPayload,
                    existingUserContexts = contextsPayload,
                )

                val result = PreferenceContextDiscoverClient.discover(request)

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
                                    targetType = dto.targetType ?: "CONTEXT",
                                )
                            }
                            _pendingActions.value = _pendingActions.value + actions
                        }
                    } else {
                        val errorMsg = ChatMessage(
                            role = "assistant",
                            content = "Sorry, I couldn't analyze your notifications right now. Please check your network and try again.",
                        )
                        _chatMessages.value = _chatMessages.value + errorMsg
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Context discover error", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = ChatMessage(
                        role = "assistant",
                        content = "An error occurred while analyzing your notifications.",
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



















