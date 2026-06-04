package org.muilab.notigpt.ui.home

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.ui.notification.component.card.noticard.NotiCard
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ReminderViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NewScreen(
    drawerViewModel: DrawerViewModel,
    reminderViewModel: ReminderViewModel,
    searchQuery: String = "",
) {
    val context = LocalContext.current
    val newItems by reminderViewModel.newSavedItems.collectAsState()
    val newUnits by drawerViewModel.newNotificationUnits.collectAsState()
    val newRecordsByKey by drawerViewModel.newNotificationRecords.collectAsState()
    var deleteTarget by remember { mutableStateOf<Pair<String, List<SavedItem>>?>(null) }

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
                    count = newTasks.size,
                    items = newTasks,
                    showBulkActions = normalizedQuery.isBlank(),
                    onSaveAll = {
                        reminderViewModel.markSavedByIds(newTasks.map { it.savedItemId })
                        Toast.makeText(context, "Moved to Tasks", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteAll = { deleteTarget = "tasks" to newTasks },
                )
            }
        }
        items(newTasks, key = { it.savedItemId }) { item ->
            NewSavedItemCard(
                item = item,
                onSave = {
                    reminderViewModel.markSaved(item.savedItemId)
                    Toast.makeText(context, "Moved to Tasks", Toast.LENGTH_SHORT).show()
                },
                onDelete = { reminderViewModel.delete(item.savedItemId) },
            )
        }
        if (newKeep.isNotEmpty()) {
            item {
                NewSavedItemSectionHeader(
                    title = "New Keep",
                    count = newKeep.size,
                    items = newKeep,
                showBulkActions = normalizedQuery.isBlank(),
                onSaveAll = {
                    reminderViewModel.markSavedByIds(newKeep.map { it.savedItemId })
                    Toast.makeText(context, "Moved to Keep", Toast.LENGTH_SHORT).show()
                },
                    onDeleteAll = { deleteTarget = "keep" to newKeep },
                )
            }
        }
        items(newKeep, key = { it.savedItemId }) { item ->
            NewSavedItemCard(
                item = item,
                onSave = {
                    reminderViewModel.markSaved(item.savedItemId)
                    Toast.makeText(context, "Moved to Keep", Toast.LENGTH_SHORT).show()
                },
                onDelete = { reminderViewModel.delete(item.savedItemId) },
            )
        }
        if (filteredUnits.isNotEmpty()) {
            item { SectionTitle("New Notifications", filteredUnits.size) }
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
    items: List<SavedItem>,
    showBulkActions: Boolean,
    onSaveAll: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("$title ($count)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (items.isNotEmpty() && showBulkActions) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveAll) { Text("Looks good, save all") }
                TextButton(onClick = onDeleteAll) { Text("Delete all") }
            }
        }
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

@Composable
private fun NewSavedItemCard(
    item: SavedItem,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall)
            if (item.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.content, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text("Looks good") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
