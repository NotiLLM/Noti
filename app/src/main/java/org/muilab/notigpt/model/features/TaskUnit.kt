package org.muilab.notigpt.model.features

import androidx.room.Entity

@Entity(tableName = "task_list", primaryKeys = ["taskId"])
data class TaskUnit(
    val taskId: String,
    val isCompleted: Boolean = false,
    val isVisible: Boolean = true,
    val taskDescription: String,
    val deadlineTimestamp: Long,
    val estimatedCompletionTime: Long,
    val associatedNotis: Set<String>,
    val userEdited: Boolean = false
)