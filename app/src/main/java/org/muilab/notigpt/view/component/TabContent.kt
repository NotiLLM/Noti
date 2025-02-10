package org.muilab.notigpt.view.component

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.vector.ImageVector
import org.muilab.notigpt.view.screen.HomeScreen
import org.muilab.notigpt.viewModel.DrawerViewModel
import org.muilab.notigpt.viewModel.ServerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabContent(
    pagerState: PagerState,
    context: Context,
    drawerViewModel: DrawerViewModel,
    serverViewModel: ServerViewModel
) {

    HorizontalPager(state = pagerState) { index ->
        when (index) {
            0 -> drawerViewModel.updateCategory("all")
            1 -> drawerViewModel.updateCategory("pinned")
            2 -> drawerViewModel.updateCategory("social")
            3 -> drawerViewModel.updateCategory("email")
        }
        HomeScreen(context, drawerViewModel, serverViewModel)
    }

}

fun getTabList(): List<Pair<String, ImageVector>> {
    return listOf(
        "Notifications" to Icons.Default.Notifications,
        "Pinned" to Icons.Default.Star,
        "Messages" to Icons.Filled.Person,
        "Emails" to Icons.Default.Email,
    )
}