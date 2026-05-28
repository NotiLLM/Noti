package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.UserContext

@Dao
interface UserContextDao {

    @Query("SELECT * FROM user_contexts ORDER BY updatedAt DESC")
    fun getAllContextsFlow(): Flow<List<UserContext>>

    @Query("SELECT * FROM user_contexts ORDER BY updatedAt DESC")
    suspend fun getAllContexts(): List<UserContext>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContext(context: UserContext)

    @Query("DELETE FROM user_contexts WHERE id = :id")
    suspend fun deleteContext(id: String)
}

