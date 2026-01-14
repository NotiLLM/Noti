package org.muilab.notigpt.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

@Composable
fun SearchBar(
    drawerViewModel: DrawerViewModel,
    onSearchToggled: (Boolean) -> Unit
) {
    val queryString by drawerViewModel.queryString.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val currentFocus = LocalFocusManager.current

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
        ) {
            OutlinedTextField(
                value = queryString,
                onValueChange = { drawerViewModel.updateQueryString(it) },
                placeholder = { Text(stringResource(R.string.ui_search_placeholder)) },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                shape = RoundedCornerShape(percent = 100),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            drawerViewModel.updateQueryString("")
                            onSearchToggled(false)
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.a11y_close_search))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    currentFocus.clearFocus()
                }),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}