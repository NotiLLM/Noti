package org.muilab.notigpt.ui.notification.component.card.noticard.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.R
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel

/**
 * Options for one notification card, shown as a Material bottom sheet of list items (was an
 * AlertDialog of text buttons). Long-press on the card opens it. Every action the card exposes
 * elsewhere is reachable here: pin/unpin, extract task, and — when available — create a scheduled
 * reminder. Persisted actions flow through callbacks owned by the screen/ViewModel.
 */
data class NotiCardOptionsState(
    val isPinned: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotiCardOptionsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    drawerViewModel: DrawerViewModel,
    notiKey: String,
    state: NotiCardOptionsState,
    onCreateReminder: (() -> Unit)? = null,
    onOpenSavedItem: (SavedItem) -> Unit = {},
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Cross-links: the active task/keep items this thread has produced. Loaded when the sheet opens.
    var linkedItems by remember(notiKey) { mutableStateOf<List<SavedItem>>(emptyList()) }
    LaunchedEffect(notiKey) {
        linkedItems = withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context.applicationContext)
            val ids = db.notiSavedItemLinkDao().getSavedItemIdsByNotiKey(notiKey)
            if (ids.isEmpty()) emptyList()
            else db.reminderListDao().getByIds(ids).filter { !it.isArchived && !it.isCompleted }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding()) {
            OptionRow(
                iconRes = if (state.isPinned) R.drawable.pin_yes else R.drawable.pin_no,
                label = stringResource(if (state.isPinned) R.string.ui_noti_action_unpin else R.string.ui_noti_action_pin),
                onClick = {
                    drawerViewModel.actOnNoti(notiKey, if (state.isPinned) "unpin" else "pin")
                    onDismiss()
                },
            )
            OptionRow(
                iconRes = R.drawable.task,
                label = stringResource(R.string.ui_action_extract_reminder),
                onClick = {
                    drawerViewModel.actOnNoti(notiKey, "extract_reminder")
                    onDismiss()
                },
            )
            if (onCreateReminder != null) {
                OptionRow(
                    iconRes = R.drawable.schedule,
                    label = stringResource(R.string.ui_reminders_create_button),
                    onClick = {
                        onCreateReminder()
                        onDismiss()
                    },
                )
            }

            // Cross-links to items this notification produced.
            if (linkedItems.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = stringResource(R.string.ui_noti_linked_items),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                linkedItems.forEach { item ->
                    LinkedItemRow(
                        item = item,
                        onClick = {
                            onOpenSavedItem(item)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** One linked task/keep item, shown as context (title + type icon). Tapping opens its detail. */
@Composable
private fun LinkedItemRow(item: SavedItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            val fallback = stringResource(
                if (item.isTask) R.string.ui_reminders_untitled_task else R.string.ui_reminders_untitled_memo
            )
            Text(
                text = item.title.ifBlank { fallback },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(if (item.isTask) R.drawable.task else R.drawable.bookmark),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun OptionRow(iconRes: Int, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
