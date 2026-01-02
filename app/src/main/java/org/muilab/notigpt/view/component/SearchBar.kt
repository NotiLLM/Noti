package org.muilab.notigpt.view.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.viewModel.DrawerViewModel


@Composable
fun SearchBar(
    drawerViewModel: DrawerViewModel,
    onSearchToggled: (Boolean) -> Unit
) {
    val queryString = drawerViewModel.queryString.collectAsState()
    val includeHistory by drawerViewModel.includeHistory.collectAsState() // New State

    val keyboardController = LocalSoftwareKeyboardController.current
    val currentFocus = LocalFocusManager.current

    Column(Modifier.fillMaxWidth()) { // Changed Row to Column to accommodate the checkbox row below or keep row if space permits
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
        ) {
            OutlinedTextField(
                value = queryString.value,
                onValueChange = { drawerViewModel.updateQueryString(it) },
                placeholder = { Text("Search...") },
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
                        Icon(Icons.Default.Close, contentDescription = "Close Search")
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

        // History Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = includeHistory,
                onCheckedChange = { drawerViewModel.toggleIncludeHistory(it) }
            )
            Text(
                text = "Search History (Hidden/Deleted)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}