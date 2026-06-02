package org.muilab.notigpt.data.remote.googletasks

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.tasks.TasksScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform boundary for Google Tasks sign-in and credential access.
 *
 * Keep GoogleSignIn option construction and account/token lookup here. Task sync code should consume this API
 * instead of reaching into Android/Google auth classes directly.
 */
object GoogleTasksAuthManager {

    private const val PREFS_NAME = "google_tasks_auth"
    private const val KEY_SIGNED_IN = "signed_in"

    private val TASKS_SCOPE = Scope(TasksScopes.TASKS)

    private fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(TASKS_SCOPE)
            .build()
    }

    fun getSignInClient(context: Context): GoogleSignInClient {
        return GoogleSignIn.getClient(context, getGoogleSignInOptions())
    }

    fun getSignInIntent(context: Context): Intent {
        return getSignInClient(context).signInIntent
    }

    /**
     * Returns the currently signed-in account with Tasks scope, or null if not signed in.
     */
    fun getAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        // Verify the account has the Tasks scope
        return if (account != null && GoogleSignIn.hasPermissions(account, TASKS_SCOPE)) {
            account
        } else {
            null
        }
    }

    /**
     * Check if user is signed in with Tasks permission.
     */
    fun isSignedIn(context: Context): Boolean {
        return getAccount(context) != null
    }

    /**
     * Handle the sign-in result from the ActivityResultLauncher.
     * Returns the signed-in account or null if sign-in failed.
     */
    suspend fun handleSignInResult(data: Intent?): GoogleSignInAccount? = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sign out from Google.
     */
    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            getSignInClient(context).signOut()
        } catch (_: Exception) {
            // Ignore errors
        }
    }

    /**
     * Revoke access (for testing/debugging).
     */
    suspend fun revokeAccess(context: Context) = withContext(Dispatchers.IO) {
        try {
            getSignInClient(context).revokeAccess()
        } catch (_: Exception) {
            // Ignore errors
        }
    }
}

