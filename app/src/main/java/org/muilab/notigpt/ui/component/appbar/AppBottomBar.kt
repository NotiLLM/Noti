package org.muilab.notigpt.ui.component.appbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.muilab.notigpt.R
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE

@Composable
fun AppBottomBar(
    selectedCategory: String,
    onItemSelected: (String) -> Unit,
    unreadCounts: Map<String, Int> // The map of unread counts
) {
    val categoryNames = listOf(NOTI_CATEGORY_GENERAL, NOTI_CATEGORY_MAKETASK, NOTI_CATEGORY_SAVE, NOTI_CATEGORY_ARCHIVE)
    val iconResIds = listOf(R.drawable.notifications, R.drawable.task_no, R.drawable.save_no, R.drawable.archive_no)

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        categoryNames
            .zip(iconResIds)
            .forEach { (categoryName, iconResId) ->
                val unreadCount = unreadCounts[categoryName] ?: 0
                val totalCount = unreadCounts["$categoryName-Total"] ?: 0

                // Logic for Badge Display
                val (badgeCount, badgeColor) = when (categoryName) {
                    NOTI_CATEGORY_ARCHIVE -> {
                        // Archive: Show Unread only (Red)
                        unreadCount to MaterialTheme.colorScheme.error
                    }
                    else -> {
                        // Show Unread (Red) if any, else Total (Secondary)
                        if (unreadCount > 0) {
                            unreadCount to MaterialTheme.colorScheme.error
                        } else {
                            totalCount to MaterialTheme.colorScheme.secondary
                        }
                    }
                }

                NavigationBarItem(
                    selected = selectedCategory == categoryName,
                    onClick = { onItemSelected(categoryName) },
                    icon = {
                        Box {
                            Icon(
                                painter = painterResource(id = iconResId),
                                contentDescription = categoryName
                            )
                            if (badgeCount > 0) {
                                NotificationBadge(count = badgeCount, backgroundColor = badgeColor)
                            }
                        }
                    },
                    label = { Text(categoryName) }
                )
            }
    }
}

@Composable
private fun NotificationBadge(count: Int, backgroundColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            // Move the badge to the top right corner of the icon
            .offset(x = 25.dp, y = (-10).dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = contentColorFor(backgroundColor),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            lineHeight = 12.sp, // Match line height to font size
            style = LocalTextStyle.current.copy(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            )
        )
    }
}