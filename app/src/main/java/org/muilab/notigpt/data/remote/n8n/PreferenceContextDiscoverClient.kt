package org.muilab.notigpt.data.remote.n8n

import org.muilab.notigpt.BuildConfig
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

}
