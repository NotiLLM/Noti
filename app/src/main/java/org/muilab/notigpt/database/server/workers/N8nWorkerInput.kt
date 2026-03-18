package org.muilab.notigpt.database.server.workers

import androidx.work.Data
import org.muilab.notigpt.util.Constants.Companion.DIFY_POST_NOTIFICATION_ACTION
import org.muilab.notigpt.util.Constants.Companion.DIFY_UPDATE_NOTIFICATION
import org.muilab.notigpt.util.Constants.Companion.N8N_TASK_EXTRACTION
import org.muilab.notigpt.util.Constants.Companion.N8N_TASK_SCAN
import org.muilab.notigpt.util.Constants.Companion.N8N_PREFERENCE_QUICK_SYNC
import org.muilab.notigpt.util.Constants.Companion.N8N_REGENERATE_ONE
import org.muilab.notigpt.util.Constants.Companion.N8N_REGENERATE_ALL
import org.muilab.notigpt.util.Constants.Companion.N8N_RERANK

/**
 * Typed view of WorkManager input data for [N8nAPIWorker].
 *
 * This keeps parsing/validation separate from the network/database side effects.
 */
sealed interface N8nWorkerInput {
    val webhookPath: String

    data class UpdateNotification(
        override val webhookPath: String,
        val notiKey: String,
    ) : N8nWorkerInput

    data class ReminderScan(
        override val webhookPath: String,
        val notiKey: String,
    ) : N8nWorkerInput

    data class ReminderExtraction(
        override val webhookPath: String,
        val notiKeysJson: String,
    ) : N8nWorkerInput

    data class PostNotificationAction(
        override val webhookPath: String,
        val notiKey: String,
        val actionType: String,
        val actionTime: Long,
    ) : N8nWorkerInput

    /** Fires a quick-sync of preference selections to the backend. */
    data class PreferenceQuickSync(
        override val webhookPath: String,
        val payloadJson: String,
    ) : N8nWorkerInput

    /** Regenerate a single reminder. */
    data class RegenerateOne(
        override val webhookPath: String,
        val reminderId: String,
    ) : N8nWorkerInput

    /** Regenerate all visible reminders. */
    data class RegenerateAll(
        override val webhookPath: String,
    ) : N8nWorkerInput

    /** Rerank a single reminder (triggered by user feedback, etc.). */
    data class Rerank(
        override val webhookPath: String,
        val reminderId: String,
        val trigger: String,
    ) : N8nWorkerInput

    companion object {
        /** Parses the legacy wire format used throughout the app. */
        fun from(input: Data): N8nWorkerInput? {
            val apiType = input.getString("api_type") ?: return null
            val webhookPath = input.getString("webhook_path") ?: return null

            return when (apiType) {
                DIFY_UPDATE_NOTIFICATION -> UpdateNotification(
                    webhookPath = webhookPath,
                    notiKey = input.getString("noti_key") ?: "",
                )

                N8N_TASK_SCAN -> ReminderScan(
                    webhookPath = webhookPath,
                    notiKey = input.getString("noti_key") ?: return null,
                )

                N8N_TASK_EXTRACTION -> ReminderExtraction(
                    webhookPath = webhookPath,
                    notiKeysJson = input.getString("noti_keys_json") ?: "[]",
                )

                DIFY_POST_NOTIFICATION_ACTION -> PostNotificationAction(
                    webhookPath = webhookPath,
                    notiKey = input.getString("noti_key") ?: return null,
                    actionType = input.getString("action_type") ?: return null,
                    actionTime = input.getLong("action_time", -1L),
                )

                N8N_PREFERENCE_QUICK_SYNC -> PreferenceQuickSync(
                    webhookPath = webhookPath,
                    payloadJson = input.getString("payload_json") ?: return null,
                )

                N8N_REGENERATE_ONE -> RegenerateOne(
                    webhookPath = webhookPath,
                    reminderId = input.getString("reminder_id") ?: return null,
                )

                N8N_REGENERATE_ALL -> RegenerateAll(
                    webhookPath = webhookPath,
                )

                N8N_RERANK -> Rerank(
                    webhookPath = webhookPath,
                    reminderId = input.getString("reminder_id") ?: return null,
                    trigger = input.getString("trigger") ?: return null,
                )

                else -> null
            }
        }
    }
}
