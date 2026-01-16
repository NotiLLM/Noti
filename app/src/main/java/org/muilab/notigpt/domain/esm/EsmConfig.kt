package org.muilab.notigpt.domain.esm

/**
 * Centralized knobs for ESM scheduling/timing.
 *
 * These values are intentionally centralized so you can tweak them quickly for pilots/tests.
 */
object EsmConfig {

    /** Trigger A: delay between reminder creation and ESM becoming deliverable. */
    const val TRIGGER_A_AVAILABLE_DELAY_MS: Long = 60 * 1000L

    /** Trigger B: delay (for completeness/testing; B does not currently schedule delivery). */
    const val TRIGGER_B_AVAILABLE_DELAY_MS: Long = 60 * 1000L

    /** Trigger C: delay (usually immediate). */
    const val TRIGGER_C_AVAILABLE_DELAY_MS: Long = 60 * 1000L

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
