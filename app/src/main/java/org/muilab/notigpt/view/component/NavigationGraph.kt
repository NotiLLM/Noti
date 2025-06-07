package org.muilab.notigpt.view.component

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.muilab.notigpt.view.screen.HomeScreen
import org.muilab.notigpt.view.screen.SettingsScreen
import org.muilab.notigpt.viewModel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NavigationGraph(navController: NavHostController, drawerViewModel: DrawerViewModel, context: Context) {
    NavHost(navController, startDestination = BottomNavItem.Home.screen_route) {
        composable(BottomNavItem.Home.screen_route) {
            HomeScreen(context, drawerViewModel)
        }
        composable(BottomNavItem.Questions.screen_route) {
            SettingsScreen()
        }
    }
}