package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.notifications.NotiRecord

@Dao
interface NotiRecordDao {
    @Upsert
    fun upsert(notiRecord: NotiRecord)

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey")
    fun getRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey IN ( :notiKeys )")
    fun getRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND isVisible = 1")
    fun getVisibleRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE isVisible = 1 AND notiKey IN ( :notiKeys )")
    fun getVisibleRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    @Query("UPDATE noti_record SET isVisible = 0 WHERE notiKey = :notiKey")
    fun setRecordsInvisibleByKey(notiKey: String)

    @Query("UPDATE noti_record SET isVisible = 0 WHERE notiKey IN ( :notiKeys)")
    fun setRecordsInvisibleByKeys(notiKeys: List<String>)

    @Query("UPDATE noti_record SET isRead = 1 WHERE notiRecordId IN ( :notiRecordIds)")
    fun setRecordsReadByIds(notiRecordIds: List<String>)

    @Query("SELECT * FROM noti_record WHERE isVisible = 1 AND notiKey IN ( :notiKeys )")
    fun getVisibleRecordsFlowByKeys(notiKeys: List<String>): Flow<List<NotiRecord>>
}