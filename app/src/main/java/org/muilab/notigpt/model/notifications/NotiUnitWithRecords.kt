package org.muilab.notigpt.model.notifications

import androidx.room.Embedded
import androidx.room.Relation

data class NotiUnitWithRecords(
    @Embedded val notiUnit: NotiUnit,
    @Relation(
        parentColumn = "notiKey",
        entityColumn = "notiKey",
        entity = VisibleNotiRecord::class
    )
    val notiRecords: List<VisibleNotiRecord>
)