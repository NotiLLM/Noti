package org.muilab.notigpt.model.features

import com.google.gson.Gson

/** Durable state for translating one review preview without changing its underlying proposal. */
data class ReviewTranslationState(
    val status: String,
    val targetLanguage: String,
    val sourceItem: SavedItem,
    val sourceSteps: List<TodoStep>,
    val evidenceRecordIds: List<String>,
    /** Pending op ids snapshotted for staged groups; empty for legacy review rows. */
    val sourceOpIds: List<Long> = emptyList(),
    val translatedItem: SavedItem? = null,
    val translatedSteps: List<TodoStep> = emptyList(),
    val sourceBatch: List<ReviewItemDraft>? = null,
    val translatedBatch: List<ReviewItemDraft>? = null,
) {
    val isPending: Boolean get() = status == STATUS_PENDING
    val translatedDraft: ReviewItemDraft?
        get() = translatedItem?.let { ReviewItemDraft(it, translatedSteps) }

    val translatedBatchDrafts: List<ReviewItemDraft>
        get() = translatedBatch.orEmpty()

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_READY = "ready"

        private val gson = Gson()

        fun pending(
            targetLanguage: String,
            source: ReviewItemDraft,
            evidenceRecordIds: List<String>,
            sourceOpIds: List<Long> = emptyList(),
            sourceBatch: List<ReviewItemDraft> = emptyList(),
        ) = ReviewTranslationState(
            status = STATUS_PENDING,
            targetLanguage = targetLanguage,
            sourceItem = source.item,
            sourceSteps = source.steps,
            evidenceRecordIds = evidenceRecordIds.filter(String::isNotBlank).distinct(),
            sourceOpIds = sourceOpIds.distinct(),
            sourceBatch = sourceBatch,
        )

        fun fromJson(value: String?): ReviewTranslationState? = value
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { gson.fromJson(it, ReviewTranslationState::class.java) }.getOrNull() }

        fun toJson(value: ReviewTranslationState): String = gson.toJson(value)
    }
}
