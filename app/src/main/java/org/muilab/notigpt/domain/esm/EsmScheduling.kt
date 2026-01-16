package org.muilab.notigpt.domain.esm

import android.content.Context
import org.muilab.notigpt.database.server.esm.enqueueEsmDelivery
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Tracks ESM deliveries that should be enqueued once the user opens the app.
 */
object EsmScheduling {

    private const val KEY_LOCAL_PREFS = "local"
    private const val KEY_PENDING_INSTANCE_IDS_JSON = "esmPendingEnqueueInstanceIdsJson"

    fun addPendingEnqueue(context: Context, instanceId: String) {
        // SharedPreferencesManager is already init'd in MainActivity, but workers can run before Activity.
        try { SharedPreferencesManager.init(context.applicationContext) } catch (_: Exception) {}

        val current = getPendingEnqueue(context).toMutableSet()
        current.add(instanceId)
        SharedPreferencesManager.put(KEY_LOCAL_PREFS, KEY_PENDING_INSTANCE_IDS_JSON, org.json.JSONArray(current.toList()).toString())
    }

    fun getPendingEnqueue(context: Context): List<String> {
        try { SharedPreferencesManager.init(context.applicationContext) } catch (_: Exception) {}

        val raw = SharedPreferencesManager.get(KEY_LOCAL_PREFS, KEY_PENDING_INSTANCE_IDS_JSON, "")
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearPendingEnqueue(context: Context) {
        try { SharedPreferencesManager.init(context.applicationContext) } catch (_: Exception) {}
        SharedPreferencesManager.put(KEY_LOCAL_PREFS, KEY_PENDING_INSTANCE_IDS_JSON, "")
    }

    /** Enqueue all pending instance deliveries and clear the list. */
    fun flushPendingEnqueue(context: Context, delayMs: Long = 0L) {
        val ids = getPendingEnqueue(context)
        if (ids.isEmpty()) return

        ids.forEach { id ->
            enqueueEsmDelivery(context.applicationContext, id, delayMs)
        }
        clearPendingEnqueue(context)
    }
}

