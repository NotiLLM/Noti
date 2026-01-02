package org.muilab.notigpt.ui.screen

import AppCategoryFilterChips
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.ui.component.NotiDrawer
import org.muilab.notigpt.ui.component.features.TaskList
import org.muilab.notigpt.ui.component.notification.search.SearchNotiCard
import org.muilab.notigpt.ui.utils.LifecycleObserver
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.viewmodel.TaskViewModel

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(context: Context, drawerViewModel: DrawerViewModel, taskViewModel: TaskViewModel) {
    val queryString by drawerViewModel.queryString.collectAsState()

    // Check if we are in search mode
    val isSearching = queryString.isNotBlank()

    Column (modifier = Modifier.fillMaxSize()) {

        // Hide TaskList and FilterChips during search to focus on results
        if (!isSearching) {
            TaskList(taskViewModel)
            AppCategoryFilterChips(drawerViewModel)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isSearching) {
                // --- Search Results View ---
                val searchResults by drawerViewModel.searchResults.collectAsState()
                val searchUnits by drawerViewModel.searchUnits.collectAsState()

                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
                    ) {
                        items(searchResults.keys.toList()) { key ->
                            val records = searchResults[key] ?: emptyList()
                            val unit = searchUnits[key]

                            if (unit != null && records.isNotEmpty()) {
                                SearchNotiCard(
                                    notiUnit = unit,
                                    records = records,
                                    drawerViewModel = drawerViewModel
                                )
                            }
                        }
                    }
                }
            } else {
                // --- Standard Drawer View ---
                NotiDrawer(context, drawerViewModel)
            }

            // Global spinner overlay
            val isLoading by drawerViewModel.isTargetLoading.collectAsState()
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    LifecycleObserver(
        onResume = {
            // Optional: refresh data if needed
        },
        onPause = {
            drawerViewModel.persistReadStatus()
        }
    )
}