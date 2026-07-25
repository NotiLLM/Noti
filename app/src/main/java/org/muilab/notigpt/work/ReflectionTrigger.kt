package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import org.muilab.notigpt.data.remote.n8n.enqueueReflectionPipeline
import org.muilab.notigpt.util.SharedPreferencesManager

/** Scheduling policy for change-driven and daily cross-thread reflection. */
object ReflectionTrigger {
    const val REFLECTION_MAX_INTERVAL_MS = 24L * 60 * 60 * 1000
    const val REFLECTION_CHANGE_DEBOUNCE_MS = 3L * 60 * 1000

    internal data class ChangeRequest(
        val delayMs: Long,
        val replaceExisting: Boolean,
    )

    fun noteDirtyItems(context: Context, itemIds: Collection<String>, now: Long = System.currentTimeMillis()) {
        val dirtyIds = itemIds.filter(String::isNotBlank)
        if (dirtyIds.isEmpty()) return
        SharedPreferencesManager.addReflectionDirtyItemIds(dirtyIds)
        val request = changeDrivenRequest()
        Log.i(TAG, "Scheduling D2 after item change: dirtyItems=${dirtyIds.size}")
        enqueueReflectionPipeline(
            context = context,
            initialDelayMs = request.delayMs,
            replaceExisting = request.replaceExisting,
            now = now,
        )
    }

    fun maybeEnqueue(context: Context, now: Long = System.currentTimeMillis()) {
        val lastSuccess = SharedPreferencesManager.lastReflectionSuccessTime
        val lastAttempt = SharedPreferencesManager.lastReflectionAttemptTime
        if (!shouldEnqueue(now, lastSuccess, lastAttempt, SharedPreferencesManager.reflectionDirtyItemCount())) return

        Log.i(TAG, "Enqueueing daily D2 safety net")
        enqueueReflectionPipeline(context = context, now = now)
    }

    fun markSuccess(dirtyVersionAtStart: Long, now: Long = System.currentTimeMillis()) {
        SharedPreferencesManager.lastReflectionSuccessTime = now
        SharedPreferencesManager.clearReflectionDirtyItemIdsIfVersion(dirtyVersionAtStart)
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun shouldEnqueue(now: Long, lastSuccess: Long, lastAttempt: Long, dirtyCount: Int): Boolean =
        now - lastSuccess >= REFLECTION_MAX_INTERVAL_MS

    internal fun changeDrivenRequest(): ChangeRequest = ChangeRequest(
        delayMs = REFLECTION_CHANGE_DEBOUNCE_MS,
        replaceExisting = true,
    )

    private const val TAG = "ReflectionTrigger"
}
