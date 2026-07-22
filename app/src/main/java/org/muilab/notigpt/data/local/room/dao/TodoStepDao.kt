package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.TodoStep

@Dao
interface TodoStepDao {
    @Upsert
    suspend fun upsert(step: TodoStep)

    @Upsert
    suspend fun upsertAll(steps: List<TodoStep>)

    @Query("SELECT * FROM todo_step WHERE parentSavedItemId = :savedItemId ORDER BY position ASC, todoStepId ASC")
    fun observeBySavedItemId(savedItemId: String): Flow<List<TodoStep>>

    @Query("SELECT * FROM todo_step ORDER BY parentSavedItemId ASC, position ASC, todoStepId ASC")
    fun observeAllVisible(): Flow<List<TodoStep>>

    @Query("SELECT * FROM todo_step WHERE parentSavedItemId = :savedItemId ORDER BY position ASC, todoStepId ASC")
    suspend fun getBySavedItemId(savedItemId: String): List<TodoStep>

    @Query("SELECT * FROM todo_step WHERE todoStepId = :todoStepId")
    suspend fun getById(todoStepId: String): TodoStep?

    @Query("UPDATE todo_step SET isCompleted = :completed WHERE todoStepId = :todoStepId")
    suspend fun setCompleted(todoStepId: String, completed: Boolean)

    @Query("DELETE FROM todo_step WHERE todoStepId = :todoStepId")
    suspend fun hardDeleteById(todoStepId: String)

    @Query("DELETE FROM todo_step WHERE parentSavedItemId = :savedItemId")
    suspend fun hardDeleteByParentId(savedItemId: String)

    @Query("DELETE FROM todo_step WHERE todoStepId IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM todo_step WHERE parentSavedItemId IN (:savedItemIds)")
    suspend fun hardDeleteByParentIds(savedItemIds: List<String>)

    @Query("DELETE FROM todo_step")
    suspend fun deleteAllForAccountSwitch()

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM todo_step WHERE parentSavedItemId = :savedItemId")
    suspend fun nextPosition(savedItemId: String): Int
}
