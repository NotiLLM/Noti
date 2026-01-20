package org.muilab.notigpt.domain.esm

/**
 * Centralized knobs for ESM scheduling/timing.
 *
 * These values are intentionally centralized so you can tweak them quickly for pilots/tests.
 */
object EsmConfig {

    /** Trigger A: delay between reminder creation and ESM becoming deliverable. */
    const val TRIGGER_A_AVAILABLE_DELAY_MS: Long = 60 * 1000L

    /** Trigger B: base delay (may be overridden by policy). */
    const val TRIGGER_B_AVAILABLE_DELAY_MS: Long = 60 * 1000L

    /** Trigger C: delay (usually immediate). */
    const val TRIGGER_C_AVAILABLE_DELAY_MS: Long = 60 * 1000L

    /**
     * If A/B trigger happened within this window (since it occurred) and it's been long enough since
     * the last ESM, we deliver immediately.
     */
    const val TRIGGER_AB_RECENT_WINDOW_MS: Long = 30 * 60 * 1000L

    /** If the last ESM (answered or shown) is older than this, allow immediate A/B delivery. */
    const val TRIGGER_AB_LAST_ESM_STALE_MS: Long = 60 * 60 * 1000L

    /** If no triggering criteria has been met for at least this long, fire Trigger C. */
    const val TRIGGER_C_NO_TRIGGER_WINDOW_MS: Long = 2 * 60 * 60 * 1000L

    /** Questionnaire answer window after it becomes AVAILABLE. */
    const val QUESTIONNAIRE_EXPIRES_AFTER_MS: Long = 60 * 60 * 1000L

    /**
     * Optional future policy knobs.
     *
     * These are not enforced everywhere yet because:
     * - cooldown requires tracking the *last delivered/answered time* across instances, and
     * - per-day cap requires counting deliveries/answers within an anchored day window.
     *
     * Once we wire them in, changing these values will affect scheduling globally.
     */
    const val UNANSWERED_COOLDOWN_MS: Long = 60 * 1000L
    const val ANSWERED_COOLDOWN_MS: Long = 60 * 1000L
    const val MAX_PER_ANCHORED_DAY: Int = 80

    /** Anchored day boundary for per-day limits (defaults: 04:00). */
    const val ANCHORED_DAY_START_HOUR: Int = 4
    const val ANCHORED_DAY_START_MINUTE: Int = 0
}
