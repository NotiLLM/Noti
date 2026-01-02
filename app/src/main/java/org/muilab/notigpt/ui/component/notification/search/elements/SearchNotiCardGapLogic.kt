package org.muilab.notigpt.ui.component.notification.search.elements

import androidx.compose.runtime.Immutable

@Immutable
internal data class SearchGapKey(
    val notiKey: String,
    val start: Long,
    val end: Long,
    val includeHistory: Boolean,
) {
    override fun toString(): String = "$notiKey|$start|$end|$includeHistory"
}

