package org.muilab.notigpt.view.screen

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.muilab.notigpt.view.component.DevControlPanel
import org.muilab.notigpt.view.component.NotiDrawer
import org.muilab.notigpt.view.component.SearchBar
import org.muilab.notigpt.view.component.UserControlPanel
import org.muilab.notigpt.viewModel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun HomeScreen(context: Context, drawerViewModel: DrawerViewModel) {
    Column {
        SearchBar(drawerViewModel)
        Box (Modifier.weight(1F), contentAlignment = Alignment.TopCenter) {
            NotiDrawer(context, drawerViewModel)
//            Box(
//                modifier = Modifier.fillMaxSize(),
//                contentAlignment = Alignment.BottomCenter
//            ) {
//                TestCard(serverViewModel)
//            }
        }
        UserControlPanel(drawerViewModel)
        DevControlPanel(context, drawerViewModel)
    }
}