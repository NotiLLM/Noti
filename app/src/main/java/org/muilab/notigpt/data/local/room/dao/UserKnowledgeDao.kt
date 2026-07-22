package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.UserKnowledge

/** Prepared local access contract for confirmed User Knowledge. */
@Dao
interface UserKnowledgeDao {
    @Query("SELECT * FROM user_knowledge ORDER BY updatedAt DESC, id ASC")
    fun observeAll(): Flow<List<UserKnowledge>>

    @Query("SELECT * FROM user_knowledge ORDER BY updatedAt DESC, id ASC")
    suspend fun getAll(): List<UserKnowledge>

    @Query("SELECT * FROM user_knowledge WHERE id = :id")
    suspend fun getById(id: String): UserKnowledge?

    @Upsert
    suspend fun upsert(knowledge: UserKnowledge)

    @Query("DELETE FROM user_knowledge WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_knowledge")
    suspend fun deleteAll()
}
