package org.muilab.notigpt.data.remote.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.muilab.notigpt.util.SharedPreferencesManager

/** Result of reading whether the signed-in account already holds a granted entitlement. */
sealed interface EntitlementCheckResult {
    data object Granted : EntitlementCheckResult
    data object NotGranted : EntitlementCheckResult
    data class CheckFailed(val cause: Throwable) : EntitlementCheckResult
}

/** Result of attempting to redeem a one-time invitation code for the signed-in account. */
sealed interface RedeemResult {
    data object Success : RedeemResult
    data object CodeAlreadyUsedOrInvalid : RedeemResult
    data class Failed(val cause: Throwable) : RedeemResult
}

/**
 * Owns invitation-code redemption and access-entitlement checks against Firestore.
 *
 * There is no backend for this: `entitlements/{uid}` and `invitationCodes/{code}` are validated
 * entirely by Firestore Security Rules (see `firestore/firestore.rules` at the repo root and
 * plans/3-invitation-and-llm-usage.md). Redemption is a single client transaction so a code can
 * never be consumed twice, even by two devices racing on the same code.
 */
class EntitlementRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private val tag = "EntitlementRepository"

    private fun userId(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private fun entitlementDoc(uid: String) = firestore
        .collection(FirestorePaths.COLLECTION_ENTITLEMENTS)
        .document(uid)

    private fun invitationCodeDoc(code: String) = firestore
        .collection(FirestorePaths.COLLECTION_INVITATION_CODES)
        .document(code)

    /**
     * Reads `entitlements/{uid}`; caches a granted result locally so callers don't need to hit
     * Firestore again for this account (see [SharedPreferencesManager.hasAccess]).
     */
    suspend fun checkAccess(): EntitlementCheckResult = withContext(Dispatchers.IO) {
        val uid = userId()
        if (uid.isBlank()) return@withContext EntitlementCheckResult.NotGranted
        try {
            val snapshot = entitlementDoc(uid).get().await()
            if (snapshot.exists() && snapshot.getBoolean("hasAccess") == true) {
                SharedPreferencesManager.hasAccess = true
                EntitlementCheckResult.Granted
            } else {
                EntitlementCheckResult.NotGranted
            }
        } catch (t: Throwable) {
            Log.w(tag, "checkAccess failed uid=$uid", t)
            EntitlementCheckResult.CheckFailed(t)
        }
    }

    /**
     * Atomically redeems [code] for the signed-in account: flips `invitationCodes/{code}` from
     * unredeemed to redeemed-by-this-uid and creates `entitlements/{uid}` in the same transaction.
     * Firestore re-validates the security rule against the current server state at commit time,
     * so a code that another device redeemed first fails here even if this device's own read was
     * momentarily stale.
     */
    suspend fun redeem(code: String): RedeemResult = withContext(Dispatchers.IO) {
        val uid = userId()
        val trimmed = code.trim()
        if (uid.isBlank() || trimmed.isBlank()) return@withContext RedeemResult.CodeAlreadyUsedOrInvalid
        try {
            firestore.runTransaction { transaction ->
                val codeSnapshot = transaction.get(invitationCodeDoc(trimmed))
                if (!codeSnapshot.exists() || codeSnapshot.getBoolean("redeemed") == true) {
                    throw CodeUnavailableException()
                }
                transaction.update(
                    invitationCodeDoc(trimmed),
                    mapOf(
                        "redeemed" to true,
                        "redeemedBy" to uid,
                        "redeemedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                transaction.set(
                    entitlementDoc(uid),
                    mapOf(
                        "hasAccess" to true,
                        "invitationCode" to trimmed,
                        "redeemedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }.await()
            SharedPreferencesManager.hasAccess = true
            RedeemResult.Success
        } catch (_: CodeUnavailableException) {
            RedeemResult.CodeAlreadyUsedOrInvalid
        } catch (t: Throwable) {
            Log.w(tag, "redeem failed uid=$uid", t)
            RedeemResult.Failed(t)
        }
    }

    private class CodeUnavailableException : Exception()
}
