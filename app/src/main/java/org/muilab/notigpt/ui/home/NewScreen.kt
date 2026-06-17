package org.muilab.notigpt.ui.home

import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.data.export.asExportable
import org.muilab.notigpt.domain.notification.countClearableActiveNotifications
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.ui.notification.component.card.noticard.NotiCard
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.reminder.component.SavedSubItemDetailScreen
import org.muilab.notigpt.ui.reminder.screen.ReminderCard
import org.muilab.notigpt.ui.reminder.screen.ReminderDateTimeDialog
import org.muilab.notigpt.ui.reminder.screen.ReminderDetailScreen
import org.muilab.notigpt.ui.reminder.viewmodel.ReminderViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel
import org.muilab.notigpt.ui.theme.NotiTheme

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NewScreen(
    drawerViewModel: DrawerViewModel,
    reminderViewModel: ReminderViewModel,
    scheduledReminderViewModel: ScheduledReminderViewModel? = null,
    preferenceViewModel: PreferenceViewModel,
    searchQuery: String = "",
) {
    val context = LocalContext.current
    val newItems by reminderViewModel.newSavedItems.collectAsState()
    val newUnits by drawerViewModel.newNotificationUnits.collectAsState()
    val newRecordsByKey by drawerViewModel.newNotificationRecords.collectAsState()
    val allSavedSubItemsByReminder by reminderViewModel.allSavedSubItemsByReminder.collectAsState()
    val relatedNotificationsState by reminderViewModel.relatedNotificationsState.collectAsState()
    val googleTasksExportResult by reminderViewModel.googleTasksExportResult.collectAsState()

    var deleteTarget by remember { mutableStateOf<Pair<String, List<SavedItem>>?>(null) }

    // Multi-select triage: which section is selecting (SavedItemType.Task / .Keep / null) and the picked ids.
    var selectionSection by remember { mutableStateOf<String?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }
    fun exitSelection() { selectionSection = null; selectedIds.clear() }
    fun toggleSelected(id: String) { if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id) }

    var editing by remember { mutableStateOf<SavedItem?>(null) }
    var editingInitialSnapshot by remember { mutableStateOf<SavedItem?>(null) }
    var editingSavedSubItem by remember { mutableStateOf<SavedSubItem?>(null) }
    var editingSavedSubItemInitial by remember { mutableStateOf<SavedSubItem?>(null) }
    var reminderDialogSavedItem by remember { mutableStateOf<SavedItem?>(null) }

    val normalizedQuery = searchQuery.trim().lowercase()
    fun SavedItem.matchesQuery(): Boolean {
        if (normalizedQuery.isBlank()) return true
        return listOf(title, content, itemType, state).any { it.lowercase().contains(normalizedQuery) }
    }

    fun NotiDisplayUnit.matchesQuery(): Boolean {
        if (normalizedQuery.isBlank()) return true
        val records = newRecordsByKey[notiKey].orEmpty()
        return records.any { record ->
            listOf(record.title, record.content, record.person).any { it.lowercase().contains(normalizedQuery) }
        } || notiUnit.appName.lowercase().contains(normalizedQuery)
    }

    fun openEditor(item: SavedItem) {
        editing = item
        editingInitialSnapshot = item
    }

    fun persistEditedItem(updated: SavedItem, base: SavedItem?, persistUnchanged: Boolean = true) {
        val emptyNow = updated.title.isBlank() && updated.content.isBlank()
        if (emptyNow) {
            reminderViewModel.delete(updated.savedItemId)
            return
        }

        val contentChanged = base != null && (base.title != updated.title || base.content != updated.content)
        val changed = base == null ||
            base.title != updated.title ||
            base.content != updated.content ||
            base.isTask != updated.isTask ||
            base.isCompleted != updated.isCompleted ||
            base.deadlineAtMs != updated.deadlineAtMs ||
            base.estimatedCompletionTime != updated.estimatedCompletionTime

        if (!changed && !persistUnchanged) return

        reminderViewModel.upsert(
            updated.copy(
                state = if (updated.isTask && updated.isCompleted) SavedItemState.Completed else SavedItemState.Saved,
                userEdited = updated.userEdited || changed,
                humanEditCount = if (contentChanged) updated.humanEditCount + 1 else updated.humanEditCount,
                lastUpdateTimestamp = System.currentTimeMillis(),
            )
        )
        if (changed && base != null && base.origin.contains("llm")) {
            preferenceViewModel.startFlow(
                entryPoint = PreferenceEntryPoint.EDIT,
                reminder = updated,
                reminderBefore = base,
            )
        }
    }

    fun exportSubItemToCalendar(st: SavedSubItem) {
        val calIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, st.title)
            putExtra(CalendarContract.Events.DESCRIPTION, st.description)
            if (st.startAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.startAtMs)
            if (st.endAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.endAtMs)
        }
        try {
            context.startActivity(calIntent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.google_calendar_no_app), Toast.LENGTH_SHORT).show()
        }
    }

    val newTasks = newItems.filter { it.itemType == SavedItemType.Task && it.matchesQuery() }
    val newKeep = newItems.filter { it.itemType == SavedItemType.Keep && it.matchesQuery() }
    val filteredUnits = newUnits.filter { it.matchesQuery() }
    val hasAnyVisibleSection = newTasks.isNotEmpty() || newKeep.isNotEmpty() || filteredUnits.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        if (newTasks.isNotEmpty()) {
            item {
                NewSavedItemSectionHeader(
                    title = "New Tasks",
                    accent = NotiTheme.semantic.taskAccent,
                    iconRes = R.drawable.task,
                    count = newTasks.size,
                    showBulkActions = normalizedQuery.isBlank(),
                    selectionMode = selectionSection == SavedItemType.Task,
                    selectedCount = if (selectionSection == SavedItemType.Task) selectedIds.size else 0,
                    onSaveAll = {
                        reminderViewModel.markSavedByIds(newTasks.map { it.savedItemId })
                        Toast.makeText(context, "Moved to Tasks", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteAll = { deleteTarget = "tasks" to newTasks },
                    onEnterSelect = { selectionSection = SavedItemType.Task; selectedIds.clear() },
                    onExitSelect = { exitSelection() },
                    onSaveSelected = {
                        reminderViewModel.markSavedByIds(selectedIds.toList())
                        Toast.makeText(context, "Saved to Tasks", Toast.LENGTH_SHORT).show()
                        exitSelection()
                    },
                    onDeleteSelected = {
                        reminderViewModel.deleteByIds(selectedIds.toList())
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        exitSelection()
                    },
                )
            }
        }
        items(newTasks, key = { it.savedItemId }) { item ->
            ReminderCard(
                reminder = item,
                subTasks = allSavedSubItemsByReminder[item.savedItemId] ?: emptyList(),
                onDelete = {
                    reminderViewModel.delete(item.savedItemId)
                    if (item.origin.contains("llm")) {
                        preferenceViewModel.startFlow(
                            entryPoint = PreferenceEntryPoint.DELETE,
                            reminder = item,
                        )
                    }
                },
                onToggleCompleted = { completed -> reminderViewModel.toggleCompleted(item, completed) },
                onEdit = { openEditor(item) },
                onCreateReminder = scheduledReminderViewModel?.let { { reminderDialogSavedItem = item } },
                onQuickExportTasks = { reminderViewModel.exportToGoogleTasks(item) },
                showDeleteButton = true,
                sectionAccent = NotiTheme.semantic.taskAccent,
                selectionMode = selectionSection == SavedItemType.Task,
                selected = selectedIds.contains(item.savedItemId),
                onSelectedChange = { checked -> if (checked) { if (item.savedItemId !in selectedIds) selectedIds.add(item.savedItemId) } else selectedIds.remove(item.savedItemId) },
                onArchive = { reminderViewModel.archiveKeep(item.savedItemId) },
                onSavedSubItemToggle = { stId, checked -> reminderViewModel.toggleSavedSubItemCompleted(stId, checked) },
                onSavedSubItemClick = { st ->
                    openEditor(item)
                    editingSavedSubItem = st
                    editingSavedSubItemInitial = st
                },
                onSavedSubItemEdit = { st ->
                    openEditor(item)
                    editingSavedSubItem = st
                    editingSavedSubItemInitial = st
                },
                onSavedSubItemDelete = { st -> reminderViewModel.deleteSavedSubItem(st.savedSubItemId) },
                onSavedSubItemExportGoogleTasks = { st -> reminderViewModel.exportToGoogleTasks(st.asExportable()) },
                onSavedSubItemExportGoogleCalendar = { st -> exportSubItemToCalendar(st) },
            )
        }
        if (newKeep.isNotEmpty()) {
            item {
                NewSavedItemSectionHeader(
                    title = "New Keep",
                    accent = NotiTheme.semantic.keepAccent,
                    iconRes = R.drawable.bookmark,
                    count = newKeep.size,
                    showBulkActions = normalizedQuery.isBlank(),
                    selectionMode = selectionSection == SavedItemType.Keep,
                    selectedCount = if (selectionSection == SavedItemType.Keep) selectedIds.size else 0,
                    onSaveAll = {
                        reminderViewModel.markSavedByIds(newKeep.map { it.savedItemId })
                        Toast.makeText(context, "Moved to Keep", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteAll = { deleteTarget = "keep" to newKeep },
                    onEnterSelect = { selectionSection = SavedItemType.Keep; selectedIds.clear() },
                    onExitSelect = { exitSelection() },
                    onSaveSelected = {
                        reminderViewModel.markSavedByIds(selectedIds.toList())
                        Toast.makeText(context, "Saved to Keep", Toast.LENGTH_SHORT).show()
                        exitSelection()
                    },
                    onDeleteSelected = {
                        reminderViewModel.deleteByIds(selectedIds.toList())
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        exitSelection()
                    },
                )
            }
        }
        items(newKeep, key = { it.savedItemId }) { item ->
            ReminderCard(
                reminder = item,
                subTasks = allSavedSubItemsByReminder[item.savedItemId] ?: emptyList(),
                onDelete = {
                    reminderViewModel.delete(item.savedItemId)
                    if (item.origin.contains("llm")) {
                        preferenceViewModel.startFlow(
                            entryPoint = PreferenceEntryPoint.DELETE,
                            reminder = item,
                        )
                    }
                },
                onToggleCompleted = { completed -> reminderViewModel.toggleCompleted(item, completed) },
                onEdit = { openEditor(item) },
                onCreateReminder = scheduledReminderViewModel?.let { { reminderDialogSavedItem = item } },
                showDeleteButton = true,
                sectionAccent = NotiTheme.semantic.keepAccent,
                selectionMode = selectionSection == SavedItemType.Keep,
                selected = selectedIds.contains(item.savedItemId),
                onSelectedChange = { checked -> if (checked) { if (item.savedItemId !in selectedIds) selectedIds.add(item.savedItemId) } else selectedIds.remove(item.savedItemId) },
                onArchive = { reminderViewModel.archiveKeep(item.savedItemId) },
                onSavedSubItemToggle = { stId, checked -> reminderViewModel.toggleSavedSubItemCompleted(stId, checked) },
                onSavedSubItemClick = { st ->
                    openEditor(item)
                    editingSavedSubItem = st
                    editingSavedSubItemInitial = st
                },
                onSavedSubItemEdit = { st ->
                    openEditor(item)
                    editingSavedSubItem = st
                    editingSavedSubItemInitial = st
                },
                onSavedSubItemDelete = { st -> reminderViewModel.deleteSavedSubItem(st.savedSubItemId) },
                onSavedSubItemExportGoogleTasks = { st -> reminderViewModel.exportToGoogleTasks(st.asExportable()) },
                onSavedSubItemExportGoogleCalendar = { st -> exportSubItemToCalendar(st) },
            )
        }
        if (filteredUnits.isNotEmpty()) {
            item {
                NewNotificationsSectionHeader(
                    count = filteredUnits.size,
                    clearableCount = countClearableActiveNotifications(filteredUnits),
                    onClearAll = { drawerViewModel.deleteAllNotis() },
                )
            }
        }
        items(filteredUnits, key = { it.notiKey }) { displayUnit ->
            val newRecords = newRecordsByKey[displayUnit.notiKey].orEmpty()
            val matchingRecords = newRecords.filter { record ->
                normalizedQuery.isBlank() || listOf(record.title, record.content, record.person)
                    .any { it.lowercase().contains(normalizedQuery) }
            }
            val recordsToShow = matchingRecords.ifEmpty { newRecords }
            NotiCard(
                context = context,
                notiDisplayUnit = NotiDisplayUnit(displayUnit.notiUnit, recordsToShow),
                isDragging = false,
                drawerViewModel = drawerViewModel,
                isCardVisible = true,
                parentViewport = Rect.Zero,
            )
        }
        if (!hasAnyVisibleSection) {
            item { EmptySectionText(if (normalizedQuery.isBlank()) "No new items" else "No matching new items") }
        }
    }

    reminderDialogSavedItem?.let { target ->
        ReminderDateTimeDialog(
            title = stringResource(R.string.ui_reminders_create_button),
            initialAtMs = System.currentTimeMillis(),
            onDismiss = { reminderDialogSavedItem = null },
            onConfirm = { remindAtMs ->
                scheduledReminderViewModel?.createForSavedItem(target, remindAtMs)
                reminderDialogSavedItem = null
            },
        )
    }

    editing?.let { current ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { /* consume */ },
                )
        ) {
            ReminderDetailScreen(
                initial = current,
                drawerViewModel = drawerViewModel,
                onCreateReminder = scheduledReminderViewModel?.let { { reminderDialogSavedItem = current } },
                onBack = { updatedOrNull ->
                    if (updatedOrNull != null) {
                        persistEditedItem(
                            updated = updatedOrNull,
                            base = editingInitialSnapshot,
                            persistUnchanged = false,
                        )
                    }
                    editing = null
                    editingInitialSnapshot = null
                    editingSavedSubItem = null
                    editingSavedSubItemInitial = null
                },
                onDelete = { id ->
                    val deletedItem = editingInitialSnapshot
                    reminderViewModel.delete(id)
                    editing = null
                    editingInitialSnapshot = null
                    editingSavedSubItem = null
                    editingSavedSubItemInitial = null
                    if (deletedItem != null && deletedItem.origin.contains("llm")) {
                        preferenceViewModel.startFlow(
                            entryPoint = PreferenceEntryPoint.DELETE,
                            reminder = deletedItem,
                        )
                    }
                },
                onSave = { updated ->
                    persistEditedItem(updated, editingInitialSnapshot)
                    editing = null
                    editingInitialSnapshot = null
                    editingSavedSubItem = null
                    editingSavedSubItemInitial = null
                },
                onExportToGoogleTasks = { reminder -> reminderViewModel.exportToGoogleTasks(reminder) },
                isGoogleTasksExporting = googleTasksExportResult is ReminderViewModel.GoogleTasksExportResult.Loading,
                onRegenerate = { reminderViewModel.regenerateOne(current.savedItemId) },
                relatedNotificationsState = relatedNotificationsState,
                onLoadRelatedNotifications = { reminder -> reminderViewModel.loadRelatedNotifications(reminder) },
                subTasks = allSavedSubItemsByReminder[current.savedItemId] ?: emptyList(),
                onAddSavedSubItem = { reminderViewModel.addSavedSubItem(current.savedItemId) },
                onSavedSubItemToggle = { stId, checked -> reminderViewModel.toggleSavedSubItemCompleted(stId, checked) },
                onSavedSubItemClick = { st ->
                    editingSavedSubItem = st
                    editingSavedSubItemInitial = st
                },
                onSavedSubItemEdit = { st ->
                    editingSavedSubItem = st
                    editingSavedSubItemInitial = st
                },
                onSavedSubItemDelete = { st -> reminderViewModel.deleteSavedSubItem(st.savedSubItemId) },
                onSavedSubItemExportGoogleTasks = { st -> reminderViewModel.exportToGoogleTasks(st.asExportable()) },
                onSavedSubItemExportGoogleCalendar = { st -> exportSubItemToCalendar(st) },
            )

            editingSavedSubItem?.let { stCurrent ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { /* consume */ },
                        )
                ) {
                    SavedSubItemDetailScreen(
                        initial = stCurrent,
                        onBack = { updatedOrNull ->
                            if (updatedOrNull != null) {
                                val base = editingSavedSubItemInitial
                                val changed = base != null && (
                                    base.title != updatedOrNull.title ||
                                        base.description != updatedOrNull.description ||
                                        base.isTask != updatedOrNull.isTask ||
                                        base.isEvent != updatedOrNull.isEvent ||
                                        base.isCompleted != updatedOrNull.isCompleted ||
                                        base.deadlineAtMs != updatedOrNull.deadlineAtMs ||
                                        base.startAtMs != updatedOrNull.startAtMs ||
                                        base.endAtMs != updatedOrNull.endAtMs
                                )
                                if (changed) reminderViewModel.upsertSavedSubItem(updatedOrNull)
                            }
                            editingSavedSubItem = null
                            editingSavedSubItemInitial = null
                        },
                        onDelete = { stId ->
                            reminderViewModel.deleteSavedSubItem(stId)
                            editingSavedSubItem = null
                            editingSavedSubItemInitial = null
                        },
                        onSave = { updated ->
                            reminderViewModel.upsertSavedSubItem(updated)
                            editingSavedSubItem = null
                            editingSavedSubItemInitial = null
                        },
                        onExportGoogleTasks = { st -> reminderViewModel.exportToGoogleTasks(st.asExportable()) },
                        onExportGoogleCalendar = { st -> exportSubItemToCalendar(st) },
                    )
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete these suggestions?") },
            text = { Text("This will remove the shown new ${target.first} items.") },
            confirmButton = {
                TextButton(onClick = {
                    reminderViewModel.deleteByIds(target.second.map { it.savedItemId })
                    Toast.makeText(context, "Deleted suggestions", Toast.LENGTH_SHORT).show()
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NewSavedItemSectionHeader(
    title: String,
    count: Int,
    iconRes: Int,
    showBulkActions: Boolean,
    accent: Color,
    selectionMode: Boolean,
    selectedCount: Int,
    onSaveAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onEnterSelect: () -> Unit,
    onExitSelect: () -> Unit,
    onSaveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    if (selectionMode) {
        // Contextual selection bar: Cancel · N selected · Save N · Delete N
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onExitSelect) {
                Icon(painterResource(R.drawable.close), contentDescription = "Cancel selection")
            }
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = onSaveSelected,
                enabled = selectedCount > 0,
                label = { Text("Save $selectedCount", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(painterResource(R.drawable.check), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) },
            )
            IconButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
                Icon(painterResource(R.drawable.delete), contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeaderRow(iconRes = iconRes, iconTint = accent, label = title, count = count, modifier = Modifier.weight(1f))
            if (showBulkActions) {
                AssistChip(
                    onClick = onSaveAll,
                    label = { Text("Save all", style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { Icon(painterResource(R.drawable.check), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) },
                )
                IconButton(onClick = onEnterSelect) {
                    Icon(painterResource(R.drawable.checklist), contentDescription = "Select", tint = accent, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onDeleteAll) {
                    Icon(painterResource(R.drawable.delete), contentDescription = "Delete all", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun NewNotificationsSectionHeader(
    count: Int,
    clearableCount: Int,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeaderRow(
            iconRes = R.drawable.notifications,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            label = "New Notifications",
            count = count,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            enabled = clearableCount > 0,
            onClick = onClearAll,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.sweep),
                contentDescription = stringResource(R.string.ui_action_clear_unpinned_notifications),
            )
        }
    }
}

/** Shared section header: [icon] Label  count — with the count in a lighter style. */
@Composable
private fun SectionHeaderRow(
    iconRes: Int,
    iconTint: Color,
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
