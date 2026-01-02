package org.muilab.notigpt.ui.component.notification.search.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState

@Composable
internal fun rememberHasContext(
    key1: Any?,
    key2: Any?,
    includeHistory: Boolean,
    compute: suspend () -> Boolean,
    state: MutableState<Boolean>,
) {
    LaunchedEffect(key1, key2, includeHistory) {
        state.value = compute()
    }
}

