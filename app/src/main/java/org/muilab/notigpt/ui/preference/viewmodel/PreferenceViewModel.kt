package org.muilab.notigpt.ui.preference.viewmodel

import android.app.Application
import android.util.Log
import org.muilab.notigpt.ui.common.feedback.AppSnackbar
import org.muilab.notigpt.ui.common.feedback.AppSnackbarMessage
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

    private val savedItemDao: SavedItemDao =
        AppDatabase.getInstance(application).savedItemDao()

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
        val item: SavedItem?,
        val savedItemBefore: SavedItem?,
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
        promoteSnackbarToFlow(event)
    }

    /** Channel-based overload: event is passed directly, StateFlow already cleared. */
    fun promoteSnackbarToFlow(event: SnackbarEvent) {
        _currentReminder = event.item
        _currentSavedItemBefore = event.savedItemBefore
        _bottomSheetStep.value = BottomSheetStep.Chips(event.entryPoint, event.contextData, getChipOptions(event.entryPoint))
    }

    // ══════════════════════════════════════════════════════════════════
    //  Bottom-sheet progressive disclosure state (Flows 1-3)
    // ══════════════════════════════════════════════════════════════════

    sealed interface BottomSheetStep {
        object Hidden : BottomSheetStep

        /**
         * Single-step chip prompt: one tap picks the reason (which also implies "make a rule"). The
         * old Scope → RuleSelection → Syncing progression collapsed here — sync runs in the background
         * and the sheet closes immediately on tap, so there's no in-sheet syncing state.
         */
        data class Chips(
            val entryPoint: PreferenceEntryPoint,
            val contextData: Map<String, Any?>,
            val options: List<Int>,
        ) : BottomSheetStep
    }

    private val _bottomSheetStep = MutableStateFlow<BottomSheetStep>(BottomSheetStep.Hidden)
    val bottomSheetStep: StateFlow<BottomSheetStep> = _bottomSheetStep

    // context for the current item being operated on
    private var _currentReminder: SavedItem? = null
    private var _currentSavedItemBefore: SavedItem? = null

    /**
     * Build rich contextData from a [SavedItem] so that n8n always receives
     * semantic content (title, description, flags) — not just metadata.
     *
     * Callers may pass extra entries via [extraContext]; they are merged in but
     * never override the item-derived keys.
     */
    private fun buildContextData(
        item: SavedItem?,
        savedItemBefore: SavedItem? = null,
        extraContext: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val ctx = extraContext.toMutableMap()
        item?.let { r ->
            ctx["item"] = mapOf(
                "savedItemId" to r.savedItemId,
                "title" to r.title,
                "content" to r.content,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "isCompleted" to r.isCompleted,
                "origin" to r.origin,
            )
        }
        savedItemBefore?.let { r ->
            ctx["savedItemBefore"] = mapOf(
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
     * Rich contextData (item title/content, before/after snapshots) is
     * auto-built from [item] and [savedItemBefore]; callers may pass additional
     * metadata (e.g. notiKey, appName) via [contextData] which is merged in.
     */
    fun startFlow(
        entryPoint: PreferenceEntryPoint,
        item: SavedItem?,
        savedItemBefore: SavedItem? = null,
        contextData: Map<String, Any?> = emptyMap(),
    ) {
        val enriched = buildContextData(item, savedItemBefore, contextData)

        when (entryPoint) {
            PreferenceEntryPoint.EDIT -> {
                // Opens BottomSheet immediately (user is in detail screen)
                _currentReminder = item
                _currentSavedItemBefore = savedItemBefore
                _bottomSheetStep.value = BottomSheetStep.Chips(entryPoint, enriched, getChipOptions(entryPoint))
            }
            PreferenceEntryPoint.DELETE,
            PreferenceEntryPoint.MANUAL_EXTRACT -> {
                // Show a lightweight Snackbar; only open BottomSheet if user clicks action
                _snackbarEvent.value = SnackbarEvent(
                    entryPoint = entryPoint,
                    item = item,
                    savedItemBefore = savedItemBefore,
                    contextData = enriched,
                )
            }
        }
    }

    /**
     * Opens the preference-learning bottom sheet immediately for [entryPoint], skipping the snackbar
     * step. Used when the trigger is itself a deliberate tap (e.g. the review screen's "Tell it why").
     */
    fun startFlowSheet(
        entryPoint: PreferenceEntryPoint,
        item: SavedItem?,
        savedItemBefore: SavedItem? = null,
        contextData: Map<String, Any?> = emptyMap(),
    ) {
        val enriched = buildContextData(item, savedItemBefore, contextData)
        _currentReminder = item
        _currentSavedItemBefore = savedItemBefore
        _bottomSheetStep.value = BottomSheetStep.Chips(entryPoint, enriched, getChipOptions(entryPoint))
    }

    fun dismissBottomSheet() {
        _bottomSheetStep.value = BottomSheetStep.Hidden
        _currentReminder = null
        _currentSavedItemBefore = null
    }

    /**
     * One-step chip set per entry point: a "just this once" terminal option, the concrete reason
     * chips, and an "Other" escape to chat. The reason chip both scopes ("make a rule") and states
     * the reason, so there's no separate scope step. Broken chips from the old two-step flow
     * (delete_group / extract_wording / edit_responsibility, and the redundant scope chips) are gone.
     */
    private fun getChipOptions(entryPoint: PreferenceEntryPoint): List<Int> {
        val reasons = when (entryPoint) {
            PreferenceEntryPoint.DELETE -> listOf(
                R.string.pref_rule_delete_sender,
                R.string.pref_rule_delete_info,
                R.string.pref_rule_delete_topic,
                R.string.pref_rule_delete_app,
            )
            PreferenceEntryPoint.MANUAL_EXTRACT -> listOf(
                R.string.pref_rule_extract_sender,
                R.string.pref_rule_extract_thread,
                R.string.pref_rule_extract_situational,
            )
            PreferenceEntryPoint.EDIT -> listOf(
                R.string.pref_rule_edit_context,
                R.string.pref_rule_edit_urgency,
                R.string.pref_rule_edit_granularity,
            )
        }
        return listOf(R.string.pref_scope_just_this_one) + reasons + R.string.pref_reason_other
    }

    /** Single-step chip selection: terminal "just this once", "Other" → chat, else fire the sync. */
    fun selectChip(chipResId: Int) {
        val current = _bottomSheetStep.value
        if (current !is BottomSheetStep.Chips) return

        when (chipResId) {
            R.string.pref_scope_just_this_one -> dismissBottomSheet()
            R.string.pref_reason_other -> openChatFromFlow(current.entryPoint, current.contextData)
            else -> {
                val ctx = getApplication<Application>()
                fireQuickSync(current.entryPoint, current.contextData, "rule", ctx.getString(chipResId), null)
            }
        }
    }

    /**
     * Non-blocking quick-sync: the sheet closes immediately and the round-trip runs in the
     * background. Created and updated rules auto-apply; deletion *proposals* never auto-apply — they
     * park as confirm-before-delete conflicts. A "Rules updated" snackbar offers a Review action to
     * confirm/veto each change; ignoring it leaves the applied creates/updates standing and the
     * parked deletions waiting.
     */
    private fun fireQuickSync(
        entryPoint: PreferenceEntryPoint,
        contextData: Map<String, Any?>,
        scope: String,
        reason: String,
        subReason: String?,
    ) {
        // Close the sheet right away — the user can navigate anywhere while the sync runs.
        _bottomSheetStep.value = BottomSheetStep.Hidden
        _currentReminder = null
        _currentSavedItemBefore = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefsBefore = prefDao.getAllPreferences()
                val prefsPayload = prefsBefore.map { p ->
                    mapOf("id" to p.id, "statement" to p.statement, "type" to p.preferenceType)
                }
                val beforeById = prefsBefore.associateBy { it.id }

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
                if (result == null) {
                    AppSnackbar.show(getApplication<Application>().getString(R.string.pref_sync_failed))
                    return@launch
                }

                val now = System.currentTimeMillis()
                val reviewRules = mutableListOf<ReviewRule>()

                // Created + updated rules apply immediately.
                result.createdRules.filter { it.id.isNotBlank() }.forEach { p ->
                    prefDao.upsertPreference(
                        ExtractionPreference(id = p.id, statement = p.statement, preferenceType = p.type, createdAt = now, updatedAt = now)
                    )
                    reviewRules += ReviewRule(p.id, p.statement, p.type, ReviewRule.Kind.Created)
                }
                result.updatedRules.filter { it.id.isNotBlank() }.forEach { p ->
                    val before = beforeById[p.id]
                    prefDao.upsertPreference(
                        ExtractionPreference(
                            id = p.id, statement = p.statement, preferenceType = p.type,
                            createdAt = before?.createdAt ?: now, updatedAt = now,
                        )
                    )
                    reviewRules += ReviewRule(p.id, p.statement, p.type, ReviewRule.Kind.Updated, previousStatement = before?.statement)
                }

                // Deletion proposals do NOT auto-apply: park each as a confirm-before-delete conflict.
                val deletions = result.deletedRuleIds.mapNotNull { beforeById[it] }
                if (deletions.isNotEmpty()) {
                    conflictDao.insertAll(
                        deletions.map { p ->
                            PreferenceConflict(
                                conflictId = "delete_${p.id}",
                                description = getApplication<Application>().getString(R.string.pref_delete_proposal, p.statement),
                                involvedPreferenceIds = p.id,
                                source = "QUICK_SYNC_DELETE",
                                createdAt = now,
                            )
                        }
                    )
                    deletions.forEach { reviewRules += ReviewRule(it.id, it.statement, it.preferenceType, ReviewRule.Kind.Deleted) }
                }

                pushPrefsToCloud()
                persistConflicts(result.conflicts, "QUICK_SYNC")

                if (reviewRules.isEmpty()) {
                    result.toastMessage?.takeIf { it.isNotBlank() }?.let { AppSnackbar.show(it) }
                } else {
                    val review = QuickSyncReview(reviewRules)
                    AppSnackbar.show(
                        AppSnackbarMessage(
                            text = result.toastMessage?.takeIf { it.isNotBlank() }
                                ?: getApplication<Application>().getString(R.string.pref_rules_updated),
                            actionLabel = getApplication<Application>().getString(R.string.pref_review),
                            onAction = { _quickSyncReview.value = review },
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Quick-sync error", e)
                AppSnackbar.show(getApplication<Application>().getString(R.string.pref_sync_error))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Quick-sync review (confirm/veto applied creates/updates + parked deletions)
    // ══════════════════════════════════════════════════════════════════

    /** One rule surfaced in the review dialog, with the change kind and (for updates) its prior text. */
    data class ReviewRule(
        val id: String,
        val statement: String,
        val type: String,
        val kind: Kind,
        val previousStatement: String? = null,
    ) {
        enum class Kind { Created, Updated, Deleted }
    }

    data class QuickSyncReview(val rules: List<ReviewRule>)

    private val _quickSyncReview = MutableStateFlow<QuickSyncReview?>(null)
    val quickSyncReview: StateFlow<QuickSyncReview?> = _quickSyncReview

    fun dismissQuickSyncReview() {
        _quickSyncReview.value = null
    }

    /**
     * Applies the user's confirm/veto decisions from the review dialog. [confirmedIds] are the rule
     * ids the user kept checked: created/updated stay applied (unchecked reverts them), and deletions
     * go through only when confirmed (unchecked keeps the rule). Either way the parked deletion
     * conflict is resolved.
     */
    fun applyReviewDecisions(review: QuickSyncReview, confirmedIds: Set<String>) {
        _quickSyncReview.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            review.rules.forEach { rule ->
                val confirmed = rule.id in confirmedIds
                when (rule.kind) {
                    ReviewRule.Kind.Created -> if (!confirmed) prefDao.deletePreference(rule.id)
                    ReviewRule.Kind.Updated -> if (!confirmed) {
                        rule.previousStatement?.let { prev ->
                            prefDao.upsertPreference(
                                ExtractionPreference(id = rule.id, statement = prev, preferenceType = rule.type, createdAt = now, updatedAt = now)
                            )
                        } ?: prefDao.deletePreference(rule.id)
                    }
                    ReviewRule.Kind.Deleted -> {
                        if (confirmed) prefDao.deletePreference(rule.id)
                        conflictDao.deleteConflict("delete_${rule.id}")
                    }
                }
            }
            pushPrefsToCloud()
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
     * [contextData] is already enriched with item/savedItemBefore maps
     * by [buildContextData] in [startFlow], so we only add flowEntryPoint here.
     */
    private fun openChatFromFlow(entryPoint: PreferenceEntryPoint, contextData: Map<String, Any?>) {
        // Clear previous conversation
        _chatMessages.value = emptyList()
        _pendingActions.value = emptyList()

        // Build a ChatFlowContext from the current item state
        val flowCtx = ChatFlowContext(
            entryPoint = entryPoint,
            title = _currentReminder?.title,
            content = _currentReminder?.content,
            savedItemBeforeTitle = _currentSavedItemBefore?.title,
            savedItemBeforeContent = _currentSavedItemBefore?.content,
            notiKey = contextData["notiKey"] as? String,
        )
        _chatFlowContext.value = flowCtx

        // Merge in flowEntryPoint; item/savedItemBefore are already present.
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
                        pushPrefsToCloud()
                    } else {
                        val pref = ExtractionPreference(
                            id = action.actionId,
                            statement = action.newStatement ?: "",
                            preferenceType = action.newPreferenceType ?: "",
                            createdAt = now,
                            updatedAt = now,
                        )
                        prefDao.upsertPreference(pref)
                        pushPrefsToCloud()
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
                        pushPrefsToCloud()
                    } else {
                        val pref = ExtractionPreference(
                            id = targetId,
                            statement = action.newStatement ?: "",
                            preferenceType = action.newPreferenceType ?: "",
                            createdAt = now,
                            updatedAt = now,
                        )
                        prefDao.upsertPreference(pref)
                        pushPrefsToCloud()
                    }
                }
                ProposedActionType.DELETE -> {
                    val targetId = action.targetPreferenceId ?: return@launch
                    if (isContext) {
                        userContextDao.deleteContext(targetId)
                        pushPrefsToCloud()
                    } else {
                        prefDao.deletePreference(targetId)
                        pushPrefsToCloud()
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

    /** Batch controls for when the LLM proposes many rules at once (chat NL stays available). */
    fun confirmAllProposedActions() {
        _pendingActions.value
            .filter { !it.confirmed && !it.dismissed }
            .forEach { confirmProposedAction(it.actionId) }
    }

    fun dismissAllProposedActions() {
        val updated = _pendingActions.value.map {
            if (!it.confirmed && !it.dismissed) it.copy(dismissed = true) else it
        }
        _pendingActions.value = updated
        maybeAutoClearChat(updated)
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

    /** Mirrors the full preference/context sets to Firestore after any mutation (best-effort). */
    private suspend fun pushPrefsToCloud() {
        try {
            org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository(getApplication())
                .syncPreferencesAndContexts()
        } catch (_: Exception) {
        }
    }

    /** Delete a single active preference (from the active rules UI). */
    fun deleteActivePreference(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.deletePreference(id)
            pushPrefsToCloud()
        }
    }

    /** Delete a single active user context (from the About Me UI). */
    fun deleteActiveContext(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userContextDao.deleteContext(id)
            pushPrefsToCloud()
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  "Learn About Me" — proactive context discovery
    // ══════════════════════════════════════════════════════════════════

    /**
     * Sends a curated summary of notifications and savedItems to an n8n endpoint
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

                // Current saved items
                val savedItems = savedItemDao.getAll()
                val savedItemsPayload = savedItems.map { r ->
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
                    currentSavedItems = savedItemsPayload,
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








