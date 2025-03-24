package org.muilab.notigpt.view.screen

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import org.muilab.notigpt.view.component.TabContent
import org.muilab.notigpt.view.component.TabLayout
import org.muilab.notigpt.viewModel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(context: Context, drawerViewModel: DrawerViewModel) {
    val pageCount = drawerViewModel.notiCategoryCount.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount.value })
    Column(modifier = Modifier.fillMaxSize()) {
        TabLayout(context, drawerViewModel, pagerState)
        TabContent(pagerState, context, drawerViewModel)
    }
}