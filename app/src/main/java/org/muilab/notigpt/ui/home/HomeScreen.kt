package org.muilab.notigpt.ui.home

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import org.muilab.notigpt.ui.theme.Dimens
import org.muilab.notigpt.ui.theme.NotiType
import org.muilab.notigpt.ui.theme.NotiTheme

/**
 * Home overview: the single entry portal that replaced the New/Tasks/Keep bottom tabs.
 *
 * Ordered by the user's real workflow: (1) review new/updated generated items, (2) check recent
 * notifications by category, (3) browse saved items via planned-date smart filters and the
 * Tasks/Keep collections.
 *
 * Visual system (Apple-restraint): a single tinted hero (the Review row); everything else is a
 * neutral card whose meaning comes from a small colored icon disc and a bold count.
 */
@Composable
fun HomeScreen(
    drawerViewModel: DrawerViewModel,
    onOpenReview: () -> Unit,
    onOpenNotiCategory: (String) -> Unit,
    onOpenSavedList: (SavedListFilter) -> Unit,
    dueReminderCount: Int = 0,
    onOpenReminders: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(),
) {
    val reviewCounts by homeViewModel.reviewCounts.collectAsState()
    val smartCounts by homeViewModel.smartFilterCounts.collectAsState()
    val llmByKey by drawerViewModel.llmStatesByKey.collectAsState()
    val newUnits by drawerViewModel.newNotificationUnits.collectAsState()
    val hasLoadedNotifications by drawerViewModel.hasLoadedInitialNotifications.collectAsState()

    val previews = remember(newUnits, llmByKey) {
        HomeViewModel.buildPreviews(newUnits, llmByKey, System.currentTimeMillis() - HomeViewModel.newNotiWindowMs())
    }
    val commPreview = previews[NotiCategory.Communication] ?: CategoryPreview()
    val contentPreview = previews[NotiCategory.Content] ?: CategoryPreview()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        // The single call-to-action; hidden entirely when there's nothing to review.
        if (reviewCounts.total > 0) {
            item { ReviewRow(counts = reviewCounts, onClick = onOpenReview) }
        }

        // Slim, zero-cost-when-empty strip: due scheduled reminders the user hasn't seen yet.
        if (dueReminderCount > 0) {
            item { RemindersDueStrip(count = dueReminderCount, onClick = onOpenReminders) }
        }

        item { HomeSectionHeader(stringResource(R.string.home_section_notifications)) }
        if (!hasLoadedNotifications) {
            item { InitialNotificationLoading() }
        } else {
            item {
                NotiCategoryRow(
                    iconRes = R.drawable.communication,
                    title = stringResource(R.string.home_noti_communication),
                    preview = commPreview,
                    // Both Home and the category screen count notification threads.
                    recentCount = commPreview.recentUnits,
                    olderCount = commPreview.olderUnits,
                    onClick = { onOpenNotiCategory(NotiCategory.Communication) },
                )
            }
            item {
                NotiCategoryRow(
                    iconRes = R.drawable.content,
                    title = stringResource(R.string.home_noti_content),
                    preview = contentPreview,
                    // Content also counts threads (notiKeys), not individual records.
                    recentCount = contentPreview.recentUnits,
                    olderCount = contentPreview.olderUnits,
                    onClick = { onOpenNotiCategory(NotiCategory.Content) },
                )
            }
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
private fun InitialNotificationLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = Dimens.sectionTop,
            bottom = Dimens.sectionHeaderBottom,
        ),
    )
}

@Composable
private fun ReviewRow(counts: ReviewCounts, onClick: () -> Unit) {
    val summary = reviewSummary(counts)
    // Neutral card in the same family as the smart-filter tiles; meaning carried by a tinted disc and
    // a big count, not a full colored slab.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenH, vertical = Dimens.inline)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterDisc(
                iconRes = R.drawable.inbox,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_review_title),
                    style = NotiType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(16.dp))
            Text(
                text = counts.total.toString(),
                style = NotiType.statNumber,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Slim due-reminders strip (accent = due-soon). Opens the scheduled Reminders screen. */
@Composable
private fun RemindersDueStrip(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenH, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = NotiTheme.semantic.dueSoonContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.cardInner, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.notifications_active),
                contentDescription = null,
                tint = NotiTheme.semantic.onDueSoonContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.home_reminders_due, count),
                style = MaterialTheme.typography.titleSmall,
                color = NotiTheme.semantic.onDueSoonContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.keyboard_arrow_down),
                contentDescription = null,
                tint = NotiTheme.semantic.onDueSoonContainer,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(-90f),
            )
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
private fun NotiCategoryRow(
    iconRes: Int,
    title: String,
    preview: CategoryPreview,
    recentCount: Int,
    olderCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenH, vertical = Dimens.cardOuterV)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = Dimens.cardInner, vertical = 14.dp),
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
                Text(text = title, style = NotiType.cardTitle)
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
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {

                if (recentCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.home_noti_window_hours,
                                org.muilab.notigpt.util.SharedPreferencesManager.homeNotiWindowHours,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CountBadge(
                            recentCount,
                            MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }

                if (olderCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_noti_older),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CountBadge(
                            olderCount,
                            MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
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
        // A single-record line carries no useful count; only show it once there's more than one.
        if (line.count > 1) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = line.count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SmartFilterGrid(
    counts: org.muilab.notigpt.data.local.room.dao.SmartFilterCounts,
    onOpen: (SavedListFilter) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Dimens.screenH),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmartFilterBox(
                count = counts.todayEarlier,
                label = stringResource(R.string.home_filter_today),
                iconRes = R.drawable.ic_calendar_today,
                discContainer = NotiTheme.semantic.todayContainer,
                discContent = NotiTheme.semantic.todayAccent,
                onClick = { onOpen(SavedListFilter.TodayEarlier) },
                modifier = Modifier.weight(1f),
            )
            SmartFilterBox(
                count = counts.upcoming,
                label = stringResource(R.string.home_filter_upcoming),
                iconRes = R.drawable.schedule,
                discContainer = NotiTheme.semantic.upcomingContainer,
                discContent = NotiTheme.semantic.upcomingAccent,
                onClick = { onOpen(SavedListFilter.Upcoming) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmartFilterBox(
                count = counts.someday,
                label = stringResource(R.string.home_filter_someday),
                iconRes = R.drawable.inbox,
                discContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                discContent = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onOpen(SavedListFilter.Someday) },
                modifier = Modifier.weight(1f),
            )
            SmartFilterBox(
                count = counts.starred,
                label = stringResource(R.string.home_filter_starred),
                iconRes = R.drawable.star_yes,
                discContainer = NotiTheme.semantic.starredContainer,
                discContent = NotiTheme.semantic.starred,
                onClick = { onOpen(SavedListFilter.Starred) },
                modifier = Modifier.weight(1f),
            )
        }
        // Undetermined is the large unplanned-triage pool; a full-width box, still a box (not a
        // collection row) so it reads as a smart filter. Task-only, so it skips the type split.
        SmartFilterBox(
            count = counts.undetermined,
            label = stringResource(R.string.home_filter_undetermined),
            iconRes = R.drawable.assignment_late,
            discContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
            discContent = MaterialTheme.colorScheme.onSurfaceVariant,
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
    iconRes: Int,
    discContainer: Color,
    discContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
) {
    // Neutral body; meaning carried by the colored disc + count.
    Surface(
        modifier = modifier
            .then(if (fullWidth) Modifier.height(72.dp) else Modifier.aspectRatio(1.6f))
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (fullWidth) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterDisc(iconRes, discContainer, discContent)
                Spacer(Modifier.size(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(16.dp))
                // Count on the right, matching every other smart-filter tile (icon-then-count reads
                // top-right there too).
                Text(
                    text = count.total.toString(),
                    style = NotiType.statNumber,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterDisc(iconRes, discContainer, discContent)
                    Text(
                        text = count.total.toString(),
                        style = NotiType.statNumber,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    TypeBreakdown(count = count, content = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Small colored icon disc — the sanctioned spot of section color on an otherwise neutral tile. */
@Composable
private fun FilterDisc(iconRes: Int, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(34.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
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
            style = NotiType.metadata,
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
    Surface(shape = MaterialTheme.shapes.extraSmall, color = containerColor) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
