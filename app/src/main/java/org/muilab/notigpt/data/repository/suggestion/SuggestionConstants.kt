package org.muilab.notigpt.data.repository.suggestion

/** Product limits for the local Suggested snapshot and its G/H evaluation pass. */
object SuggestionConstants {
    const val REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1_000L
    const val G_SKIP_AT_OR_BELOW_ITEM_COUNT = 25
    const val G_MAX_CANDIDATES = 25
    const val H_MAX_SUGGESTIONS = 10
}
