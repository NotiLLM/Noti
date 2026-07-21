package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import org.muilab.notigpt.data.remote.n8n.enqueueReflectionPipeline
import org.muilab.notigpt.util.SharedPreferencesManager

/** Scheduling policy for daily and item-volume-triggered cross-thread reflection. */
object ReflectionTrigger {
    const val REFLECTION_MAX_INTERVAL_MS = 24L * 60 * 60 * 1000
    const val REFLECTION_DIRTY_ITEM_THRESHOLD = 5
    const val REFLECTION_MIN_INTERVAL_MS = 1L * 60 * 60 * 1000

    fun noteDirtyItems(context: Context, itemIds: Collection<String>, now: Long = System.currentTimeMillis()) {
        SharedPreferencesManager.addReflectionDirtyItemIds(itemIds)
        maybeEnqueue(context, now)
    }

    fun maybeEnqueue(context: Context, now: Long = System.currentTimeMillis()) {
        val dirtyCount = SharedPreferencesManager.reflectionDirtyItemCount()
        val lastSuccess = SharedPreferencesManager.lastReflectionSuccessTime
        val lastAttempt = SharedPreferencesManager.lastReflectionAttemptTime
        if (!shouldEnqueue(now, lastSuccess, lastAttempt, dirtyCount)) return

        val dailyDue = now - lastSuccess >= REFLECTION_MAX_INTERVAL_MS
        val volumeDue = dirtyCount >= REFLECTION_DIRTY_ITEM_THRESHOLD

        Log.i(TAG, "Enqueueing D2: dailyDue=$dailyDue volumeDue=$volumeDue dirtyItems=$dirtyCount")
        enqueueReflectionPipeline(context)
    }

    fun markSuccess(dirtyVersionAtStart: Long, now: Long = System.currentTimeMillis()) {
        SharedPreferencesManager.lastReflectionSuccessTime = now
        SharedPreferencesManager.clearReflectionDirtyItemIdsIfVersion(dirtyVersionAtStart)
    }

    internal fun shouldEnqueue(now: Long, lastSuccess: Long, lastAttempt: Long, dirtyCount: Int): Boolean {
        if (now - lastAttempt < REFLECTION_MIN_INTERVAL_MS) return false
        return now - lastSuccess >= REFLECTION_MAX_INTERVAL_MS || dirtyCount >= REFLECTION_DIRTY_ITEM_THRESHOLD
    }

    private const val TAG = "ReflectionTrigger"
}
