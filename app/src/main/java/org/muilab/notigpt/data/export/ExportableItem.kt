package org.muilab.notigpt.data.export

import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.TodoStep

/**
 * Small adapter interface for exporting SavedItems and todo steps through one formatting path.
 *
 * Keep this as a presentation/export shape only. If export rules start affecting app behavior,
 * move those rules back to the SavedItem or step domain layer.
 */
interface ExportableItem {
    val exportTitle: String
    val exportDescription: String
    val exportDeadlineTimestamp: Long
    val exportIsCompleted: Boolean
}

/** Adapt a [SavedItem] to [ExportableItem]. */
fun SavedItem.asExportable(): ExportableItem = object : ExportableItem {
    override val exportTitle = title
    override val exportDescription = content
    override val exportDeadlineTimestamp = deadlineAtMs
    override val exportIsCompleted = isCompleted
}

/** Adapt a [TodoStep] to [ExportableItem]. */
fun TodoStep.asExportable(): ExportableItem = object : ExportableItem {
    override val exportTitle = text
    override val exportDescription = ""
    override val exportDeadlineTimestamp = 0L
    override val exportIsCompleted = isCompleted
}
