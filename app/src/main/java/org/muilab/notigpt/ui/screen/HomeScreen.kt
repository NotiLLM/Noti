package org.muilab.notigpt.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.component.notification.card.notirecord.NotiRecordContextCard
import org.muilab.notigpt.ui.screens.NotificationsScreen
import org.muilab.notigpt.ui.utils.LifecycleObserver
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

/**
 * Home route that hosts the notification drawer experience.
 *
 * Keep route-level composition here and pass drawer behavior to NotificationsScreen/DrawerViewModel. Avoid adding
 * notification business logic directly to this wrapper.
 */
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(drawerViewModel: DrawerViewModel) {
    val queryString by drawerViewModel.queryString.collectAsState()

    val isSearching = queryString.isNotBlank()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSearching) {
            val searchResults by drawerViewModel.searchResults.collectAsState()
            val searchUnits by drawerViewModel.searchUnits.collectAsState()

            if (searchResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ui_home_no_results))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
                ) {
                    items(searchResults.keys.toList()) { key ->
                        val records = searchResults[key] ?: emptyList()
                        val unit = searchUnits[key]
                        if (records.isNotEmpty()) {
                            NotiRecordContextCard(
                                notiKey = key,
                                records = records,
                                drawerViewModel = drawerViewModel,
                                cachedUnit = unit,
                            )
                        }
                    }
                }
            }
        } else {
            NotificationsScreen(drawerViewModel = drawerViewModel)
        }

        val isLoading by drawerViewModel.isTargetLoading.collectAsState()
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    LifecycleObserver(
        onResume = { },
        onPause = {
            drawerViewModel.persistReadStatus()
        }
    )
}