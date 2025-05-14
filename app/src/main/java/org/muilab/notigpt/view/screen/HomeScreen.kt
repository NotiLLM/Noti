package org.muilab.notigpt.view.screen

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.view.component.DevControlPanel
import org.muilab.notigpt.view.component.NotiDrawer
import org.muilab.notigpt.view.component.SearchBar
import org.muilab.notigpt.view.component.TabLayout
import org.muilab.notigpt.view.component.UserControlPanel
import org.muilab.notigpt.view.component.notification.AutoControlBar
import org.muilab.notigpt.viewModel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(context: Context, drawerViewModel: DrawerViewModel) {
    val pageCount = drawerViewModel.notiCategoryCount.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount.value })
    var toSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                TabLayout(context, drawerViewModel, pagerState)
            }
            IconButton(
                onClick = {
                    toSearch = !toSearch
                },
                modifier = Modifier
                    .background(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .size(32.dp)
                    .align(alignment = androidx.compose.ui.Alignment.CenterVertically)
            ) {
                // If toSearch is true, show cancel icon. Else show search icon.
                if (toSearch) {
                    // Show cancel icon
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    // Show search icon
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        if (toSearch)
            SearchBar(drawerViewModel)

        Column {
            AutoControlBar()
            Box (Modifier.weight(1F), contentAlignment = Alignment.TopCenter) {
                NotiDrawer(context, drawerViewModel)
            }
            UserControlPanel(drawerViewModel)
            DevControlPanel(context, drawerViewModel)
        }
    }
}