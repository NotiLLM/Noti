package org.muilab.notigpt.ui.component.notification.card.searchcard

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.SearchGapKey
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.SearchNotiCardHeader
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.SearchNotiCardLoadButton
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.SearchNotiCardRecordList
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.SearchNotiCardScaffold
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.checkGapCached
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.computeOverallTitle
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.rememberClipboardController
import org.muilab.notigpt.ui.component.notification.card.searchcard.elements.rememberGapCache
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun SearchNotiCard(
    notiUnit: NotiUnit,
    records: List<NotiRecord>,
    drawerViewModel: DrawerViewModel
) {
    val bitmap = notiUnit.bitmap
    val appName = notiUnit.appName
    val isPeople = notiUnit.isPeople

    val clipboard = rememberClipboardController()

    val includeHistory by drawerViewModel.includeHistory.collectAsState()

    val gapCache = rememberGapCache(notiUnit.notiKey, includeHistory)

    suspend fun checkGap(start: Long, end: Long): Boolean {
        return checkGapCached(
            cache = gapCache,
            key = SearchGapKey(notiUnit.notiKey, start, end, includeHistory),
        ) {
            drawerViewModel.checkGapHasRecords(notiUnit.notiKey, start, end)
        }
    }

    val notiOverallTitle = remember(records, isPeople) {
        computeOverallTitle(records, isPeople)
    }

    SearchNotiCardScaffold {
        SearchNotiCardHeader(
            bitmap = bitmap,
            appName = appName,
            overallTitle = notiOverallTitle,
            category = notiUnit.category,
            isVisible = notiUnit.isVisible,
            onAccess = { drawerViewModel.accessNotification(notiUnit) }
        )

        HorizontalDivider()

        var hasOlderContextAtTop by remember(records, includeHistory) { mutableStateOf(false) }
        LaunchedEffect(records, includeHistory) {
            if (records.isNotEmpty()) {
                val firstRecordTime = records.first().time
                hasOlderContextAtTop = checkGap(0L, firstRecordTime)
            } else {
                hasOlderContextAtTop = false
            }
        }

        if (hasOlderContextAtTop) {
            SearchNotiCardLoadButton(
                label = "Load Older...",
                onClick = { drawerViewModel.loadSearchContext(notiUnit.notiKey, isOlder = true) }
            )
        }

        SearchNotiCardRecordList(
            notiKey = notiUnit.notiKey,
            records = records,
            notiOverallTitle = notiOverallTitle,
            isPeople = isPeople,
            includeHistory = includeHistory,
            clipboard = clipboard,
            checkGap = { start, end -> checkGap(start, end) },
            onLoadGap = { start, end, fromStart ->
                drawerViewModel.loadGapRecords(
                    notiUnit.notiKey,
                    start,
                    end,
                    fromStart = fromStart
                )
                // Invalidate cache entry for this gap after we've loaded records.
                gapCache.remove(
                    SearchGapKey(
                        notiUnit.notiKey,
                        start,
                        end,
                        includeHistory
                    ).toString()
                )
            }
        )

        var hasNewerContextAtBottom by remember(records, includeHistory) { mutableStateOf(false) }
        LaunchedEffect(records, includeHistory) {
            if (records.isNotEmpty()) {
                val lastRecordTime = records.last().time
                hasNewerContextAtBottom = checkGap(lastRecordTime, Long.MAX_VALUE)
            } else {
                hasNewerContextAtBottom = false
            }
        }

        if (hasNewerContextAtBottom) {
            SearchNotiCardLoadButton(
                label = "Load Newer...",
                onClick = { drawerViewModel.loadSearchContext(notiUnit.notiKey, isOlder = false) }
            )
        }
    }
}