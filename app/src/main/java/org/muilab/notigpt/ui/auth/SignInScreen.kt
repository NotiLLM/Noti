package org.muilab.notigpt.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.data.remote.auth.GoogleAuthManager

/**
 * Mandatory login gate shown instead of the app content until a Google account is signed in.
 *
 * Saved items and preferences live under the account's Firebase UID in Firestore, so the app
 * cannot operate anonymously. Signing in with a different account than last time requires an
 * explicit confirmation and a choice to retain device notification history or start clean.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pendingSwitchUser by remember { mutableStateOf<FirebaseUser?>(null) }
    var keepNotificationHistory by remember { mutableStateOf(true) }
    val signInFailedMessage = stringResource(R.string.sign_in_failed)

    val startSignIn: () -> Unit = {
        if (!isLoading) {
            isLoading = true
            errorText = null
            scope.launch {
                when (val outcome = GoogleAuthManager.signIn(context)) {
                    is GoogleAuthManager.SignInOutcome.SignedIn -> onSignedIn()
                    is GoogleAuthManager.SignInOutcome.AccountSwitchRequired -> {
                        pendingSwitchUser = outcome.user
                    }
                    GoogleAuthManager.SignInOutcome.Cancelled -> Unit
                    is GoogleAuthManager.SignInOutcome.Failed -> {
                        errorText = outcome.error.localizedMessage
                            ?: signInFailedMessage
                    }
                }
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sign_in_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = startSignIn) {
                Text(stringResource(R.string.sign_in_with_google))
            }
        }

        errorText?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }

    pendingSwitchUser?.let { user ->
        AlertDialog(
            onDismissRequest = { /* force an explicit choice */ },
            title = { Text(stringResource(R.string.account_switch_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.account_switch_message, user.email ?: user.uid))
                    AccountSwitchChoice(
                        selected = keepNotificationHistory,
                        onClick = { keepNotificationHistory = true },
                        title = stringResource(R.string.account_switch_keep_history),
                        warning = stringResource(R.string.account_switch_keep_history_warning),
                    )
                    AccountSwitchChoice(
                        selected = !keepNotificationHistory,
                        onClick = { keepNotificationHistory = false },
                        title = stringResource(R.string.account_switch_start_clean),
                        warning = stringResource(R.string.account_switch_start_clean_warning),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSwitchUser = null
                    isLoading = true
                    scope.launch {
                        GoogleAuthManager.completeAccountSwitch(
                            context = context,
                            user = user,
                            keepNotificationHistory = keepNotificationHistory,
                        )
                        isLoading = false
                        onSignedIn()
                    }
                }) { Text(stringResource(R.string.account_switch_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    GoogleAuthManager.abortAccountSwitch()
                    pendingSwitchUser = null
                }) { Text(stringResource(R.string.ui_action_cancel)) }
            },
        )
    }
}

@Composable
internal fun AccountSwitchChoice(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    warning: String,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
