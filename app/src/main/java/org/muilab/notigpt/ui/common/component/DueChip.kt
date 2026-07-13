package org.muilab.notigpt.ui.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.theme.NotiTheme
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr

/** Deadline urgency buckets driving the [DueChip] color treatment. */
enum class DueUrgency { OVERDUE, SOON, LATER }

private const val SOON_WINDOW_MS = 48L * 60L * 60L * 1000L

/** Buckets a deadline (ms since epoch) relative to now. */
fun dueUrgencyOf(deadlineAtMs: Long, now: Long = System.currentTimeMillis()): DueUrgency = when {
    deadlineAtMs < now -> DueUrgency.OVERDUE
    deadlineAtMs - now <= SOON_WINDOW_MS -> DueUrgency.SOON
    else -> DueUrgency.LATER
}

/**
 * A small pill showing a task deadline as `absolute (relative)`, colored by urgency:
 * overdue = red, due soon (≤48h) = amber, later = neutral.
 *
 * Deadlines ≤ 0 render nothing; callers should show their own "no deadline" affordance.
 */
@Composable
fun DueChip(
    deadlineAtMs: Long,
    modifier: Modifier = Modifier,
) {
    if (deadlineAtMs <= 0L) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val semantic = NotiTheme.semantic

    val urgency = dueUrgencyOf(deadlineAtMs)
    val container: Color
    val onContainer: Color
    val iconRes: Int
    when (urgency) {
        DueUrgency.OVERDUE -> {
            container = semantic.overdueContainer
            onContainer = semantic.onOverdueContainer
            iconRes = R.drawable.assignment_late
        }
        DueUrgency.SOON -> {
            container = semantic.dueSoonContainer
            onContainer = semantic.onDueSoonContainer
            iconRes = R.drawable.schedule
        }
        DueUrgency.LATER -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
            iconRes = R.drawable.schedule
        }
    }

    // Relative-only on cards (e.g. "明天 23:59" / "2 天前"); the detail screen shows the absolute date/time.
    val label = getRelativeTimeStr(deadlineAtMs, context)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
        )
    }
}

/**
 * A small neutral pill showing the user-set "do date" (when they plan to work on the task),
 * as opposed to [DueChip]'s deadline. No urgency coloring: the do date is a plan, not an obligation.
 *
 * Values ≤ 0 (unset) render nothing.
 */
@Composable
fun DoDateChip(
    doAtMs: Long,
    modifier: Modifier = Modifier,
) {
    if (doAtMs <= 0L) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = MaterialTheme.colorScheme.secondaryContainer
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer

    // The "someday" sentinel is not a real date; label it as such instead of formatting a far-future time.
    val label = if (org.muilab.notigpt.model.features.SavedItem.isSomeday(doAtMs)) {
        androidx.compose.ui.res.stringResource(R.string.do_date_someday)
    } else {
        androidx.compose.ui.res.stringResource(
            R.string.ui_reminders_do_date_chip,
            getRelativeTimeStr(doAtMs, context),
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_calendar_today),
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
        )
    }
}
