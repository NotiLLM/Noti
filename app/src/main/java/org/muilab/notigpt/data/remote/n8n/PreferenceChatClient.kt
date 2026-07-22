package org.muilab.notigpt.data.remote.n8n

import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChatRequestDto

/** Mode-free shared assistant chat client. */
object PreferenceChatClient {
    suspend fun interact(request: PersonalizationChatRequestDto): PersonalizationClientResult =
        PersonalizationAssistantTransport.post(
            webhookPath = BuildConfig.N8N_PREFERENCE_CHAT_INTERACT_PATH,
            request = request,
            confirmedState = request.confirmedState,
            logTag = "PrefChatClient",
        )

    /** Compile-safe bridge for the legacy screen; removed when plan 04-05 adopts typed turns. */
    suspend fun interact(request: N8nChatInteractRequestDto): N8nChatInteractResponseDto? =
        LegacyPersonalizationUiBridge.chat(request)
}
