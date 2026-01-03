package org.muilab.notigpt.ui.component.notification.card.searchcard.elements

import androidx.compose.runtime.snapshots.SnapshotStateMap

internal suspend fun checkGapCached(
    cache: SnapshotStateMap<String, Boolean>,
    key: SearchGapKey,
    compute: suspend () -> Boolean,
): Boolean {
    val cached = cache[key.toString()]
    if (cached != null) return cached
    val computed = compute()
    cache[key.toString()] = computed
    return computed
}

