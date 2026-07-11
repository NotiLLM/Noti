package org.muilab.notigpt.data.remote.n8n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Process-wide extraction health state: consecutive failures and the last failure kind.
 *
 * Workers write, the UI reads (banner "extraction pending — server unreachable" plus a snackbar
 * for user-triggered failures). Persisted through SharedPreferencesManager so the state survives
 * process death; mirrored in a StateFlow so Compose can observe it live.
 */
object ExtractionStatusStore {

    object FailureKind {
        const val None = ""
        const val Network = "network"
        const val Server = "server"
    }

    data class Status(
        val consecutiveFailures: Int = 0,
        val lastFailureKind: String = FailureKind.None,
        val lastFailureAt: Long = 0L,
        /** Bumped on each failed user-triggered extraction; the UI shows a snackbar per bump. */
        val userTriggeredFailureTick: Long = 0L,
    )

    private const val PREFS = "local"
    private const val KEY_FAILURES = "extraction_consecutive_failures"
    private const val KEY_KIND = "extraction_last_failure_kind"
    private const val KEY_AT = "extraction_last_failure_at"

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    /** Loads persisted failure state; call once after SharedPreferencesManager.init. */
    fun restore() {
        _status.value = Status(
            consecutiveFailures = SharedPreferencesManager.get(PREFS, KEY_FAILURES, 0),
            lastFailureKind = SharedPreferencesManager.get(PREFS, KEY_KIND, FailureKind.None),
            lastFailureAt = SharedPreferencesManager.get(PREFS, KEY_AT, 0L),
        )
    }

    fun recordFailure(kind: String, userTriggered: Boolean = false) {
        val now = System.currentTimeMillis()
        val next = _status.value.copy(
            consecutiveFailures = _status.value.consecutiveFailures + 1,
            lastFailureKind = kind,
            lastFailureAt = now,
            userTriggeredFailureTick = if (userTriggered) now else _status.value.userTriggeredFailureTick,
        )
        _status.value = next
        persist(next)
    }

    fun recordSuccess() {
        if (_status.value.consecutiveFailures == 0) return
        val next = _status.value.copy(consecutiveFailures = 0, lastFailureKind = FailureKind.None)
        _status.value = next
        persist(next)
    }

    private fun persist(s: Status) {
        try {
            SharedPreferencesManager.put(PREFS, KEY_FAILURES, s.consecutiveFailures)
            SharedPreferencesManager.put(PREFS, KEY_KIND, s.lastFailureKind)
            SharedPreferencesManager.put(PREFS, KEY_AT, s.lastFailureAt)
        } catch (_: Exception) {
            // Status is advisory UI state; never fail a worker over it.
        }
    }
}
