package org.muilab.notigpt.data.remote.n8n

import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nContextDiscoverRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationDiscoveryRequestDto

/** Explicit discovery client constrained to request-local evidence identifiers. */
object PreferenceContextDiscoverClient {
    suspend fun discover(request: PersonalizationDiscoveryRequestDto): PersonalizationClientResult =
        PersonalizationAssistantTransport.post(
            webhookPath = BuildConfig.N8N_CONTEXT_DISCOVER_PATH,
            request = request,
            confirmedState = request.confirmedState,
            evidenceIds = request.evidenceIds,
            logTag = "CtxDiscoverClient",
        )

    /** Compile-safe bridge for the legacy screen; removed when plan 04-05 adopts typed turns. */
    suspend fun discover(request: N8nContextDiscoverRequestDto): N8nChatInteractResponseDto? =
        LegacyPersonalizationUiBridge.discovery(request)
}
