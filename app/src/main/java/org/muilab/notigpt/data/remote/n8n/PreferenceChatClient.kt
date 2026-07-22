package org.muilab.notigpt.data.remote.n8n

import org.muilab.notigpt.BuildConfig
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

}
