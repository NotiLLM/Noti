package org.muilab.notigpt.domain.esm

object EsmStatuses {
    const val PENDING = "PENDING"
    const val AVAILABLE = "AVAILABLE"
    const val ANSWERED = "ANSWERED"
    const val EXPIRED = "EXPIRED"
    const val DISCARDED_SUPERSEDED = "DISCARDED_SUPERSEDED"
}

object EsmSnapshotStatuses {
    const val STAGED = "STAGED"
    const val KEPT = "KEPT"
    const val DISCARDED = "DISCARDED"
}

object EsmTriggerTypes {
    const val A_USER_TRIGGERED_EXTRACTION = "A"
    const val B_ENTERED_EDIT_PAGE = "B"
    const val C_AUTO_GENERATED = "C"
    const val DEBUG = "DEBUG"
}

