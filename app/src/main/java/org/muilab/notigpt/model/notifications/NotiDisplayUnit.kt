package org.muilab.notigpt.model.notifications

/**
 * In-memory display aggregate for one active drawer notification and its recent records.
 *
 * This is not a Room entity. Keep it as the UI-facing join between NotiUnit and NotiRecord so DAOs can
 * stay normalized while cards receive the context they need.
 */
class NotiDisplayUnit (
    val notiUnit: NotiUnit,
    val notiRecords: List<NotiRecord>,
) {
    val notiKey: String
        get() = notiUnit.notiKey

    val title: String
        get() = notiRecords.lastOrNull()?.getDisplayedTitle(notiUnit.isPeople) ?: ""

    val sortScore: Double
        get() = notiUnit.sortScore

    val lastUpdateTime: Long
        get() = notiRecords.lastOrNull()?.time ?: 0L

    // NOTE: UI should format this with Context to ensure correct localization.
}