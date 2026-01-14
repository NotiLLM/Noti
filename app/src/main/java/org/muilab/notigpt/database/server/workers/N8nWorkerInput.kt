package org.muilab.notigpt.database.server.workers

import androidx.work.Data
import org.muilab.notigpt.util.Constants.Companion.DIFY_POST_NOTIFICATION_ACTION
import org.muilab.notigpt.util.Constants.Companion.DIFY_UPDATE_NOTIFICATION
import org.muilab.notigpt.util.Constants.Companion.N8N_TASK_EXTRACTION
import org.muilab.notigpt.util.Constants.Companion.N8N_TASK_SCAN

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

                else -> null
            }
        }
    }
}
