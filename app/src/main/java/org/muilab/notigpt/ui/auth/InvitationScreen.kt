package org.muilab.notigpt.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.data.remote.firestore.EntitlementCheckResult
import org.muilab.notigpt.data.remote.firestore.EntitlementRepository
import org.muilab.notigpt.data.remote.firestore.RedeemResult

/**
 * Access gate shown between [SignInScreen] and `AppScaffold` until the signed-in account has a
 * granted `entitlements/{uid}` Firestore document (see plans/3-invitation-and-llm-usage.md).
 *
 * There is no server to ask "is this code valid" — [EntitlementRepository] validates directly
 * against Firestore Security Rules, so the three outcomes below (checking, needs a code, granted)
 * are the complete state machine; there is no separate "server rejected the pipeline call" state
 * because n8n itself never checks access (see the plan's accepted-risk decision).
 */
@Composable
fun InvitationScreen(
    onAccessGranted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = remember { EntitlementRepository() }

    var isChecking by remember { mutableStateOf(true) }
    var code by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val invalidOrUsedMessage = stringResource(R.string.invitation_code_invalid_or_used)
    val networkErrorMessage = stringResource(R.string.invitation_network_error)

    LaunchedEffect(Unit) {
        when (repository.checkAccess()) {
            EntitlementCheckResult.Granted -> onAccessGranted()
            EntitlementCheckResult.NotGranted -> isChecking = false
            // An unreachable check degrades to the code-entry screen rather than blocking
            // indefinitely; submitting a code will surface the same network error state below
            // if the network is still unavailable.
            is EntitlementCheckResult.CheckFailed -> isChecking = false
        }
    }

    if (isChecking) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.invitation_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val submit: () -> Unit = {
        if (!isSubmitting && code.isNotBlank()) {
            isSubmitting = true
            errorText = null
            scope.launch {
                when (repository.redeem(code)) {
                    RedeemResult.Success -> onAccessGranted()
                    RedeemResult.CodeAlreadyUsedOrInvalid -> errorText = invalidOrUsedMessage
                    is RedeemResult.Failed -> errorText = networkErrorMessage
                }
                isSubmitting = false
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
            text = stringResource(R.string.invitation_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.invitation_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.invitation_code_label)) },
            enabled = !isSubmitting,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        if (isSubmitting) {
            CircularProgressIndicator()
        } else {
            Button(onClick = submit) {
                Text(stringResource(R.string.invitation_submit))
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
}
