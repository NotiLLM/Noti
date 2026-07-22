package org.muilab.notigpt.data.remote.n8n

import android.util.Log
import com.google.gson.Gson
import java.util.concurrent.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationConfirmedStateDto
import org.muilab.notigpt.data.remote.n8n.dto.allSnapshots
import org.muilab.notigpt.domain.personalization.PersonalizationAssistantTurn
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore

sealed interface PersonalizationClientResult {
    data class Success(val turn: PersonalizationAssistantTurn) : PersonalizationClientResult

    data class Failure(
        val kind: Kind,
        val validationFailure: PersonalizationValidationFailure? = null,
    ) : PersonalizationClientResult

    enum class Kind {
        NETWORK,
        HTTP,
        EMPTY_RESPONSE,
        INVALID_RESPONSE,
    }
}

/** Shared transport/decode/validate boundary for the three personalization webhooks. */
internal object PersonalizationAssistantTransport {
    private val gson = Gson()

    suspend fun post(
        webhookPath: String,
        request: Any,
        confirmedState: PersonalizationConfirmedStateDto,
        evidenceIds: Set<String>? = null,
        logTag: String,
    ): PersonalizationClientResult {
        val requestJson = gson.toJson(request)
        Log.d(logTag, "Request bytes=${requestJson.length}")
        val response = try {
            N8nAPIClient.n8nAPIService.postToWebhook(
                webhookPath,
                requestJson.toRequestBody(JSON_MEDIA_TYPE),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(logTag, "Network error", error)
            return PersonalizationClientResult.Failure(PersonalizationClientResult.Kind.NETWORK)
        }
        if (!response.isSuccessful) {
            Log.w(logTag, "HTTP ${response.code()}")
            return PersonalizationClientResult.Failure(PersonalizationClientResult.Kind.HTTP)
        }
        val body = response.body()?.string()
        if (body.isNullOrBlank()) {
            Log.w(logTag, "Empty response body")
            return PersonalizationClientResult.Failure(PersonalizationClientResult.Kind.EMPTY_RESPONSE)
        }
        Log.d(logTag, "Response bytes=${body.length}")
        val validation = PersonalizationAssistantResponseDecoder.decodeAndValidate(
            rawJson = body,
            targetSnapshots = confirmedState.toDomainSnapshots(),
            evidenceIds = evidenceIds,
        )
        return when (validation) {
            is PersonalizationValidationResult.Valid -> PersonalizationClientResult.Success(validation.turn)
            is PersonalizationValidationResult.Invalid -> {
                Log.w(logTag, "Invalid response code=${validation.failure.code}")
                PersonalizationClientResult.Failure(
                    kind = PersonalizationClientResult.Kind.INVALID_RESPONSE,
                    validationFailure = validation.failure,
                )
            }
        }
    }

    private fun PersonalizationConfirmedStateDto.toDomainSnapshots(): List<PersonalizationRecordSnapshot> =
        allSnapshots().map { snapshot ->
            PersonalizationRecordSnapshot(
                targetStore = PersonalizationStore.valueOf(snapshot.targetStore),
                id = snapshot.id,
                statement = snapshot.statement,
                createdAt = snapshot.createdAt,
                updatedAt = snapshot.updatedAt,
            )
        }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
}
