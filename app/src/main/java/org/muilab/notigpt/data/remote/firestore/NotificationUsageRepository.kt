package org.muilab.notigpt.data.remote.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * Best-effort 6-hour rollup of received-notification count/character-volume, keyed by account
 * (see plans/3-invitation-and-llm-usage.md). This is a separate concern from per-LLM-call token
 * usage ([org.muilab.notigpt.data.remote.n8n.UsageLogger]): it exists to observe how much raw
 * notification volume a user generates, independent of how many pipeline stages later process it.
 *
 * Callers must count each notification exactly once, at first capture — see
 * NotiActionsRepository.insertNotiRecord's existing `existed` check, which this pairs with so a
 * notification is never double-counted across pipeline stages A/B/C.
 */
class NotificationUsageRepository(
    firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) {
    // Deferred: bucketDocId/bucketStart are pure and unit-tested without any Firebase app
    // initialized, so FirebaseFirestore.getInstance() must not run until actually needed.
    private val firestore: FirebaseFirestore by lazy(firestoreProvider)

    private val tag = "NotificationUsage"

    private fun userId(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    /** Buckets are 6 hours wide, aligned to 00/06/12/18 local time. */
    internal fun bucketDocId(uid: String, nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val bucketHour = (local.hour / 6) * 6
        return "${uid}_${local.toLocalDate()}_${bucketHour.toString().padStart(2, '0')}"
    }

    internal fun bucketStart(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Date {
        val local = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val bucketHour = (local.hour / 6) * 6
        val start = local.toLocalDate().atStartOfDay(zoneId).plusHours(bucketHour.toLong())
        return Date.from(start.toInstant())
    }

    suspend fun recordCapturedNotification(characterCount: Int) = withContext(Dispatchers.IO) {
        val uid = userId()
        if (uid.isBlank()) return@withContext
        try {
            val now = System.currentTimeMillis()
            firestore.collection(FirestorePaths.COLLECTION_NOTIFICATION_USAGE)
                .document(bucketDocId(uid, now))
                .set(
                    mapOf(
                        "uid" to uid,
                        "bucketStart" to bucketStart(now),
                        "notificationCount" to FieldValue.increment(1),
                        "characterCount" to FieldValue.increment(characterCount.toLong()),
                    ),
                    SetOptions.merge(),
                )
                .await()
        } catch (t: Throwable) {
            Log.w(tag, "recordCapturedNotification failed uid=$uid", t)
        }
    }
}
