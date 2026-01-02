package org.muilab.notigpt.ui.component

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import android.util.Log
import org.muilab.notigpt.ui.component.appbar.AppBottomBar
import org.muilab.notigpt.ui.component.appbar.AppTopBar
import org.muilab.notigpt.ui.screen.HomeScreen
import org.muilab.notigpt.ui.screen.SettingsScreen
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.viewmodel.TaskViewModel
import androidx.compose.ui.Modifier

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppScaffold(
    context: Context,
    drawerViewModel: DrawerViewModel,
    taskViewModel: TaskViewModel
) {

    // Log the ViewModel identity to help debug instance mismatches
    Log.d("AppScaffold", "composed with DrawerViewModel hash=${drawerViewModel.hashCode()}")

    // State to manage whether the search bar is expanded
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isSettingsShown by remember { mutableStateOf(false) }
    // State to manage the selected item in the bottom navigation bar
    val selectedCategory = drawerViewModel.category.collectAsState()
    val unreadCounts by drawerViewModel.unreadCountsByCategory.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                drawerViewModel = drawerViewModel,
                isSearchExpanded = isSearchExpanded,
                // Lambda to toggle the search state
                onSearchToggled = { isSearchExpanded = it },
                isSettingsShown = isSettingsShown,
                onSettingsShown = { isSettingsShown = it }
            )
        },
        bottomBar = {
            if (!isSettingsShown) {
                AppBottomBar(
                    selectedCategory = selectedCategory.value,
                    onItemSelected = { category ->
                        drawerViewModel.updateCategory(category)
                        drawerViewModel.updateQueryString("")
                        isSearchExpanded = false
                        // Reset the search state when a new category is selected
                    },
                    unreadCounts = unreadCounts,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceDim
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
        ) {
            if (!isSettingsShown) {
                HomeScreen(context, drawerViewModel, taskViewModel)
            } else {
                SettingsScreen()
            }
        }
    }
}