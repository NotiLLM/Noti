package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.TaskUnit

@Dao
interface TaskListDao {

    @Upsert
    suspend fun insert(taskUnit: TaskUnit)

    @Query("SELECT * FROM task_list ORDER BY estimatedCompletionTime ASC, deadlineTimestamp ASC")
    fun getAllTasks(): Flow<List<TaskUnit>>

    // Visible tasks: tasks that are not completed (isCompleted = 0)
    @Query("SELECT * FROM task_list WHERE isVisible = 1 ORDER BY estimatedCompletionTime ASC, deadlineTimestamp ASC")
    fun getVisibleTasks(): Flow<List<TaskUnit>>

    @Query("UPDATE task_list SET isCompleted = :completed WHERE taskId = :taskId")
    suspend fun markTaskAsCompleted(taskId: String, completed: Boolean)

    @Query("DELETE FROM task_list WHERE taskId = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("DELETE FROM task_list WHERE isCompleted = 1")
    suspend fun deleteCompleted()

    @Query("SELECT COUNT(*) FROM task_list WHERE isCompleted = 0")
    fun getUnfinishedCount(): Flow<Int>
}