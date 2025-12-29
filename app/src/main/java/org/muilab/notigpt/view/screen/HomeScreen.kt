package org.muilab.notigpt.view.screen

import AppCategoryFilterChips
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.muilab.notigpt.view.component.DevControlPanel
import org.muilab.notigpt.view.component.NotiDrawer
import org.muilab.notigpt.view.component.features.TaskList
import org.muilab.notigpt.viewModel.DrawerViewModel
import org.muilab.notigpt.viewModel.TaskViewModel

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(context: Context, drawerViewModel: DrawerViewModel, taskViewModel: TaskViewModel) {
    Column (modifier = Modifier.fillMaxSize()) {
        TaskList(taskViewModel)
        AppCategoryFilterChips(drawerViewModel)
        Box(modifier = Modifier.fillMaxSize()) {
            NotiDrawer(context, drawerViewModel)

            // Global spinner overlay triggered by DrawerViewModel when user switches targets
            val isLoading by drawerViewModel.isTargetLoading.collectAsState()
            // Log the loading state changes for debugging
            LaunchedEffect(isLoading) {
                Log.d("AppScaffold", "isTargetLoading changed: $isLoading")
            }
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}