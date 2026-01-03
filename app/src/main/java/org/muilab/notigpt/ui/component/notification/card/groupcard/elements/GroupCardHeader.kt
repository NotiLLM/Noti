package org.muilab.notigpt.ui.component.notification.card.groupcard.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R

@Composable
internal fun GroupCardHeader(
    title: String,
    childCount: Int,
    expanded: Boolean,
    isSortingMode: Boolean,
    onToggleExpanded: () -> Unit,
    onEditTitle: () -> Unit,
    onUngroup: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.size(32.dp)
        ) {
            Text(if (expanded) "▼" else "▶", fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .clickable(onClick = onToggleExpanded)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val countText = "$childCount notifications" +
                if (!expanded && childCount > 1) " (${childCount - 1} more...)" else ""
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onEditTitle,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit group", modifier = Modifier.size(16.dp))
        }

        if (isSortingMode) {
            IconButton(
                modifier = Modifier.minimumInteractiveComponentSize(),
                onClick = onUngroup
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.leave_group),
                    contentDescription = "Ungroup"
                )
            }
        }
    }
}
