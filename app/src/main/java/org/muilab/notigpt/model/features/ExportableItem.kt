package org.muilab.notigpt.model.features

/**
 * Common shape for anything exportable to Google Tasks / Google Calendar.
 *
 * Both [ReminderUnit] and [SubTask] satisfy this contract, so the export
 * dialog and repository can be written once.
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
