package org.muilab.notigpt.ui.component.notification.search.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

@Composable
internal fun rememberGapCache(notiKey: String, includeHistory: Boolean): SnapshotStateMap<String, Boolean> {
    return remember(notiKey, includeHistory) { mutableStateMapOf() }
}

