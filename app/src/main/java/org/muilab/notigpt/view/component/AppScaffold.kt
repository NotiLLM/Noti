package org.muilab.notigpt.view.component

import AppCategoryFilterChips
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.view.component.appbar.AppBottomBar
import org.muilab.notigpt.view.component.appbar.AppTopBar
import org.muilab.notigpt.view.component.features.TaskList
import org.muilab.notigpt.view.screen.HomeScreen
import org.muilab.notigpt.view.screen.SettingsScreen
import org.muilab.notigpt.viewModel.DrawerViewModel
import org.muilab.notigpt.viewModel.TaskViewModel

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
                        // Reset the search state when a new category is selected
                        isSearchExpanded = false
                    },
                    unreadCounts = unreadCounts,
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (!isSettingsShown) {
                HomeScreen(context, drawerViewModel, taskViewModel)
            } else {
                SettingsScreen()
            }
        }
    }
}