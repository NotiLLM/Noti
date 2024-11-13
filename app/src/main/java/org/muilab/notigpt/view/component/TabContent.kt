package org.muilab.notigpt.view.component

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.muilab.notigpt.view.screen.HomeScreen
import org.muilab.notigpt.viewModel.DrawerViewModel
import org.muilab.notigpt.viewModel.ServerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabContent(
    tabData: List<Pair<String, ImageVector>>,
    pagerState: PagerState,
    context: Context,
    drawerViewModel: DrawerViewModel,
    serverViewModel: ServerViewModel
) {
    HorizontalPager(state = pagerState) { index ->
        when (index) {
            0 -> HomeScreen(context, drawerViewModel, serverViewModel, category="all")
            1 -> HomeScreen(context, drawerViewModel, serverViewModel, category="pinned")
            2 -> HomeScreen(context, drawerViewModel, serverViewModel, category="social")
            3 -> HomeScreen(context, drawerViewModel, serverViewModel, category="email")
        }
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