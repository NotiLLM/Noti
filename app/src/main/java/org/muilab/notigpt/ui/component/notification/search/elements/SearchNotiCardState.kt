package org.muilab.notigpt.ui.component.notification.search.elements

import androidx.compose.runtime.Immutable

@Immutable
internal data class SearchNotiCardState(
    val hasOlderContextAtTop: Boolean,
    val hasNewerContextAtBottom: Boolean,
)
