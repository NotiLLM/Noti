package org.muilab.notigpt.view.screen

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.muilab.notigpt.view.component.TabContent
import org.muilab.notigpt.view.component.TabLayout
import org.muilab.notigpt.view.component.getTabList
import org.muilab.notigpt.viewModel.DrawerViewModel
import org.muilab.notigpt.viewModel.ServerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(context: Context, drawerViewModel: DrawerViewModel, geminiViewModel: ServerViewModel) {
    val tabData = getTabList()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabData.size })
    Column(modifier = Modifier.fillMaxSize()) {
        TabLayout(tabData, pagerState)
        TabContent(tabData, pagerState, context, drawerViewModel, geminiViewModel)
    }
}