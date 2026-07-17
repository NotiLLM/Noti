package org.muilab.notigpt.util

/**
 * Central constants for background workflow types and notification timing values.
 *
 * Keep constants here only when they are shared across layers. Feature-local constants should stay near their
 * owning component or repository to avoid turning this class into a catch-all.
 */
class Constants {
    companion object {
        const val NOTI_REMOVE_DELAY = 20 * 1000L

        // n8n task API types. The per-notiKey extraction pipeline runs its stages (A→B→C→D1→E1)
        // sequentially inside one worker job, so it needs a single api_type; the reflection pass
        // (D2→E2) is a separate scheduled job.
        const val N8N_EXTRACTION_PIPELINE = "extraction_pipeline"
        const val N8N_REFLECTION_PIPELINE = "reflection_pipeline"
        const val N8N_REGENERATE_ONE = "regenerate_one"

        // n8n preference API types
        const val N8N_PREFERENCE_QUICK_SYNC = "preference_quick_sync"
    }
}
