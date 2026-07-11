package org.muilab.notigpt.data.remote.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.tasks.await
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.firestore.FirestoreRestoreRepository
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Google Sign-In via Credential Manager backed by Firebase Auth.
 *
 * Login is mandatory: the Firebase UID becomes [SharedPreferencesManager.userId], which keys both
 * the Firestore paths and the n8n payloads. Signing in with a DIFFERENT Google account than the
 * last one wipes the local LLM-owned data (saved items, links, change log, journal, preferences,
 * user contexts — notification history stays) and restores from the new account's cloud copy;
 * callers must confirm with the user before invoking [completeAccountSwitch].
 */
object GoogleAuthManager {

    private const val TAG = "GoogleAuth"
    private const val PREFS = "local"
    private const val KEY_LAST_UID = "last_signed_in_uid"

    sealed class SignInOutcome {
        /** Signed in and local state is consistent with this account. */
        data class SignedIn(val user: FirebaseUser) : SignInOutcome()

        /** Signed in as a different account than last time; confirm before wiping local data. */
        data class AccountSwitchRequired(val user: FirebaseUser, val previousUid: String) : SignInOutcome()

        data class Failed(val error: Throwable) : SignInOutcome()
    }

    fun currentUser(): FirebaseUser? = FirebaseAuth.getInstance().currentUser

    fun isSignedIn(): Boolean = currentUser() != null

    /**
     * Runs the Credential Manager Google Sign-In flow and signs into Firebase.
     *
     * [activityContext] must be an Activity context (Credential Manager shows UI).
     */
    suspend fun signIn(activityContext: Context): SignInOutcome {
        return try {
            val webClientId = resolveWebClientId(activityContext)
            if (webClientId.isBlank()) {
                return SignInOutcome.Failed(IllegalStateException("Missing default_web_client_id — check google-services.json / Firebase console OAuth client"))
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken

            val authResult = FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            val user = authResult.user
                ?: return SignInOutcome.Failed(IllegalStateException("Firebase returned no user"))

            val previousUid = SharedPreferencesManager.get(PREFS, KEY_LAST_UID, "")
            if (previousUid.isNotBlank() && previousUid != user.uid) {
                SignInOutcome.AccountSwitchRequired(user, previousUid)
            } else {
                finalizeSignIn(activityContext, user)
                SignInOutcome.SignedIn(user)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Sign-in failed", t)
            SignInOutcome.Failed(t)
        }
    }

    /** User confirmed the account switch: wipe local LLM-owned data, then restore from the new account. */
    suspend fun completeAccountSwitch(context: Context, user: FirebaseUser) {
        wipeLocalAccountData(context)
        finalizeSignIn(context, user)
    }

    /** User declined the account switch: sign the new account back out, keeping local data intact. */
    fun abortAccountSwitch() {
        FirebaseAuth.getInstance().signOut()
    }

    private suspend fun finalizeSignIn(context: Context, user: FirebaseUser) {
        SharedPreferencesManager.userId = user.uid
        SharedPreferencesManager.put(PREFS, KEY_LAST_UID, user.uid)
        try {
            FirebaseCrashlytics.getInstance().setUserId(user.uid)
        } catch (_: Exception) {
        }
        try {
            FirestoreRestoreRepository(context.applicationContext).restoreIfLocalEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "Restore after sign-in failed (continuing)", t)
        }
    }

    private suspend fun wipeLocalAccountData(context: Context) {
        val db = AppDatabase.getInstance(context.applicationContext)
        // saved_item cascades noti_saved_item_link + saved_item_change_log via FK.
        db.reminderListDao().deleteAllForAccountSwitch()
        db.subTaskDao().deleteAllForAccountSwitch()
        db.extractionJournalDao().deleteAllEntries()
        db.extractionJournalDao().deleteAllSummaries()
        db.notiLlmStateDao().deleteAll()
        db.extractionPreferenceDao().deleteAll()
        db.userContextDao().deleteAll()
    }

    /**
     * The OAuth web client id normally generated into resources by the google-services plugin.
     * Resolved dynamically so a placeholder google-services.json (no oauth_client entry) still compiles.
     */
    private fun resolveWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }
}
