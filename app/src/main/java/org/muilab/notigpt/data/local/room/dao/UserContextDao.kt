package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.UserContext

/**
 * Local access layer for durable user-context statements used by extraction and preference flows.
 *
 * These rows describe the user, not individual notifications. Keep discovery and ranking logic outside
 * the DAO so storage stays independent from the model that inferred the statement.
 */
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

    @Query("DELETE FROM user_contexts")
    suspend fun deleteAll()
}

