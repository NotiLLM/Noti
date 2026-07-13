package org.muilab.notigpt.ui.common.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel

/**
 * Shared Compose search field used by drawer/reminder screens.
 *
 * A borderless filled pill (no indicator lines) that auto-focuses and raises the keyboard when it
 * appears. The trailing button clears the query, or closes search when the query is already empty.
 * Keep this component presentation-only — query interpretation stays in the caller/ViewModel.
 */
@Composable
fun SearchBar(
    drawerViewModel: DrawerViewModel,
    onSearchToggled: (Boolean) -> Unit,
    queryStringOverride: String? = null,
    onQueryStringChange: ((String) -> Unit)? = null,
) {
    val drawerQueryString by drawerViewModel.queryString.collectAsState()
    val queryString = queryStringOverride ?: drawerQueryString
    val updateQueryString = onQueryStringChange ?: drawerViewModel::updateQueryString

    val keyboardController = LocalSoftwareKeyboardController.current
    val currentFocus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Raise focus + keyboard as the field appears.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
        TextField(
            value = queryString,
            onValueChange = { updateQueryString(it) },
            placeholder = {
                Text(
                    text = stringResource(R.string.ui_search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .minimumInteractiveComponentSize(),
            shape = MaterialTheme.shapes.large,
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (queryString.isEmpty()) onSearchToggled(false) else updateQueryString("")
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.a11y_close_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                currentFocus.clearFocus()
            }),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}
