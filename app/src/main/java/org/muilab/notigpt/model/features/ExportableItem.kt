package org.muilab.notigpt.model.features

/**
 * Small adapter interface for exporting reminders and subtasks through one formatting path.
 *
 * Keep this as a presentation/export shape only. If export rules start affecting app behavior,
 * move those rules back to the reminder or subtask domain layer.
 */
interface ExportableItem {
    val exportTitle: String
    val exportDescription: String
    val exportDeadlineTimestamp: Long
    val exportStartTime: Long
    val exportEndTime: Long
    val exportIsCompleted: Boolean
}

/** Adapt a [ReminderUnit] to [ExportableItem]. */
fun ReminderUnit.asExportable(): ExportableItem = object : ExportableItem {
    override val exportTitle = reminderTitle
    override val exportDescription = reminderContent
    override val exportDeadlineTimestamp = deadlineTimestamp
    override val exportStartTime = startTime
    override val exportEndTime = endTime
    override val exportIsCompleted = isCompleted
}

/** Adapt a [SubTask] to [ExportableItem]. */
fun SubTask.asExportable(): ExportableItem = object : ExportableItem {
    override val exportTitle = title
    override val exportDescription = description
    override val exportDeadlineTimestamp = deadlineTimestamp
    override val exportStartTime = startTime
    override val exportEndTime = endTime
    override val exportIsCompleted = isCompleted
}
