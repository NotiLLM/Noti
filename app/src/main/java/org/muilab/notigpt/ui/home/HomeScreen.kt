package org.muilab.notigpt.ui.home

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.NotiCategory
import org.muilab.notigpt.ui.common.component.AppIconImage
import org.muilab.notigpt.ui.common.navigation.SavedListFilter
import org.muilab.notigpt.ui.home.viewmodel.CategoryPreview
import org.muilab.notigpt.ui.home.viewmodel.CategoryPreviewLine
import org.muilab.notigpt.ui.home.viewmodel.HomeViewModel
import org.muilab.notigpt.ui.home.viewmodel.ReviewCounts
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.theme.NotiTheme

/**
 * Home overview: the single entry portal that replaced the New/Tasks/Keep bottom tabs.
 *
 * Ordered by the user's real workflow: (1) review new/updated generated items, (2) check recent
 * notifications by category, (3) browse saved items via planned-date smart filters and the
 * Tasks/Keep collections.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun HomeScreen(
    drawerViewModel: DrawerViewModel,
    onOpenReview: () -> Unit,
    onOpenNotiCategory: (String) -> Unit,
    onOpenSavedList: (SavedListFilter) -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val reviewCounts by homeViewModel.reviewCounts.collectAsState()
    val smartCounts by homeViewModel.smartFilterCounts.collectAsState()
    val llmByKey by drawerViewModel.llmStatesByKey.collectAsState()
    val newUnits by drawerViewModel.newNotificationUnits.collectAsState()

    val previews = remember(newUnits, llmByKey) {
        HomeViewModel.buildPreviews(newUnits, llmByKey, System.currentTimeMillis() - HomeViewModel.NEW_NOTI_WINDOW_MS)
    }
    val commPreview = previews[NotiCategory.Communication] ?: CategoryPreview()
    val contentPreview = previews[NotiCategory.Content] ?: CategoryPreview()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        item {
            ReviewRow(counts = reviewCounts, onClick = onOpenReview)
        }

        item { HomeSectionHeader(stringResource(R.string.home_section_notifications)) }
        item {
            NotiCategoryRow(
                iconRes = R.drawable.communication,
                title = stringResource(R.string.home_noti_communication),
                preview = commPreview,
                // Communication counts individual messages (records).
                badgeCount = commPreview.totalUnits,
                onClick = { onOpenNotiCategory(NotiCategory.Communication) },
            )
        }
        item {
            NotiCategoryRow(
                iconRes = R.drawable.content,
                title = stringResource(R.string.home_noti_content),
                preview = contentPreview,
                // Content counts threads (notiKeys), not individual records.
                badgeCount = contentPreview.totalUnits,
                onClick = { onOpenNotiCategory(NotiCategory.Content) },
            )
        }

        item { HomeSectionHeader(stringResource(R.string.home_section_saved)) }
        item {
            SmartFilterGrid(
                counts = smartCounts,
                onOpen = onOpenSavedList,
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ReviewRow(counts: ReviewCounts, onClick: () -> Unit) {
    val summary = reviewSummary(counts)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(enabled = counts.total > 0, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.inbox),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_review_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (counts.total > 0) {
                CountBadge(counts.total, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
            }
        }
    }
}

@Composable
private fun reviewSummary(counts: ReviewCounts): String {
    if (counts.total == 0) return stringResource(R.string.home_review_none)
    // Resolve all candidate strings up front (composable context), then pick the non-zero ones.
    val newTasks = stringResource(R.string.home_review_new_tasks, counts.newTasks)
    val updatedTasks = stringResource(R.string.home_review_updated_tasks, counts.updatedTasks)
    val newKeeps = stringResource(R.string.home_review_new_keeps, counts.newKeeps)
    val updatedKeeps = stringResource(R.string.home_review_updated_keeps, counts.updatedKeeps)
    val parts = buildList {
        if (counts.newTasks > 0) add(newTasks)
        if (counts.updatedTasks > 0) add(updatedTasks)
        if (counts.newKeeps > 0) add(newKeeps)
        if (counts.updatedKeeps > 0) add(updatedKeeps)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun NotiCategoryRow(iconRes: Int, title: String, preview: CategoryPreview, badgeCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (preview.topLines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_noti_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    preview.topLines.forEach { line -> CategoryPreviewLineRow(line) }
                }
            }
            if (badgeCount > 0) {
                CountBadge(badgeCount, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainerHighest)
            }
        }
    }
}

@Composable
private fun CategoryPreviewLineRow(line: CategoryPreviewLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        line.icon?.let { bitmap ->
            AppIconImage(
                bitmap = bitmap,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = line.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = line.count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmartFilterGrid(
    counts: org.muilab.notigpt.data.local.room.dao.SmartFilterCounts,
    onOpen: (SavedListFilter) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmartFilterBox(
                count = counts.todayEarlier,
                label = stringResource(R.string.home_filter_today),
                container = NotiTheme.semantic.taskContainer,
                content = NotiTheme.semantic.onTaskContainer,
                onClick = { onOpen(SavedListFilter.TodayEarlier) },
                modifier = Modifier.weight(1f),
            )
            SmartFilterBox(
                count = counts.upcoming,
                label = stringResource(R.string.home_filter_upcoming),
                container = NotiTheme.semantic.keepContainer,
                content = NotiTheme.semantic.onKeepContainer,
                onClick = { onOpen(SavedListFilter.Upcoming) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmartFilterBox(
                count = counts.someday,
                label = stringResource(R.string.home_filter_someday),
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurface,
                onClick = { onOpen(SavedListFilter.Someday) },
                modifier = Modifier.weight(1f),
            )
            SmartFilterBox(
                count = counts.starred,
                label = stringResource(R.string.home_filter_starred),
                container = NotiTheme.semantic.dueSoonContainer,
                content = NotiTheme.semantic.onDueSoonContainer,
                onClick = { onOpen(SavedListFilter.Starred) },
                modifier = Modifier.weight(1f),
            )
        }
        // Undetermined is the large unplanned-triage pool; give it its own full-width box, still a box
        // (not a collection row) so it reads as a smart filter. Task-only, so it skips the type split.
        SmartFilterBox(
            count = counts.undetermined,
            label = stringResource(R.string.home_filter_undetermined),
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { onOpen(SavedListFilter.Undetermined) },
            modifier = Modifier.fillMaxWidth(),
            fullWidth = true,
        )
    }
}

@Composable
private fun SmartFilterBox(
    count: org.muilab.notigpt.data.local.room.dao.BucketCount,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
) {
    Surface(
        modifier = modifier
            .then(if (fullWidth) Modifier.height(72.dp) else Modifier.aspectRatio(1.7f))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = container,
    ) {
        if (fullWidth) {
            // Undetermined is task-only: the total already reflects tasks, so no type split here.
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = count.total.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
                Spacer(Modifier.size(16.dp))
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = content)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = count.total.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall, color = content)
                    TypeBreakdown(count = count, content = content)
                }
            }
        }
    }
}

/** "N 📋 · M 📌" type split under a smart-filter box; each type shown only when it has items. */
@Composable
private fun TypeBreakdown(count: org.muilab.notigpt.data.local.room.dao.BucketCount, content: Color) {
    if (count.tasks == 0 && count.keeps == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (count.tasks > 0) TypeCountChip(count.tasks, R.drawable.task, content)
        if (count.tasks > 0 && count.keeps > 0) {
            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.6f),
            )
        }
        if (count.keeps > 0) TypeCountChip(count.keeps, R.drawable.keep, content)
    }
}

@Composable
private fun TypeCountChip(value: Int, iconRes: Int, content: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CountBadge(count: Int, contentColor: Color, containerColor: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = containerColor) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
