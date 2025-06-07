package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.muilab.notigpt.model.notifications.NotiCategory

@Dao
interface NotiCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(notiCategory: NotiCategory)

    @Query("DELETE FROM noti_category WHERE categoryName = :category")
    fun deleteCategory(category: String)

    @Query("SELECT * FROM noti_category")
    fun getAll(): List<NotiCategory>

    @Query("SELECT COUNT(*) FROM noti_category")
    fun getCount(): Int
}