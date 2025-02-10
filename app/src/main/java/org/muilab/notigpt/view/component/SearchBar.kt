package org.muilab.notigpt.view.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.viewModel.DrawerViewModel


@Composable
fun SearchBar(drawerViewModel: DrawerViewModel) {

    val queryString = drawerViewModel.queryString.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val currentFocus = LocalFocusManager.current

    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 15.dp)) {
        OutlinedTextField(
            value = queryString.value,
            onValueChange = {
                drawerViewModel.updateQueryString(it)
            },
            label = { Text("Search") },
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (queryString.value.isNotEmpty() ) {
                    IconButton(onClick = { drawerViewModel.updateQueryString("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                currentFocus.clearFocus()
            })
        )
    }
}