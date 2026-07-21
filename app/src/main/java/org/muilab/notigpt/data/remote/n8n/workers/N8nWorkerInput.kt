package org.muilab.notigpt.data.remote.n8n.workers

import androidx.work.Data
import org.muilab.notigpt.util.Constants.Companion.N8N_EXTRACTION_PIPELINE
import org.muilab.notigpt.util.Constants.Companion.N8N_REFLECTION_PIPELINE
import org.muilab.notigpt.util.Constants.Companion.N8N_PREFERENCE_QUICK_SYNC
import org.muilab.notigpt.util.Constants.Companion.N8N_REGENERATE_ONE
import org.muilab.notigpt.util.Constants.Companion.N8N_REVIEW_TRANSLATION

/**
 * Typed view of WorkManager input data for N8nAPIWorker.
 *
 * This is the boundary between stringly-typed WorkManager Data and workflow handlers. Keep Data key parsing here
 * so handlers receive validated job intent instead of raw key/value bundles.
 */
sealed interface N8nWorkerInput {
    val webhookPath: String

    /** Per-notiKey extraction pipeline (A→B→C→D1→E1). [forced] skips scan and starts at B. */
    data class ExtractionPipeline(
        override val webhookPath: String,
        val notiKey: String,
        val forced: Boolean,
    ) : N8nWorkerInput

    /** Periodic cross-thread reflection merge (D2→E2). */
    data class ReflectionPipeline(
        override val webhookPath: String,
    ) : N8nWorkerInput

    /** Fires a quick-sync of preference selections to the backend. */
    data class PreferenceQuickSync(
        override val webhookPath: String,
        val payloadJson: String,
    ) : N8nWorkerInput

    /** Regenerate a single SavedItem. */
    data class RegenerateOne(
        override val webhookPath: String,
        val savedItemId: String,
    ) : N8nWorkerInput

    /** Translates one snapshotted review preview without running extraction or merge stages. */
    data class ReviewTranslation(
        override val webhookPath: String,
        val reviewKey: String,
    ) : N8nWorkerInput

    companion object {
        /** Parses WorkManager Data keys into a typed worker input. */
        fun from(input: Data): N8nWorkerInput? {
            val apiType = input.getString("api_type") ?: return null
            val webhookPath = input.getString("webhook_path") ?: return null

            return when (apiType) {
                N8N_EXTRACTION_PIPELINE -> ExtractionPipeline(
                    webhookPath = webhookPath,
                    notiKey = input.getString("noti_key") ?: return null,
                    forced = input.getBoolean("forced", false),
                )

                N8N_REFLECTION_PIPELINE -> ReflectionPipeline(
                    webhookPath = webhookPath,
                )

                N8N_PREFERENCE_QUICK_SYNC -> PreferenceQuickSync(
                    webhookPath = webhookPath,
                    payloadJson = input.getString("payload_json") ?: return null,
                )

                N8N_REGENERATE_ONE -> RegenerateOne(
                    webhookPath = webhookPath,
                    savedItemId = input.getString("saved_item_id")
                        ?: input.getString("reminder_id")
                        ?: return null,
                )

                N8N_REVIEW_TRANSLATION -> ReviewTranslation(
                    webhookPath = webhookPath,
                    reviewKey = input.getString("review_key") ?: return null,
                )

                else -> null
            }
        }
    }
}
