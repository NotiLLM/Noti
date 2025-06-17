package org.muilab.notigpt.view.component

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import org.muilab.notigpt.view.screen.HomeScreen
import org.muilab.notigpt.viewModel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppScaffold(context: Context, drawerViewModel: DrawerViewModel) {
    // Will be used for navigation in the future
//    val navController = rememberNavController()
//
//    Scaffold(
//        bottomBar = {
//            AppBottomNavigation(navController)
//        }
//    ) { innerPadding ->
//        Box(modifier = Modifier.padding(innerPadding)) {
//            NavigationGraph(navController, drawerViewModel, context)
//        }
//    }
    HomeScreen(context, drawerViewModel)
}