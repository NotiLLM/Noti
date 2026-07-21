package org.muilab.notigpt.domain.notification

import org.muilab.notigpt.model.notifications.NotiDisplayUnit

/** Active drawer threads that have at least one active record to render. */
fun List<NotiDisplayUnit>.withActiveRecords(): List<NotiDisplayUnit> =
    filter { it.notiRecords.isNotEmpty() }
