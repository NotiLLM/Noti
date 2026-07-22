package org.muilab.notigpt.data.remote.n8n

import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.dto.N8nQuickSyncRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nQuickSyncResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationQuickSyncRequestDto

/** Explicit-feedback client whose only extra context is the triggering user action. */
object PreferenceQuickSyncClient {
    suspend fun sync(request: PersonalizationQuickSyncRequestDto): PersonalizationClientResult =
        PersonalizationAssistantTransport.post(
            webhookPath = BuildConfig.N8N_PREFERENCE_QUICK_SYNC_PATH,
            request = request,
            confirmedState = request.confirmedState,
            logTag = "PrefQuickSyncClient",
        )

    /** Compile-safe bridge for the legacy screen; it never applies typed proposals. */
    suspend fun sync(request: N8nQuickSyncRequestDto): N8nQuickSyncResponseDto? =
        LegacyPersonalizationUiBridge.quickSync(request)
}
